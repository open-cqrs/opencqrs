/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class PropagationUtilTest {

    @ParameterizedTest
    @EnumSource(value = PropagationMode.class, mode = EnumSource.Mode.EXCLUDE, names = "NONE")
    public void nonConflictingMetaDataPropagated(PropagationMode mode) {
        Map source = Map.of("source01", 42L);
        Map destination = Map.of("destination01", true);

        assertThat(PropagationUtil.propagateMetaData(destination, source, mode))
                .containsAllEntriesOf(source)
                .containsAllEntriesOf(destination);
    }

    @Test
    public void noMetaDataPropagated() {
        Map source = Map.of("source01", 42L);
        Map destination = Map.of("destination01", true);

        assertThat(PropagationUtil.propagateMetaData(destination, source, PropagationMode.NONE))
                .containsAllEntriesOf(destination)
                .doesNotContainKeys(source.keySet().toArray());
    }

    @Test
    public void metaDataPropagatedDuplicatesPreserved() {
        Map source = Map.of("source01", 42L, "destination01", false);
        Map destination = Map.of("destination01", true);

        assertThat(PropagationUtil.propagateMetaData(destination, source, PropagationMode.KEEP_IF_PRESENT))
                .isEqualTo(Map.of("source01", 42L, "destination01", true));
    }

    @Test
    public void metaDataPropagatedDuplicatesOverridden() {
        Map source = Map.of("source01", 42L, "destination01", false);
        Map destination = Map.of("destination01", true);

        assertThat(PropagationUtil.propagateMetaData(destination, source, PropagationMode.OVERRIDE_IF_PRESENT))
                .isEqualTo(Map.of("source01", 42L, "destination01", false));
    }
}
