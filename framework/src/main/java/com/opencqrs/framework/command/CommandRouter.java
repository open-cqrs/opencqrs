/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static java.util.stream.Collectors.*;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.command.cache.NoStateRebuildingCache;
import com.opencqrs.framework.command.cache.StateRebuildingCache;
import com.opencqrs.framework.command.interceptor.*;
import com.opencqrs.framework.interceptor.InterceptorExecutionException;
import com.opencqrs.framework.persistence.CapturedEvent;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.persistence.ImmediateEventPublisher;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * {@link Command} router implementation providing CQRS-style command execution.
 *
 * @see #send(Command, Map)
 */
public final class CommandRouter {

    private final EventReader eventReader;
    private final ImmediateEventPublisher immediateEventPublisher;
    private final Map<Class<Command>, CommandHandlerDefinition> commandHandlerDefinitions;
    private final Map<Class<?>, List<StateRebuildingHandlerDefinition<Object, Object>>>
            stateRebuildingHandlerDefinitions;
    private final List<CommandInterceptor> interceptors;
    private final StateRebuildingCache stateRebuildingCache;

    /**
     * Creates a pre-configured instance of {@code this}.
     *
     * @param eventReader the event source
     * @param immediateEventPublisher the event sink
     * @param commandHandlerDefinitions a non-empty list of command handler definitions to be executable
     * @param stateRebuildingHandlerDefinitions a non-empty list of state rebuilding handler definitions used for
     *     event-sourcing
     * @param interceptors an ordered list of {@link CommandInterceptor}s (index {@code 0} = outermost), empty if none
     * @param stateRebuildingCache the cache to use for state rebuilding
     */
    public CommandRouter(
            EventReader eventReader,
            ImmediateEventPublisher immediateEventPublisher,
            List<CommandHandlerDefinition> commandHandlerDefinitions,
            List<StateRebuildingHandlerDefinition> stateRebuildingHandlerDefinitions,
            List<CommandInterceptor> interceptors,
            StateRebuildingCache stateRebuildingCache) {
        this.eventReader = eventReader;
        this.immediateEventPublisher = immediateEventPublisher;
        this.stateRebuildingCache = stateRebuildingCache;
        this.interceptors = interceptors;

        Set<Class<Command>> ambiguousCommands =
                findDuplicates(commandHandlerDefinitions.stream().map(CommandHandlerDefinition::commandClass));
        if (!ambiguousCommands.isEmpty()) {
            throw new IllegalStateException("duplicate command handler definitions found for: " + ambiguousCommands);
        }
        this.commandHandlerDefinitions = commandHandlerDefinitions.stream()
                .collect(toMap(CommandHandlerDefinition::commandClass, Function.identity()));
        this.stateRebuildingHandlerDefinitions = new HashMap<>();
        stateRebuildingHandlerDefinitions.forEach(srhd -> this.stateRebuildingHandlerDefinitions
                .computeIfAbsent(srhd.instanceClass(), clazz -> new ArrayList<>())
                .add(srhd));
    }

    /**
     * Creates a pre-configured instance of {@code this} with no {@linkplain CommandInterceptor interceptors} and a
     * {@link NoStateRebuildingCache}.
     *
     * @param eventReader the event source
     * @param immediateEventPublisher the event sink
     * @param commandHandlerDefinitions a non-empty list of command handler definitions to be executable
     * @param stateRebuildingHandlerDefinitions a non-empty list of state rebuilding handler definitions used for
     *     event-sourcing
     */
    public CommandRouter(
            EventReader eventReader,
            ImmediateEventPublisher immediateEventPublisher,
            List<CommandHandlerDefinition> commandHandlerDefinitions,
            List<StateRebuildingHandlerDefinition> stateRebuildingHandlerDefinitions) {
        this(
                eventReader,
                immediateEventPublisher,
                commandHandlerDefinitions,
                stateRebuildingHandlerDefinitions,
                List.of(),
                new NoStateRebuildingCache());
    }

    /**
     * Sends the given command with empty meta-data to the {@linkplain CommandHandlerDefinition#commandClass()
     * appropriate} {@link CommandHandler} for execution.
     *
     * @param command the command to be executed
     * @return the result from the {@link CommandHandler}, may be {@code null}
     * @param <R> the result type
     * @see #send(Command, Map)
     */
    public <R> @Nullable R send(Command command) {
        return send(command, Map.of());
    }

    /**
     * Sends the given command and meta-data to the {@linkplain CommandHandlerDefinition#commandClass() appropriate}
     * {@link CommandHandler} for execution. The command execution process involves the following:
     *
     * <ol>
     *   <li>the {@linkplain CommandHandlerDefinition#commandClass() matching} {@link CommandHandler} is determined
     *   <li>the {@linkplain CommandHandlerDefinition#instanceClass() instance type} for event-sourcing is determined
     *   <li>all {@linkplain StateRebuildingHandlerDefinition#instanceClass() matching} {@link StateRebuildingHandler}s
     *       are determined
     *   <li>the {@link StateRebuildingCache} is
     *       {@linkplain StateRebuildingCache#fetchAndMerge(StateRebuildingCache.CacheKey, Function) fetched}
     *   <li>newer (than cached) events are {@linkplain EventReader#consumeAsObject(EventReader.ClientRequestor,
     *       java.util.function.BiConsumer)} read} from the underlying event store, upcasted and converted to Java
     *       objects
     *   <li>the {@link Command#getSubjectCondition()} is checked
     *   <li>the events are applied to all matching {@link StateRebuildingHandler}s to reconstruct the instance state
     *   <li>the {@linkplain StateRebuildingCache cache is updated} with the reconstructed instance state
     *   <li>the command is {@linkplain CommandHandler executed} on the instance
     *   <li>the events captured as part of the command execution are {@linkplain ImmediateEventPublisher#publish(List,
     *       List) published atomically} to the underlying event store
     *   <li>the {@link CommandHandler} result is returned to the caller
     * </ol>
     *
     * <p>The whole execution is wrapped by the applicable {@linkplain CommandInterceptor interceptors}, which may
     * observe, time, transform, short-circuit, or veto (by throwing) any of the above stages.
     *
     * <p><b>No events will be published in case of an exception thrown from any of the involved handlers.</b>
     *
     * @param command the command to be executed
     * @param metaData the meta-data to be passed to the command handler
     * @return the result from the {@link CommandHandler}, may be {@code null}
     * @param <R> the result type
     */
    public <R> @Nullable R send(Command command, Map<String, ?> metaData) {
        CommandHandlerDefinition<Object, Command, R> commandHandlerDefinition =
                commandHandlerDefinitions.get(command.getClass());
        if (commandHandlerDefinition == null) {
            throw new CqrsFrameworkException.NonTransientException("no command handler definition for command: "
                    + command.getClass().getName());
        }
        Optional<List<StateRebuildingHandlerDefinition<Object, Object>>> relevantSRHDs =
                Optional.ofNullable(stateRebuildingHandlerDefinitions.get(commandHandlerDefinition.instanceClass()));
        StateRebuildingCache.CacheKey<Object> cacheKey = new StateRebuildingCache.CacheKey<>(
                command.getSubject(),
                commandHandlerDefinition.instanceClass(),
                commandHandlerDefinition.sourcingMode());

        try {
            var applicableInterceptors = interceptors.stream()
                    .filter(interceptor -> interceptor.commandClass().isAssignableFrom(command.getClass()))
                    .toList();
            return new CommandInterceptorChain<R>(applicableInterceptors)
                    .execute(new CommandInvocation<>(command, metaData), chain -> {
                        Function<StateRebuildingCache.CacheValue<Object>, StateRebuildingCache.CacheValue<Object>>
                                mergeFunction = cached -> {
                                    Set<Option> options = new HashSet<>();
                                    if (cached.eventId() != null) {
                                        options.add(new Option.LowerBoundExclusive(cached.eventId()));
                                    }

                                    EventReader.ClientRequestor clientRequestor =
                                            switch (commandHandlerDefinition.sourcingMode()) {
                                                case NONE -> (client, eventConsumer) -> {};
                                                case LOCAL ->
                                                    (client, eventConsumer) ->
                                                            client.read(command.getSubject(), options, eventConsumer);
                                                case RECURSIVE ->
                                                    (client, eventConsumer) -> {
                                                        options.add(new Option.Recursive());
                                                        client.read(command.getSubject(), options, eventConsumer);
                                                    };
                                            };

                                    AtomicReference<@Nullable String> latestSourcedId =
                                            new AtomicReference<@Nullable String>(cached.eventId());
                                    Map<String, String> sourcedSubjectIds = new HashMap<>(cached.sourcedSubjectIds());
                                    List<SourcedEvent> sourcedEvents = new ArrayList<>();

                                    eventReader.consumeRaw(clientRequestor, (rawCallback, raw) -> {
                                        latestSourcedId.set(raw.id());
                                        sourcedSubjectIds.put(raw.subject(), raw.id());
                                        rawCallback.upcast((upcastedCallback, upcasted) ->
                                                upcastedCallback.convert((metadata, o) ->
                                                        sourcedEvents.add(new SourcedEvent(o, metadata, raw))));
                                    });

                                    if (!commandHandlerDefinition.sourcingMode().equals(SourcingMode.NONE)) {
                                        switch (command.getSubjectCondition()) {
                                            case NONE -> {}
                                            case EXISTS -> {
                                                if (!sourcedSubjectIds.containsKey(command.getSubject())) {
                                                    throw new CommandSubjectDoesNotExistException(
                                                            "subject condition violated, no event was sourced matching"
                                                                    + " the subject of the given command: "
                                                                    + command.getClass()
                                                                            .getName(),
                                                            command);
                                                }
                                            }
                                            case PRISTINE -> {
                                                if (sourcedSubjectIds.containsKey(command.getSubject())) {
                                                    throw new CommandSubjectAlreadyExistsException(
                                                            "subject condition violated, at least one event was sourced"
                                                                    + " matching the subject of given command: "
                                                                    + command.getClass()
                                                                            .getName(),
                                                            command);
                                                }
                                            }
                                        }
                                    }

                                    AtomicReference<@Nullable Object> instance =
                                            new AtomicReference<@Nullable Object>(cached.instance());
                                    try {
                                        for (SourcedEvent sourced : sourcedEvents) {
                                            if (relevantSRHDs.isPresent()) {
                                                Util.applyUsingHandlers(
                                                        relevantSRHDs.get(),
                                                        instance,
                                                        sourced.raw.subject(),
                                                        sourced.event,
                                                        sourced.metaData,
                                                        sourced.raw,
                                                        chain);
                                            }
                                        }
                                    } catch (RuntimeException e) {
                                        throw e;
                                    } catch (Exception e) {
                                        throw new InterceptorExecutionException(
                                                "command interceptor raised a checked exception while applying a sourced event",
                                                e);
                                    }

                                    return new StateRebuildingCache.CacheValue<>(
                                            latestSourcedId.get(), instance.get(), sourcedSubjectIds);
                                };

                        AtomicReference<StateRebuildingCache.@Nullable CacheValue<Object>> sourced =
                                new AtomicReference<>();
                        chain.sourcing(
                                new Sourcing(
                                        commandHandlerDefinition.instanceClass(),
                                        commandHandlerDefinition.sourcingMode()),
                                () -> sourced.set(stateRebuildingCache.fetchAndMerge(cacheKey, mergeFunction)));
                        StateRebuildingCache.CacheValue<Object> fromCacheMerged = Objects.requireNonNull(sourced.get());

                        CommandEventCapturer<Object> eventCapturer = new CommandEventCapturer<>(
                                fromCacheMerged.instance(),
                                command.getSubject(),
                                relevantSRHDs.orElseGet(List::of),
                                chain);

                        R result = chain.handler(
                                new CommandHandlerInvocation(
                                        commandHandlerDefinition,
                                        fromCacheMerged.instance(),
                                        fromCacheMerged.eventId()),
                                () -> switch (commandHandlerDefinition.handler()) {
                                    case CommandHandler.ForCommand<Object, Command, R> handler ->
                                        handler.handle(command, eventCapturer);
                                    case CommandHandler.ForInstanceAndCommand<Object, Command, R> handler ->
                                        handler.handle(fromCacheMerged.instance(), command, eventCapturer);
                                    case CommandHandler.ForInstanceAndCommandAndMetaData<Object, Command, R> handler ->
                                        handler.handle(fromCacheMerged.instance(), command, metaData, eventCapturer);
                                });

                        if (!eventCapturer.getEvents().isEmpty()) {
                            List<CapturedEvent> events = List.copyOf(eventCapturer.getEvents());

                            List<Precondition> additionalPreconditions = events.stream()
                                    .map(CapturedEvent::subject)
                                    .filter(subject -> switch (commandHandlerDefinition.sourcingMode()) {
                                        case NONE -> false;
                                        case LOCAL -> subject.equals(command.getSubject());
                                        case RECURSIVE -> subject.startsWith(command.getSubject());
                                    })
                                    .filter(subject ->
                                            !fromCacheMerged.sourcedSubjectIds().containsKey(subject))
                                    .map(Precondition.SubjectIsPristine::new)
                                    .collect(toList());
                            switch (command.getSubjectCondition()) {
                                case PRISTINE ->
                                    additionalPreconditions.add(
                                            new Precondition.SubjectIsPristine(command.getSubject()));
                                case EXISTS ->
                                    additionalPreconditions.add(
                                            new Precondition.SubjectIsPopulated(command.getSubject()));
                            }
                            fromCacheMerged.sourcedSubjectIds().entrySet().stream()
                                    .map(e -> new Precondition.SubjectIsOnEventId(e.getKey(), e.getValue()))
                                    .collect(toCollection(() -> additionalPreconditions));

                            Publish publishRequest = new Publish(events, additionalPreconditions);
                            Publish appendRequest = chain.publish(publishRequest, () -> publishRequest);
                            immediateEventPublisher.publish(
                                    appendRequest.events(), appendRequest.additionalPreconditions());
                        }
                        return result;
                    });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new InterceptorExecutionException("command interceptor raised a checked exception", e);
        }
    }

    record SourcedEvent(Object event, Map<String, ?> metaData, Event raw) {}

    private <T> Set<T> findDuplicates(Stream<T> input) {
        return input.collect(collectingAndThen(groupingBy(Function.identity(), counting()), frequencyMap -> {
            frequencyMap.values().removeIf(count -> count == 1);
            return frequencyMap.keySet();
        }));
    }
}
