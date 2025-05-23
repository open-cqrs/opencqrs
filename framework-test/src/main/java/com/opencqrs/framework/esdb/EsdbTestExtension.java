/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.esdb;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;

public class EsdbTestExtension implements BeforeAllCallback, AfterAllCallback {

    static GenericContainer<?> esdb;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (esdb == null) {
            esdb = new GenericContainer<>(
                    "docker.io/thenativeweb/eventsourcingdb:" + System.getProperty("esdb.version"))
                    .withExposedPorts(3000)
                    .withCreateContainerCmdModifier(cmd -> cmd.withCmd(
                            "run",
                            "--api-token",
                            "secret",
                            "--data-directory-temporary",
                            "--http-enabled=true",
                            "--https-enabled=false"));
            esdb.start();

            TestPropertyValues.of(
                    "esdb.server.uri=http://" + esdb.getHost() + ":" + esdb.getFirstMappedPort(),
                    "esdb.server.api-token=secret"
            ).applyTo(getApplicationContext(context).getEnvironment());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (esdb != null) {
            esdb.stop();
        }
    }

    private ConfigurableApplicationContext getApplicationContext(ExtensionContext context) {
        return (ConfigurableApplicationContext) SpringExtension.getApplicationContext(context);
    }
}
