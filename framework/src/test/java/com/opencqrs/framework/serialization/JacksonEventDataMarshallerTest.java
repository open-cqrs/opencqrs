/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.serialization;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.CqrsFrameworkException;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.DatabindException;

@JsonTest
@Import({
    JacksonAutoConfiguration.class,
    JacksonEventDataMarshallerAutoConfiguration.class,
})
public class JacksonEventDataMarshallerTest {

    @Autowired
    private JacksonEventDataMarshaller subject;

    @Autowired
    private JacksonTester<Map<String, ?>> jacksonTester;

    @Test
    public void serializesEventDataToJsonMap() throws IOException {
        Map<String, ?> serialized =
                subject.serialize(new EventData<>(Map.of("answer", 42, "flag", true), new BookAddedEvent("4711")));

        JsonContent<Map<String, ?>> jsonContent = jacksonTester.write(serialized);

        assertThat(jsonContent)
                .isEqualToJson(
                        """
                                {
                                    "metadata" : {
                                        "answer" : 42,
                                        "flag": true
                                    },
                                    "payload" : {
                                        "isbn": "4711"
                                    }
                                }
                                """);
    }

    @Test
    public void serializationErrorMappedToNonTransientException() {
        assertThatThrownBy(() -> subject.serialize(new EventData<>(Map.of("answer", 42, "flag", true), 42L)))
                .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                .hasCauseInstanceOf(DatabindException.class);
    }

    @Test
    public void deserializesJsonMapToEventData() throws IOException {
        Map<String, ?> json = jacksonTester
                .parse(
                        """
                {
                    "metadata" : {
                        "answer" : 42,
                        "flag": true
                    },
                    "payload" : {
                        "isbn": "4711"
                    }
                }
                """)
                .getObject();

        EventData<BookAddedEvent> deserialized = subject.deserialize(json, BookAddedEvent.class);

        assertThat(deserialized.metaData()).isEqualTo(Map.of("answer", 42, "flag", true));

        assertThat(deserialized.payload()).isEqualTo(new BookAddedEvent("4711"));
    }

    @Test
    public void deserializationErrorMappedToNonTransientException() throws IOException {
        Map<String, ?> json = jacksonTester
                .parse(
                        """
                {
                    "metadata" : {
                        "answer" : 42,
                        "flag": true
                    },
                    "payload" : {
                        "isbn" : "4711"
                    }
                }
                """)
                .getObject();

        assertThatThrownBy(() -> subject.deserialize(json, Long.class))
                .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                .hasCauseInstanceOf(DatabindException.class);
    }

    @Test
    public void handlesPolymorphicEventSerialization() throws IOException {
        var event = new PolymorphicEvent.A(new PolymorphicEvent.X.Y("test"));

        Map<String, ?> serialized = subject.serialize(new EventData<>(Map.of(), event));

        assertThat(subject.deserialize(serialized, PolymorphicEvent.class).payload())
                .isNotSameAs(event)
                .isEqualTo(event);

        JsonContent<Map<String, ?>> jsonContent = jacksonTester.write(serialized);
        assertThat(jsonContent).extractingJsonPathStringValue("$.payload.type").isEqualTo("a");
        assertThat(jsonContent)
                .extractingJsonPathStringValue("$.payload.x.type")
                .isEqualTo("y");
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
    @JsonSubTypes(@JsonSubTypes.Type(value = PolymorphicEvent.A.class, name = "a"))
    sealed interface PolymorphicEvent {

        record A(X x) implements PolymorphicEvent {}

        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
        @JsonSubTypes(@JsonSubTypes.Type(value = PolymorphicEvent.X.Y.class, name = "y"))
        sealed interface X {
            record Y(String message) implements X {}
        }
    }
}
