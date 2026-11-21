/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencqrs.framework.command.interceptor.CommandInterceptor;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class MetaDataPropagatingCommandInterceptorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MetaDataPropagatingCommandInterceptorAutoConfiguration.class));

    @Test
    public void noInterceptorByDefaultWhenNoKeysConfigured() {
        runner.run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(MetaDataPropagatingCommandInterceptor.class));
    }

    @Test
    public void interceptorRegisteredWhenKeysConfigured() {
        runner.withPropertyValues("opencqrs.metadata.propagation.keys=correlation-id,tenant")
                .run(context ->
                        assertThat(context).hasNotFailed().hasSingleBean(MetaDataPropagatingCommandInterceptor.class));
    }

    @Test
    public void noInterceptorWhenModeNoneEvenWithKeys() {
        runner.withPropertyValues(
                        "opencqrs.metadata.propagation.mode=none", "opencqrs.metadata.propagation.keys=correlation-id")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(MetaDataPropagatingCommandInterceptor.class));
    }

    @Test
    public void backsOffWhenUserProvidesOwnInterceptor() {
        MetaDataPropagatingCommandInterceptor custom =
                new MetaDataPropagatingCommandInterceptor(PropagationMode.OVERRIDE_IF_PRESENT, Set.of("x"));
        runner.withPropertyValues("opencqrs.metadata.propagation.keys=correlation-id")
                .withBean(MetaDataPropagatingCommandInterceptor.class, () -> custom)
                .run(context -> assertThat(context)
                        .hasSingleBean(MetaDataPropagatingCommandInterceptor.class)
                        .getBean(MetaDataPropagatingCommandInterceptor.class)
                        .isSameAs(custom));
    }

    @Test
    public void sortsOutermostAmongInterceptors() {
        runner.withPropertyValues("opencqrs.metadata.propagation.keys=correlation-id")
                .withBean(CommandInterceptor.class, Mockito::mock)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var ordered = context.getBeanProvider(CommandInterceptor.class)
                            .orderedStream()
                            .toList();
                    assertThat(ordered).hasSize(2).first().isInstanceOf(MetaDataPropagatingCommandInterceptor.class);
                });
    }
}
