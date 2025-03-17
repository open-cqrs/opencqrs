/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface to be implemented when {@link Event}s need to be migrated to a new representation, so-called upcasting.
 * This is required, as {@link Event}s are immutable and thus need to be migrated on-the-fly.
 *
 * <p>Event upcasting is limited to {@link Event#type()} and {@link Event#data()}, represented by {@link Result}.
 *
 * <p><strong>This interface (and direct implementations) operate on the low-level {@link Event} and hence may have to
 * use an additional {@link EventDataMarshaller} if {@link Event#data()} needs to be upcasted.
 * {@link AbstractEventDataMarshallingEventUpcaster} may be inherited to gain access to {@link EventData} based
 * upcasting.</strong>
 *
 * @see EventUpcasters
 * @see AbstractEventDataMarshallingEventUpcaster
 */
public interface EventUpcaster {

    /**
     * Determines if {@code this} upcaster is relevant for upcasting the given {@link Event}. This method must be called
     * prior to {@link #upcast(Event)}.
     *
     * @param event the event that may need to be upcasted
     * @return {@code true} if {@code this} can upcast the event, {@code false} otherwise
     */
    boolean canUpcast(Event event);

    /**
     * Upcasts the given event to a stream of {@link Result}s. This allows implementations to:
     *
     * <ul>
     *   <li>effectively drop an event by returning an empty stream
     *   <li>upcast an event to one new event by returning a single element stream
     *   <li>effectively split up an event by returning a multi element stream
     * </ul>
     *
     * @param event the event to be upcasted
     * @return a stream of {@link Result}s carrying the upcasted {@link Event#type()} and {@link Event#data()}
     */
    Stream<Result> upcast(Event event);

    /**
     * Captures upcasted {@link Event#type()} and {@link Event#data()}.
     *
     * @param type the potentially modified {@link Event#type()}
     * @param data the potentially modified {@link Event#data()}
     */
    record Result(@NotBlank String type, @NotNull Map<String, ?> data) {}
}
