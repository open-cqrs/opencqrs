/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.opencqrs.esdb.client.*;
import com.opencqrs.framework.*;
import com.opencqrs.framework.command.*;
import com.opencqrs.framework.command.cache.StateRebuildingCache.CacheKey;
import com.opencqrs.framework.command.cache.StateRebuildingCache.CacheValue;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "opencqrs.command-handling.cache.type=in-memory",
            "opencqrs.command-handling.cache.capacity=5",
        })
@Testcontainers
public class CommandRouterLruInMemoryCachingTest {

    @TestConfiguration
    public static class MyConfig {

        // Book instance handlers

        @CommandHandling(sourcingMode = SourcingMode.RECURSIVE)
        public void handle(AddBookCommand command, CommandEventPublisher<Book> publisher) {
            publisher.publish(new BookAddedEvent(command.isbn()));
            publisher.publishRelative("pages/42", new BookPageDamagedEvent(42));
        }

        @StateRebuilding
        public Book on(BookAddedEvent event) {
            return new Book(event.isbn(), false);
        }

        @CommandHandling(sourcingMode = SourcingMode.RECURSIVE)
        public void handle(Book instance, BorrowBookCommand command, CommandEventPublisher<Book> publisher) {
            publisher.publish(new BookBorrowedEvent());
        }

        @StateRebuilding
        public Book on(Book instance, BookBorrowedEvent event) {
            return new Book(instance.isbn(), !instance.lent());
        }

        @CommandHandling(sourcingMode = SourcingMode.LOCAL)
        public void handle(Book instance, ReturnBookCommand command, CommandEventPublisher<Book> publisher) {
            publisher.publish(new BookReturnedEvent());
        }

        @StateRebuilding
        public Book on(Book instance, BookReturnedEvent event) {
            return new Book(instance.isbn(), !instance.lent());
        }

        // UUID instance handlers

        @StateRebuilding
        public UUID on(BookAddedEvent event, Event raw) {
            return UUID.fromString(event.isbn());
        }

        @CommandHandling(sourcingMode = SourcingMode.RECURSIVE)
        public void handle(UUID instance, ArchiveBookCommand command) {}
    }

    @Autowired
    private LruInMemoryStateRebuildingCache cache;

    @Autowired
    private CommandRouter commandRouter;

    @MockitoSpyBean
    private EsdbClient client;

    private final String isbn = UUID.randomUUID().toString();

    @AfterEach
    public void pruneCache() {
        cache.cache.clear();
    }

    @Test
    public void noCachingIfInstanceDoesNotYetExist() {
        assertChangedCacheKeys(() -> commandRouter.send(new AddBookCommand(isbn)), delta -> {
            assertThat(delta).isEmpty();
        });
    }

    @Test
    public void previousCommandInstanceInitiallySourcedAndCached() {
        commandRouter.send(new AddBookCommand(isbn));

        assertChangedCacheKeys(() -> commandRouter.send(new BorrowBookCommand(isbn)), delta -> {
            assertThat(delta)
                    .singleElement()
                    .isEqualTo(new CacheKey<>("/books/" + isbn, Book.class, SourcingMode.RECURSIVE))
                    .satisfies(cacheKey -> {
                        assertThat(cache.cache.get(cacheKey))
                                .isInstanceOfSatisfying(
                                        CacheValue.class, cacheValue -> assertThat(cacheValue.instance())
                                                .isEqualTo(new Book(isbn, false)));
                    });
        });
    }

    @Test
    public void previousCommandInstanceUpdatedWithinCache() {
        commandRouter.send(new AddBookCommand(isbn));

        assertChangedCacheKeys(
                () -> {
                    commandRouter.send(new BorrowBookCommand(isbn));
                    commandRouter.send(new BorrowBookCommand(isbn));
                },
                delta -> {
                    assertThat(delta)
                            .singleElement()
                            .isEqualTo(new CacheKey<>("/books/" + isbn, Book.class, SourcingMode.RECURSIVE))
                            .satisfies(cacheKey -> {
                                assertThat(cache.cache.get(cacheKey))
                                        .isInstanceOfSatisfying(
                                                CacheValue.class, cacheValue -> assertThat(cacheValue.instance())
                                                        .isEqualTo(new Book(isbn, true)));
                            });
                });
    }

    @Test
    public void sourcedWithExclusiveLowerBoundIdFromPreviouslyCachedInstance() {
        commandRouter.send(new AddBookCommand(isbn));

        assertChangedCacheKeys(() -> commandRouter.send(new BorrowBookCommand(isbn)), delta -> {
            var eventId = cache.cache.get(delta.stream().findFirst().get()).eventId();

            commandRouter.send(new BorrowBookCommand(isbn));

            ArgumentCaptor<Set<Option>> options = ArgumentCaptor.captor();
            verify(client, atLeastOnce()).read(eq("/books/" + isbn), options.capture(), any());

            assertThat(options.getValue()).contains(new Option.LowerBoundExclusive(eventId));
        });
    }

    @Test
    public void eventsPublishedIncludingPreconditionsFromCachedSourcedSubjectIds() {
        commandRouter.send(new AddBookCommand(isbn));

        assertChangedCacheKeys(() -> commandRouter.send(new BorrowBookCommand(isbn)), delta -> {
            Map<String, String> cachedSubjectsOnId =
                    cache.cache.get(delta.stream().findFirst().get()).sourcedSubjectIds();
            assertThat(cachedSubjectsOnId)
                    .as("2 events published by AddBookCommand")
                    .hasSize(2);

            ArgumentCaptor<List<Precondition>> preconditions = ArgumentCaptor.captor();
            verify(client, atLeastOnce()).write(anyList(), preconditions.capture());

            cachedSubjectsOnId.forEach((subject, onId) -> {
                assertThat(preconditions.getValue()).anySatisfy(precondition -> assertThat(precondition)
                        .isEqualTo(new Precondition.SubjectIsOnEventId(subject, onId)));
            });
        });
    }

    @Test
    public void cacheKeyIsSubjectSpecific() {
        assertChangedCacheKeys(
                () -> {
                    IntStream.rangeClosed(1, 3).forEach(value -> {
                        var otherIsbn = UUID.randomUUID().toString();
                        commandRouter.send(new AddBookCommand(otherIsbn));
                        commandRouter.send(new BorrowBookCommand(otherIsbn));
                    });
                },
                delta -> {
                    assertThat(delta).hasSize(3);
                });
    }

    @Test
    public void cacheKeyIsInstanceClassSpecific() {
        assertChangedCacheKeys(
                () -> {
                    commandRouter.send(new AddBookCommand(isbn));
                    commandRouter.send(new BorrowBookCommand(isbn));
                    commandRouter.send(new ArchiveBookCommand(isbn));
                },
                delta -> {
                    assertThat(delta).hasSize(2);
                    assertThat(delta).map(CacheKey::subject).allMatch(it -> it.equals("/books/" + isbn));
                    assertThat(delta).map(CacheKey::instanceClass).containsExactlyInAnyOrder(Book.class, UUID.class);
                });
    }

    @Test
    public void cacheKeyIsSourcingModeSpecific() {
        assertChangedCacheKeys(
                () -> {
                    commandRouter.send(new AddBookCommand(isbn));
                    commandRouter.send(new BorrowBookCommand(isbn));
                    commandRouter.send(new ReturnBookCommand(isbn));
                },
                delta -> {
                    assertThat(delta).hasSize(2);
                    assertThat(delta).map(CacheKey::subject).allMatch(it -> it.equals("/books/" + isbn));
                    assertThat(delta)
                            .map(CacheKey::sourcingMode)
                            .containsExactlyInAnyOrder(SourcingMode.RECURSIVE, SourcingMode.LOCAL);
                });
    }

    @Test
    public void leastRecentlyUsedCacheKeyAutomaticallyDropped() {
        commandRouter.send(new AddBookCommand(isbn));
        commandRouter.send(new BorrowBookCommand(isbn));

        IntStream.rangeClosed(1, 5).forEach(value -> {
            var otherIsbn = UUID.randomUUID().toString();
            commandRouter.send(new AddBookCommand(otherIsbn));
            commandRouter.send(new BorrowBookCommand(otherIsbn));
        });

        assertThat(cache.cache.size()).isEqualTo(6 - 1);
        assertThat(cache.cache.keySet()).map(CacheKey::subject).noneMatch(it -> it.contains(isbn));
    }

    private void assertChangedCacheKeys(Runnable r, Consumer<Set<CacheKey>> assertion) {
        Set<CacheKey> before = new HashSet<>(cache.cache.keySet());
        r.run();
        Set<CacheKey> after = new HashSet<>(cache.cache.keySet());
        after.removeAll(before);
        assertion.accept(after);
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
