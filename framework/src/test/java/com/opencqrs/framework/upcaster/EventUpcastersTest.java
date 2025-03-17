/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.Event;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventUpcastersTest {

    private final Event sourceEvent = new Event(
            "source",
            "subject",
            "original",
            Map.of("sourced", 1),
            "1.0",
            "0",
            Instant.now(),
            "application/json",
            "1",
            "0");

    @Mock(strictness = Mock.Strictness.LENIENT)
    private EventUpcaster upcaster1;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private EventUpcaster upcaster2;

    @Test
    public void unalteredEventIfNoUpcasterPresent() {
        EventUpcasters subject = new EventUpcasters();

        assertThat(subject.upcast(sourceEvent).collect(toList()))
                .singleElement()
                .isSameAs(sourceEvent);
    }

    @Test
    public void unalteredEventIfUpcasterCannotUpcast() {
        doReturn(false).when(upcaster1).canUpcast(sourceEvent);

        EventUpcasters subject = new EventUpcasters(upcaster1);

        assertThat(subject.upcast(sourceEvent).collect(toList()))
                .singleElement()
                .isSameAs(sourceEvent);

        verify(upcaster1, never()).upcast(any());
    }

    @Test
    public void eventUpcastedOnce() {
        EventUpcaster.Result result = new EventUpcaster.Result("upcasted", Map.of("upcasted", 42));

        doReturn(true).when(upcaster1).canUpcast(sourceEvent);
        doReturn(Stream.of(result)).when(upcaster1).upcast(sourceEvent);

        EventUpcasters subject = new EventUpcasters(upcaster1);

        assertThat(subject.upcast(sourceEvent).collect(toList()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e)
                            .usingRecursiveComparison()
                            .ignoringFields("type", "data")
                            .isEqualTo(sourceEvent);
                    assertThat(e.type()).isSameAs(result.type());
                    assertThat(e.data()).isSameAs(result.data());
                });
    }

    @Test
    public void eventUpcastedChained() {
        EventUpcaster.Result intermediateResult = new EventUpcaster.Result("intermediate", Map.of("intermediate", 24));
        EventUpcaster.Result finalResult = new EventUpcaster.Result("upcasted", Map.of("upcasted", 42));

        doReturn(true).when(upcaster1).canUpcast(sourceEvent);
        doReturn(Stream.of(intermediateResult)).when(upcaster1).upcast(sourceEvent);
        doReturn(true).when(upcaster2).canUpcast(argThat(event -> event.type().equals(intermediateResult.type())));
        doReturn(Stream.of(finalResult)).when(upcaster2).upcast(argThat(event -> event.type()
                .equals(intermediateResult.type())));

        EventUpcasters subject = new EventUpcasters(upcaster2, upcaster1);

        assertThat(subject.upcast(sourceEvent).collect(toList()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e)
                            .usingRecursiveComparison()
                            .ignoringFields("type", "data")
                            .isEqualTo(sourceEvent);
                    assertThat(e.type()).isSameAs(finalResult.type());
                    assertThat(e.data()).isSameAs(finalResult.data());
                });
    }

    @Test
    public void eventUpcastedToMultipleEvents() {
        EventUpcaster.Result result1 = new EventUpcaster.Result("upcasted1", Map.of("upcasted", 42));
        EventUpcaster.Result result2 = new EventUpcaster.Result("upcasted2", Map.of("upcasted", 44));

        doReturn(true).when(upcaster1).canUpcast(sourceEvent);
        doReturn(Stream.of(result1, result2)).when(upcaster1).upcast(sourceEvent);

        EventUpcasters subject = new EventUpcasters(upcaster1);

        assertThat(subject.upcast(sourceEvent).collect(toList()))
                .hasSize(2)
                .anySatisfy(e -> {
                    assertThat(e)
                            .usingRecursiveComparison()
                            .ignoringFields("type", "data")
                            .isEqualTo(sourceEvent);
                    assertThat(e.type()).isSameAs(result1.type());
                    assertThat(e.data()).isSameAs(result1.data());
                })
                .anySatisfy(e -> {
                    assertThat(e)
                            .usingRecursiveComparison()
                            .ignoringFields("type", "data")
                            .isEqualTo(sourceEvent);
                    assertThat(e.type()).isSameAs(result2.type());
                    assertThat(e.data()).isSameAs(result2.data());
                });
    }

    @Test
    public void eventUpcastedToNoEvents() {
        doReturn(true).when(upcaster1).canUpcast(sourceEvent);
        doReturn(Stream.empty()).when(upcaster1).upcast(sourceEvent);

        EventUpcasters subject = new EventUpcasters(upcaster1);

        assertThat(subject.upcast(sourceEvent).collect(toList())).isEmpty();
    }
}
