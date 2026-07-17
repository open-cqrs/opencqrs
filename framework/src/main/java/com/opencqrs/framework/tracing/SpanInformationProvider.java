package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandlerDefinition;

import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class SpanInformationProvider {

    public interface Customizer extends UnaryOperator<Map<String, String>> {}

    public static Supplier<Map<String, String>> forEventHandler(Integer partition, EventHandlerDefinition<?> eventHandlerDefinition, Event event) {
        return () -> {
            var result = Map.of(
                    "span.name", "event " + (eventHandlerDefinition.group() + "-" + partition),
                    "event.id", event.id(),
                    "event.type", event.type(),
                    "event.subject", event.subject(),
                    "event.source", event.source(),
                    "event.timestamp", event.time().toString(),
                    "event.class", eventHandlerDefinition.eventClass().getName(),
                    "handler.group", eventHandlerDefinition.group(),
                    "handler.partition", String.valueOf(partition)

            );

            if (eventHandlerDefinition.handler() instanceof Customizer) {
                return ((Customizer) eventHandlerDefinition.handler()).apply(result);
            }

            return result;
        };
    }
}
