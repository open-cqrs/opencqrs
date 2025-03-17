/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import java.util.HashMap;
import java.util.Map;

/** Util class for meta-data propagation. */
public class PropagationUtil {

    public static Map<String, ?> propagateMetaData(
            Map<String, ?> metaData, Map<String, ?> propagationData, PropagationMode mode) {
        if (mode == PropagationMode.NONE) return metaData;

        var result = new HashMap<String, Object>(metaData);
        propagationData.forEach((key, value) -> result.merge(key, value, (o1, o2) -> switch (mode) {
            case KEEP_IF_PRESENT -> o1;
            case OVERRIDE_IF_PRESENT -> o2;
            default -> throw new IllegalStateException("Unexpected value: " + mode);
        }));

        return result;
    }
}
