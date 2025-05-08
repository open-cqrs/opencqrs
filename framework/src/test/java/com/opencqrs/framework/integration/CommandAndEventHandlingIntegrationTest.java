/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.opencqrs.esdb.client.Client;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;
import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.*;
import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.StateRebuilding;
import com.opencqrs.framework.eventhandler.EventHandling;
import com.opencqrs.framework.eventhandler.EventHandlingProcessorLifecycleController;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.AbstractEventDataMarshallingEventUpcaster;
import com.opencqrs.framework.upcaster.EventUpcaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public class CommandAndEventHandlingIntegrationTest {

    @TestConfiguration
    static class SuccessfulCommandAndEventHandling {

        @CommandHandling
        public void handle(Book book, AddBookCommand command, CommandEventPublisher<Book> eventPublisher) {
            assertThat(book).as("must not yet exist").isNull();
            eventPublisher.publish(new BookAddedEvent(command.isbn()));
        }

        @StateRebuilding
        public Book bookAdded(BookAddedEvent event) {
            return new Book(event.isbn(), false);
        }

        @CommandHandling
        public void handle(Book book, BorrowBookCommand command, CommandEventPublisher<Book> eventPublisher) {
            assertThat(book.isbn()).isEqualTo(command.isbn());
            if (book.lent()) {
                throw new IllegalStateException("book already lent: " + book.isbn());
            }
            eventPublisher.publish(new BookBorrowedEvent());
        }

        @StateRebuilding
        public Book bookBorrowed(Book book, BookBorrowedEvent event) {
            return new Book(book.isbn(), true);
        }

        int booksAdded = 0;
        int booksLent = 0;

        @EventHandling("group-1")
        public void countBooksAdded(BookAddedEvent event) {
            ++booksAdded;
        }

        @EventHandling("group-1")
        public void countBooksLent(BookBorrowedEvent event) {
            ++booksLent;
        }
    }

    @TestConfiguration
    static class EventHandlingRetries {

        public record Execution(String id, Boolean failing) {}

        List<Execution> executions = new ArrayList<>();

        @EventHandling("group-2")
        public void bookAddedFailing(BookAddedEvent event) {
            if (event.isbn().startsWith("fail")) {
                if (executions.stream().noneMatch(it -> it.id.equals(event.isbn()))) {
                    executions.add(new Execution(event.isbn(), true));
                    throw new IllegalStateException("failing");
                } else {
                    executions.add(new Execution(event.isbn(), false));
                }
            } else if (event.isbn().startsWith("succeed")) {
                executions.add(new Execution(event.isbn(), false));
            }
        }
    }

    @TestConfiguration
    static class EventUpcasting {

        @Bean
        public EventUpcaster testEventUpcaster(
                EventTypeResolver eventTypeResolver, EventDataMarshaller eventDataMarshaller) {
            return new AbstractEventDataMarshallingEventUpcaster(eventDataMarshaller) {

                @Override
                public boolean canUpcast(Event event) {
                    return "book.page.damaged-v1".equals(event.type());
                }

                @Override
                protected Stream<MetaDataAndPayloadResult> doUpcast(
                        Event event, Map<String, ?> metaData, Map<String, ?> payload) {
                    return Stream.of(new MetaDataAndPayloadResult(
                            eventTypeResolver.getEventType(BookPageDamagedEvent.class),
                            Map.of("upcasted", !((Boolean) metaData.get("upcasted"))),
                            Map.of("page", payload.get("number"))));
                }
            };
        }

        AtomicReference<BookPageDamagedEvent> upcastedEvent = new AtomicReference<>();
        AtomicReference<Map<String, ?>> upcastedMetaData = new AtomicReference<>();

        @EventHandling("group-3")
        public void handleUpcastedEvent(Map<String, ?> metaData, BookPageDamagedEvent event) {
            upcastedEvent.compareAndSet(null, event);
            upcastedMetaData.compareAndSet(null, metaData);
        }
    }

    @TestConfiguration
    static class TransactionalEventHandling {

        @Autowired
        private PlatformTransactionManager platformTransactionManager;

        int activeTransactionCount = 0;

        public int getActiveTransactionCount() {
            return activeTransactionCount;
        }

        @EventHandling("group-3")
        @Transactional
        public void expectActiveTransaction(BookAddedEvent event) {
            new TransactionTemplate(
                            platformTransactionManager,
                            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_MANDATORY))
                    .executeWithoutResult(transactionStatus -> ++activeTransactionCount);
        }
    }

    @TestConfiguration
    public static class InterruptableEventHandlerConfiguration {

        final CyclicBarrier barrier = new CyclicBarrier(2);
        private final AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        @EventHandling("group-4")
        public void handle(BookAddedEvent event) throws BrokenBarrierException, InterruptedException, TimeoutException {
            barrier.await();

            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException e) {
                exceptionRef.compareAndSet(null, e);
                throw e;
            }
        }
    }

    @Autowired
    private CommandRouter commandRouter;

    @Autowired
    private Client client;

    @Autowired
    private ApplicationContext applicationContext;

    private ConfigurableApplicationContext getEventHandlerContext() {
        return applicationContext.getBean("eventHandlingProcessorContext", ConfigurableApplicationContext.class);
    }

    @Test
    public void commandsAndEventsSuccessfullyHandled(@Autowired SuccessfulCommandAndEventHandling configuration) {
        var id = UUID.randomUUID().toString();

        commandRouter.send(new AddBookCommand(id));
        await().untilAsserted(() -> assertThat(configuration.booksAdded).isGreaterThanOrEqualTo(1));

        commandRouter.send(new BorrowBookCommand(id));
        await().untilAsserted(() -> assertThat(configuration.booksLent).isGreaterThanOrEqualTo(1));
    }

    @Test
    public void failingEventHandlerRetried(@Autowired EventHandlingRetries configuration) {
        commandRouter.send(new AddBookCommand("succeed-1"));
        commandRouter.send(new AddBookCommand("fail-1"));
        commandRouter.send(new AddBookCommand("succeed-2"));
        commandRouter.send(new AddBookCommand("fail-2"));
        commandRouter.send(new AddBookCommand("succeed-3"));

        await().untilAsserted(() -> {
            assertThat(configuration.executions)
                    .containsExactly(
                            new EventHandlingRetries.Execution("succeed-1", false),
                            new EventHandlingRetries.Execution("fail-1", true),
                            new EventHandlingRetries.Execution("fail-1", false),
                            new EventHandlingRetries.Execution("succeed-2", false),
                            new EventHandlingRetries.Execution("fail-2", true),
                            new EventHandlingRetries.Execution("fail-2", false),
                            new EventHandlingRetries.Execution("succeed-3", false));
        });
    }

    @Test
    public void eventsUpcasted(
            @Autowired EventUpcasting configuration, @Autowired EventDataMarshaller eventDataMarshaller) {
        client.write(
                List.of(new EventCandidate(
                        "tag://test",
                        "/damaged/42",
                        "book.page.damaged-v1",
                        eventDataMarshaller.serialize(
                                new EventData<>(Map.of("upcasted", false), Map.of("number", 42))))),
                List.of(new Precondition.SubjectIsPristine("/damaged/42")));

        await().untilAsserted(() -> {
            assertThat(configuration.upcastedEvent.get()).isEqualTo(new BookPageDamagedEvent(42));
            assertThat(configuration.upcastedMetaData.get())
                    .hasEntrySatisfying("upcasted", o -> assertThat(o).isEqualTo(true));
        });
    }

    @Test
    public void eventsHandledTransactionally(@Autowired TransactionalEventHandling configuration) {
        commandRouter.send(new AddBookCommand(UUID.randomUUID().toString()));
        await().untilAsserted(() ->
                assertThat(configuration.getActiveTransactionCount()).isGreaterThanOrEqualTo(1));
    }

    @Test
    @DirtiesContext
    public void eventHandlingProcessorsStoppedOnContextShutdown(
            @Autowired InterruptableEventHandlerConfiguration config)
            throws BrokenBarrierException, InterruptedException {
        commandRouter.send(new AddBookCommand(UUID.randomUUID().toString()));

        config.barrier.await();

        var processorLifecycleControllers =
                getEventHandlerContext().getBeansOfType(EventHandlingProcessorLifecycleController.class);
        getEventHandlerContext().close();

        await().untilAsserted(() -> {
            assertThat(config.exceptionRef)
                    .hasValueSatisfying(e -> assertThat(e).isInstanceOf(InterruptedException.class));
            assertThat(processorLifecycleControllers.values())
                    .allSatisfy(it -> assertThat(it.isRunning()).isFalse());
        });
    }

    @Container
    static GenericContainer<?> esdb = new GenericContainer<>(
                    "docker.io/thenativeweb/eventsourcingdb:" + System.getProperty("esdb.version"))
            .withExposedPorts(3000)
            .withCreateContainerCmdModifier(cmd -> cmd.withCmd(
                    "run",
                    "--api-token",
                    "secret",
                    "--data-directory-temporary",
                    "--http-enabled=true",
                    "--https-enabled=false"));

    @DynamicPropertySource
    static void esdbProperties(DynamicPropertyRegistry registry) {
        registry.add("esdb.server.uri", () -> "http://" + esdb.getHost() + ":" + esdb.getFirstMappedPort());
        registry.add("esdb.server.api-token", () -> "secret");
    }
}
