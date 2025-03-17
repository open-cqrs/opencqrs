/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link JacksonEventDataMarshaller}.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnBean(ObjectMapper.class)
public class JacksonEventDataMarshallerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventDataMarshaller.class)
    public JacksonEventDataMarshaller jacksonEventSerializer(ObjectMapper objectMapper) {
        return new JacksonEventDataMarshaller(objectMapper);
    }
}
