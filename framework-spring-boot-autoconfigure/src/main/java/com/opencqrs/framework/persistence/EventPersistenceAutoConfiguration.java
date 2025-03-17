/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Client;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link EventRepository} and {@link EventSource}.
 */
@AutoConfiguration
public class EventPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventSource eventSource(Environment environment) {
        return new EventSource(environment.getProperty("spring.application.name"));
    }

    @Bean
    @ConditionalOnMissingBean
    public EventRepository eventRepository(
            Client client,
            EventSource eventSource,
            EventTypeResolver eventTypeResolver,
            EventDataMarshaller eventDataMarshaller,
            EventUpcasters eventUpcasters) {
        return new EventRepository(client, eventSource, eventTypeResolver, eventDataMarshaller, eventUpcasters);
    }
}
