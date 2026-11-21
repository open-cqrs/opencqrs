/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RegistrationGuardTest {

    private final RegistrationGuard subject = new RegistrationGuard();

    @Test
    void openBeforeFreeze() {
        assertThat(subject.isFrozen()).isFalse();
        assertThatCode(subject::ensureOpen).doesNotThrowAnyException();
    }

    @Test
    void rejectsRegistrationAfterFreeze() {
        subject.freeze();

        assertThat(subject.isFrozen()).isTrue();
        assertThatThrownBy(subject::ensureOpen)
                .isInstanceOf(InterceptorContractViolation.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void freezeIsIdempotent() {
        subject.freeze();
        assertThatCode(subject::freeze).doesNotThrowAnyException();
        assertThat(subject.isFrozen()).isTrue();
    }
}
