/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Client;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Interface specifying operations for reading events from a {@link Client}. This includes reading raw {@link Event}s,
 * upcasted {@link Event}s, or events converted to Java {@link Object}s and meta-data.
 */
public interface EventReader {

    /**
     * Consumes raw {@link Event}s from the given {@link ClientRequestor} and passes them to the given event consumer.
     *
     * @param clientRequestor wrapping the client fetch operation
     * @param eventConsumer a bi-consumer called for each raw {@link Event} accompanied by a {@link RawCallback} for
     *     futher event processing, if needed
     */
    void consumeRaw(ClientRequestor clientRequestor, BiConsumer<RawCallback, Event> eventConsumer);

    /**
     * Consumes upcasted {@link Event}s from the given {@link ClientRequestor} and passes them to the given event
     * consumer.
     *
     * @param clientRequestor wrapping the client fetch operation
     * @param eventConsumer a bi-consumer called for each upcasted {@link Event} accompanied by an
     *     {@link UpcastedCallback} for further event processing, if needed
     * @see EventUpcasters
     */
    default void consumeUpcasted(ClientRequestor clientRequestor, BiConsumer<UpcastedCallback, Event> eventConsumer) {
        consumeRaw(clientRequestor, (rawCallback, event) -> rawCallback.upcast(eventConsumer));
    }

    /**
     * Consumes upcasted and converted Java event objects from the given {@link ClientRequestor} and passes them to the
     * given event consumer.
     *
     * @param clientRequestor wrapping the client fetch operation
     * @param eventConsumer a consumer called for each upcasted and converted Java event object
     * @see EventUpcasters
     * @see EventTypeResolver
     * @see EventDataMarshaller
     */
    default void consumeAsObject(ClientRequestor clientRequestor, Consumer<Object> eventConsumer) {
        consumeUpcasted(clientRequestor, (upcastedCallback, event) -> upcastedCallback.convert(eventConsumer));
    }

    /**
     * Consumes upcasted and converted meta-data and Java event objects from the given {@link ClientRequestor} and
     * passes them to the given event consumer.
     *
     * @param clientRequestor wrapping the client fetch operation
     * @param eventConsumer a bi-consumer called for each upcasted and converted meta-data and Java event object
     * @see EventUpcasters
     * @see EventTypeResolver
     * @see EventDataMarshaller
     */
    default void consumeAsObject(ClientRequestor clientRequestor, BiConsumer<Map<String, ?>, Object> eventConsumer) {
        consumeUpcasted(clientRequestor, (upcastedCallback, event) -> upcastedCallback.convert(eventConsumer));
    }

    /**
     * Retrieves a list of raw {@link Event}s matching the given subject and options.
     *
     * @param subject the subject to fetch from
     * @param options set of fetch options
     * @return a list of raw {@link Event}s, may be empty
     */
    default List<Event> readRaw(String subject, Set<Option> options) {
        var result = new ArrayList<Event>();

        consumeRaw(
                (client, eventConsumer) -> client.read(subject, options, eventConsumer),
                (callback, event) -> result.add(event));

        return result;
    }

    /**
     * Retrieves a list of upcasted {@link Event}s matching the given subject and options.
     *
     * @param subject the subject to fetch from
     * @param options set of fetch options
     * @return a list of upcasted {@link Event}s, may be empty
     */
    default List<Event> readUpcasted(String subject, Set<Option> options) {
        var result = new ArrayList<Event>();

        consumeUpcasted(
                (client, eventConsumer) -> client.read(subject, options, eventConsumer),
                (callback, event) -> result.add(event));

        return result;
    }

    /**
     * Retrieves a list of upcasted and converted Java event objects matching the given subject and options.
     *
     * @param subject the subject to fetch from
     * @param options set of fetch options
     * @return a list of Java event objects, may be empty
     */
    default List<Object> readAsObject(String subject, Set<Option> options) {
        var result = new ArrayList<>();

        consumeAsObject((client, eventConsumer) -> client.read(subject, options, eventConsumer), (Consumer<Object>)
                result::add);

        return result;
    }

    /** Generically wraps {@link Client} calls to consume {@link Event}s. */
    @FunctionalInterface
    interface ClientRequestor {

        /**
         * Encapsulates a {@link Client} call to fetch raw {@link Event}s passed to the given consumer.
         *
         * @param client the client to fetch from
         * @param eventConsumer the raw {@link Event} consumer
         */
        void request(Client client, Consumer<Event> eventConsumer);
    }

    /**
     * Callback interface specifying operations to deal with an encapsulated raw {@link Event}.
     *
     * @see #consumeRaw(ClientRequestor, BiConsumer)
     */
    @FunctionalInterface
    interface RawCallback {

        /**
         * Upcasts the encapsulated raw {@link Event} and maps resulting event stream to the given consumer.
         *
         * @param eventConsumer a bi-consumer called for each upcasted {@link Event} accompanied by an
         *     {@link UpcastedCallback} for further event processing, if needed
         * @see EventUpcasters
         */
        void upcast(BiConsumer<UpcastedCallback, Event> eventConsumer);
    }

    /** Callback interface specifying operations to deal with an encapsulated upcasted {@link Event}. */
    interface UpcastedCallback {

        /**
         * Determines the Java {@link Class} for the encapsulated {@link Event}.
         *
         * @return the associated Java type
         * @see EventTypeResolver
         */
        Class<?> getEventJavaClass();

        /**
         * Converts the encapsulated {@link Event} to a Java object of the type determined by
         * {@link #getEventJavaClass()} and maps it to the given consumer.
         *
         * @param eventConsumer consumer for the converted Java event object
         * @see EventTypeResolver
         * @see EventDataMarshaller
         */
        default void convert(Consumer<Object> eventConsumer) {
            convert((metaData, o) -> eventConsumer.accept(o));
        }

        /**
         * Converts the encapsulated {@link Event} to meta-data and a Java object of the type determined by
         * {@link #getEventJavaClass()} and maps it to the given consumer.
         *
         * @param eventConsumer bi-consumer for the meta-data map and the converted Java event object
         * @see EventTypeResolver
         * @see EventDataMarshaller
         */
        void convert(BiConsumer<Map<String, ?>, Object> eventConsumer);
    }
}
