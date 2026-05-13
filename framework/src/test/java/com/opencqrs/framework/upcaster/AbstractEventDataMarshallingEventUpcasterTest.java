/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.EsdbClientAutoConfiguration;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = EsdbClientAutoConfiguration.class)
public class AbstractEventDataMarshallingEventUpcasterTest {

    @TestConfiguration
    public static class MyUpcasterConfiguration {

        @Bean
        public AbstractEventDataMarshallingEventUpcaster subject(EventDataMarshaller eventDataMarshaller) {
            return new AbstractEventDataMarshallingEventUpcaster(eventDataMarshaller) {

                @Override
                public boolean canUpcast(Event event) {
                    return "original".equals(event.type());
                }

                @Override
                protected Stream<MetaDataAndPayloadResult> doUpcast(
                        Event event, Map<String, ?> metaData, Map<String, ?> payload) {
                    Map metaData1 = new HashMap(metaData);
                    metaData1.put("key1", "upcasted1");
                    Map payload1 = new HashMap(payload);
                    payload1.put("isbn", "upcasted1");

                    Map metaData2 = new HashMap(metaData);
                    metaData2.put("key1", "upcasted2");
                    Map payload2 = new HashMap(payload);
                    payload2.put("isbn", "upcasted2");

                    return Stream.of(
                            new MetaDataAndPayloadResult("upcasted", metaData1, payload1),
                            new MetaDataAndPayloadResult("upcasted", metaData2, payload2));
                }
            };
        }
    }

    @MockitoBean
    private EsdbClient notUsed;

    @Autowired
    private EventDataMarshaller eventDataMarshaller;

    @Autowired
    private EventUpcasters eventUpcasters;

    @Test
    public void upcastsUnmarshalledEventDataToMultipleEvents() {
        Stream<Event> upcasted = eventUpcasters.upcast(new Event(
                "source",
                "subject",
                "original",
                eventDataMarshaller.serialize(new EventData<>(
                        Map.of("key1", "not-upcasted", "key2", "unaffected"), new BookAddedEvent("not-upcasted"))),
                "1.0",
                "001",
                Instant.now(),
                "application/json",
                "hash",
                "predecessor",
                null,
                null));

        assertThat(upcasted)
                .hasSize(2)
                .map(event -> eventDataMarshaller.deserialize(event.data(), BookAddedEvent.class))
                .anySatisfy(eventData -> {
                    assertThat(eventData.metaData()).isEqualTo(Map.of("key1", "upcasted1", "key2", "unaffected"));
                    assertThat(eventData.payload()).isEqualTo(new BookAddedEvent("upcasted1"));
                })
                .anySatisfy(eventData -> {
                    assertThat(eventData.metaData()).isEqualTo(Map.of("key1", "upcasted2", "key2", "unaffected"));
                    assertThat(eventData.payload()).isEqualTo(new BookAddedEvent("upcasted2"));
                });
    }
}
