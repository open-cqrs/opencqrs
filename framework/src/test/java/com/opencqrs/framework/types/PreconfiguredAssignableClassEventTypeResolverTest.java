/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PreconfiguredAssignableClassEventTypeResolverTest {

    private final PreconfiguredAssignableClassEventTypeResolver subject =
            new PreconfiguredAssignableClassEventTypeResolver(Map.of(
                    "a.v1", A.class,
                    "b.v1", B.class,
                    "sealed.v2", BaseEvent.class));

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            a.v1      | com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$A
            b.v1      | com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$B
            sealed.v2 | com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$BaseEvent
    """)
    public void classResolvedToType(String type, Class<?> clazz) {
        assertThat(subject.getJavaClass(type)).isEqualTo(clazz);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$A           | a.v1
            com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$B           | b.v1
            com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$BaseEvent   | sealed.v2
            com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$BaseEvent$C | sealed.v2
            com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolverTest$BaseEvent$D | sealed.v2
    """)
    public void typeResolvedToClass(Class<?> clazz, String type) {
        assertThat(subject.getEventType(clazz)).isEqualTo(type);
    }

    @Test
    public void ambiguousMappingDetectedUponInitialization() {
        assertThatThrownBy(() -> new PreconfiguredAssignableClassEventTypeResolver(Map.of(
                        "a", BaseEvent.class,
                        "b", BaseEvent.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void ambiguousEventTypeResolutionDetected() {
        var subject = new PreconfiguredAssignableClassEventTypeResolver(Map.of(
                "a", BaseEvent.class,
                "b", BaseEvent.C.class));

        assertThatThrownBy(() -> subject.getEventType(BaseEvent.C.class))
                .isInstanceOf(EventTypeResolutionException.class)
                .hasMessageContainingAll(
                        "ambiguous",
                        "assignable classes",
                        "event class",
                        BaseEvent.class.getName(),
                        BaseEvent.C.class.getName());
    }

    @Test
    public void unresolvableEventTypeDetected() {
        var subject = new PreconfiguredAssignableClassEventTypeResolver(Map.of("a", A.class));

        assertThatThrownBy(() -> subject.getEventType(BaseEvent.D.class))
                .isInstanceOf(EventTypeResolutionException.class)
                .hasMessageContainingAll("no assignable type", "event class", BaseEvent.D.class.getName());
    }

    record A() {}

    record B() {}

    sealed interface BaseEvent {

        record C() implements BaseEvent {}

        record D() implements BaseEvent {}
    }
}
