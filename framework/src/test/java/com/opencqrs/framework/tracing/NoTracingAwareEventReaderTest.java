/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import static org.mockito.Mockito.verify;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.EventReader;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NoTracingAwareEventReaderTest {

    @Mock
    private EventReader eventReader;

    @Mock
    private EventReader.ClientRequestor clientRequestor;

    @Mock
    private BiConsumer<EventReader.RawCallback, Event> eventConsumer;

    @InjectMocks
    private NoTracingAwareEventReader subject;

    @Test
    public void consumeRawDelegatedUnchanged() {
        subject.consumeRaw(clientRequestor, eventConsumer);

        verify(eventReader).consumeRaw(clientRequestor, eventConsumer);
    }
}
