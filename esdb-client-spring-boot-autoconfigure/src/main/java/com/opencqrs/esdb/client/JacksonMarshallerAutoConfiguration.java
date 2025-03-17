/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.jackson.JacksonMarshaller;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link JacksonMarshaller}. */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnBean(ObjectMapper.class)
public class JacksonMarshallerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Marshaller.class)
    public JacksonMarshaller jacksonMarshaller(ObjectMapper objectMapper) {
        return new JacksonMarshaller(objectMapper);
    }
}
