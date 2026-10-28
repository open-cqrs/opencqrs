/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.EventReader;
import java.util.function.BiConsumer;

public interface TracingAwareEventReader {

    // resurrects trace, if present, before calling the BiConsumer
    void consumeRaw(
            EventReader.ClientRequestor clientRequestor, BiConsumer<EventReader.RawCallback, Event> eventConsumer);
}
