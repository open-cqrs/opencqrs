/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.Map;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EsdbHealthIndicatorTest {

    @Mock
    private EsdbClient client;

    @InjectMocks
    private EsdbHealthIndicator subject;

    @ParameterizedTest
    @CsvSource({
        "pass, UP",
        "warn, UP",
        "fail, DOWN",
    })
    public void esdbStatusProperlyMapped(Health.Status status, String healthStatus) {
        var checks = Map.of("foo", 42);
        doReturn(new Health(status, checks)).when(client).health();

        assertThat(subject.health()).satisfies(h -> {
            assertThat(h.getStatus().getCode()).isEqualTo(healthStatus);
            assertThat(h.getDetails()).containsEntry("status", status).containsEntry("checks", checks);
        });
    }
}
