/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DummyConfiguration {

    @Bean
    public Object notInitialized() {
        return new Object();
    }
}
