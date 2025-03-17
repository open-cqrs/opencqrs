/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import static java.util.stream.Collectors.toCollection;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.CqrsFrameworkException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Implementation class delegating to a list of configurable {@link EventUpcaster}s combining the
 * {@link EventUpcaster.Result}s appropriately.
 *
 * @see #upcast(Event)
 */
public class EventUpcasters {

    private final List<EventUpcaster> upcasters;

    public EventUpcasters(List<EventUpcaster> upcasters) {
        this.upcasters = upcasters;
    }

    public EventUpcasters(EventUpcaster... upcasters) {
        this(Arrays.stream(upcasters).toList());
    }

    private boolean upcastSingleEvent(Event event, List<Event> result) {
        long canUpcastCount =
                upcasters.stream().filter(upcaster -> upcaster.canUpcast(event)).count();
        if (canUpcastCount > 1) {
            throw new CqrsFrameworkException.NonTransientException("ambiguous upcasters found for: " + event);
        } else if (canUpcastCount == 0) {
            result.add(event);
            return false;
        }

        upcasters.stream()
                .filter(upcaster -> upcaster.canUpcast(event))
                .flatMap(upcaster -> upcaster.upcast(event))
                .map(upcastingResult -> new Event(
                        event.source(),
                        event.subject(),
                        upcastingResult.type(),
                        upcastingResult.data(),
                        event.specVersion(),
                        event.id(),
                        event.time(),
                        event.dataContentType(),
                        event.hash(),
                        event.predecessorHash()))
                .collect(toCollection(() -> result));
        return true;
    }

    /**
     * If necessary upcasts the given {@link Event} using all configured {@link EventUpcaster}s repeatedly, as long as
     * one of them is {@linkplain EventUpcaster#canUpcast(Event) capable} of upcasting the event. The given event,
     * hence, will be upcasted using a chain of upcasters, as long as necessary. <b>Due to non-deferred stream
     * population be aware, that this method will block infinitely or cause {@link OutOfMemoryError} if <i>cyclically
     * dependent</i> {@link EventUpcaster}s have been configured, for instance two {@link TypeChangingEventUpcaster}s
     * switching types conversely.</b>
     *
     * @param event the event to be upcasted, if relevant
     * @return a stream of {@link Event}s with {@link Event#type()} and {@link Event#data()} upcasted as needed, all
     *     other attributes remain the same
     */
    public Stream<Event> upcast(Event event) {
        var result = new AtomicReference<>(new ArrayList<>(List.of(event)));
        AtomicBoolean proceed = new AtomicBoolean(true);

        while (proceed.get()) {
            result.updateAndGet(r -> {
                var newResult = new ArrayList<Event>();
                proceed.set(r.stream()
                        .map(e -> upcastSingleEvent(e, newResult))
                        .reduce((a, b) -> a || b)
                        .orElse(false));
                return newResult;
            });
        }
        return result.get().stream();
    }
}
