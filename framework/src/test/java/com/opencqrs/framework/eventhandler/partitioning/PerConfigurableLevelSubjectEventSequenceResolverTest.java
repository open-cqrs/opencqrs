/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencqrs.esdb.client.Event;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PerConfigurableLevelSubjectEventSequenceResolverTest {

    @ParameterizedTest
    @CsvSource({
        "2,     /book/4711/pages/2444,  /book/4711",
        "2,     /book/4711,             /book/4711",
        "3,     /book/4711,             /book/4711",
        "1,     /book/4711,             /book",
        "2,     /book/4711/,            /book/4711",
        "2,     /,                      /",
        "2,     ignored/book/4711/43,   /book/4711",
    })
    public void foo(int levelsToKeep, String subject, String sequenceId) {
        var resolver = new PerConfigurableLevelSubjectEventSequenceResolver(levelsToKeep);

        Event e = new Event(null, subject, null, null, null, null, null, null, null, null, null, null);

        assertThat(resolver.sequenceIdFor(e)).isEqualTo(sequenceId);
    }
}
