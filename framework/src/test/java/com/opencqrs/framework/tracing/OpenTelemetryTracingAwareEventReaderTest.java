/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.EventReader;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OpenTelemetryTracingAwareEventReaderTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String TRACE_PARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
    private static final String TRACE_STATE = "alpha=t6mt,beta=00f067aa0ba902b7";

    private static final Span ACTIVE_SPAN = Span.wrap(SpanContext.create(
            "0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getSampled(), TraceState.getDefault()));

    @Mock
    private EventReader eventReader;

    @Mock
    private EventReader.ClientRequestor clientRequestor;

    @Mock
    private EventReader.RawCallback rawCallback;

    private OpenTelemetryTracingAwareEventReader subject;

    @BeforeEach
    public void setup() {
        subject = new OpenTelemetryTracingAwareEventReader(
                eventReader,
                OpenTelemetry.propagating(ContextPropagators.create(W3CTraceContextPropagator.getInstance())),
                new EventTracingContextGetter());
    }

    private static Event event(String traceParent, String traceState) {
        return new Event(
                "tag://test",
                "/books/4711",
                "com.opencqrs.books-added.v1",
                Map.of(),
                "1.0",
                "42",
                Instant.now(),
                "application/json",
                "hash",
                "predecessorHash",
                traceParent,
                traceState);
    }

    private void eventReaderSupplies(Event event) {
        doAnswer(invocation -> {
                    BiConsumer<EventReader.RawCallback, Event> eventConsumer = invocation.getArgument(1);
                    eventConsumer.accept(rawCallback, event);
                    return null;
                })
                .when(eventReader)
                .consumeRaw(same(clientRequestor), any());
    }

    @Test
    public void traceContextRestoredFromEventWithinConsumer() {
        var event = event(TRACE_PARENT, TRACE_STATE);
        eventReaderSupplies(event);

        var consumedCallback = new AtomicReference<EventReader.RawCallback>();
        var consumedEvent = new AtomicReference<Event>();
        var consumedSpanContext = new AtomicReference<SpanContext>();
        subject.consumeRaw(clientRequestor, (callback, e) -> {
            consumedCallback.set(callback);
            consumedEvent.set(e);
            consumedSpanContext.set(Span.current().getSpanContext());
        });

        assertThat(consumedCallback.get()).isSameAs(rawCallback);
        assertThat(consumedEvent.get()).isSameAs(event);
        assertThat(consumedSpanContext.get()).satisfies(spanContext -> {
            assertThat(spanContext.getTraceId()).isEqualTo(TRACE_ID);
            assertThat(spanContext.getSpanId()).isEqualTo(SPAN_ID);
            assertThat(spanContext.isSampled()).isTrue();
            assertThat(spanContext.isRemote()).isTrue();
            assertThat(spanContext.getTraceState().asMap())
                    .containsExactlyInAnyOrderEntriesOf(Map.of("alpha", "t6mt", "beta", "00f067aa0ba902b7"));
        });
    }

    @Test
    public void previousContextRestoredAfterConsumer() {
        eventReaderSupplies(event(TRACE_PARENT, null));

        var before = Context.current();
        var during = new AtomicReference<Context>();
        subject.consumeRaw(clientRequestor, (callback, e) -> during.set(Context.current()));

        assertThat(during.get()).isNotSameAs(before);
        assertThat(Context.current()).isSameAs(before);
    }

    @Test
    public void previousContextRestoredIfConsumerFails() {
        eventReaderSupplies(event(TRACE_PARENT, null));

        var before = Context.current();
        assertThatThrownBy(() -> subject.consumeRaw(clientRequestor, (callback, e) -> {
                    throw new IllegalStateException("event handler failed");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("event handler failed");

        assertThat(Context.current()).isSameAs(before);
    }

    @Test
    public void currentContextKeptIfEventCarriesNoTraceInformation() {
        eventReaderSupplies(event(null, null));

        var consumedSpanContext = new AtomicReference<SpanContext>();
        try (var ignored = ACTIVE_SPAN.makeCurrent()) {
            subject.consumeRaw(
                    clientRequestor,
                    (callback, e) -> consumedSpanContext.set(Span.current().getSpanContext()));
        }

        assertThat(consumedSpanContext.get()).isEqualTo(ACTIVE_SPAN.getSpanContext());
    }

    @Test
    public void eventTraceContextTakesPrecedenceOverCurrentContext() {
        eventReaderSupplies(event(TRACE_PARENT, null));

        var consumedSpanContext = new AtomicReference<SpanContext>();
        try (var ignored = ACTIVE_SPAN.makeCurrent()) {
            subject.consumeRaw(
                    clientRequestor,
                    (callback, e) -> consumedSpanContext.set(Span.current().getSpanContext()));
            assertThat(Span.current()).isSameAs(ACTIVE_SPAN);
        }

        assertThat(consumedSpanContext.get().getTraceId()).isEqualTo(TRACE_ID);
    }
}
