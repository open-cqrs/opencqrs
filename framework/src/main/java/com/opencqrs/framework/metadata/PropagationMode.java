/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

/**
 * Specifies how meta-data is going to be propagated from source (for instance command meta-data) to destination (for
 * instance published event meta-data).
 *
 * @see MetaDataPropagatingCommandInterceptor
 */
public enum PropagationMode {

    /** Specifies that no meta-data keys (and values) will be propagated from source to destination. */
    NONE,

    /**
     * Specifies that meta-data will be propagated from source to destination, but meta-data keys already present within
     * the destination will not be overridden.
     */
    KEEP_IF_PRESENT,

    /**
     * Specifies that meta-data will be propagated from source to destination, overriding any meta-data keys present in
     * both source and destination.
     */
    OVERRIDE_IF_PRESENT,
}
