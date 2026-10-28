/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

import com.opencqrs.esdb.client.Event;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventTracingContextGetterTest {

    private EventTracingContextGetter subject = new EventTracingContextGetter();

    private final String TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private final String TRACE_STATE = "alpha=t6mt,beta=00f067aa0ba902b7";

    @Test
    public void shouldExtractNoTraceContext() {
        Event mocked = Mockito.mock(Mockito.RETURNS_DEEP_STUBS);
        doReturn(null).when(mocked).traceParent();

        assertFalse(subject.keys(mocked).iterator().hasNext());
    }

    @Test
    public void shouldExtractTraceParentButNoTracState() {
        Event mocked = Mockito.mock(Mockito.RETURNS_DEEP_STUBS);
        doReturn(TRACE_PARENT).when(mocked).traceParent();
        doReturn(null).when(mocked).traceState();

        List<String> keys = (List<String>) subject.keys(mocked);
        assertEquals(1, keys.size());

        String key = keys.getFirst();
        assertEquals("traceparent", key);

        assertEquals(TRACE_PARENT, subject.get(mocked, key));
    }

    @Test
    public void shouldExtractTraceParentAndTracState() {
        Event mocked = Mockito.mock(Mockito.RETURNS_DEEP_STUBS);
        doReturn(TRACE_PARENT).when(mocked).traceParent();
        doReturn(TRACE_STATE).when(mocked).traceState();

        List<String> keys = (List<String>) subject.keys(mocked);
        assertEquals(2, keys.size());

        String traceParentKey = keys.getFirst();
        assertEquals("traceparent", traceParentKey);

        String traceStateKey = keys.get(1);
        assertEquals("tracestate", traceStateKey);

        assertEquals(TRACE_PARENT, subject.get(mocked, traceParentKey));
        assertEquals(TRACE_STATE, subject.get(mocked, traceStateKey));
    }
}
