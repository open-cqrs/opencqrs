/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

/**
 * Whether a raw event &mdash; and each converted event it upcasts into &mdash; belongs to the partition the owning
 * processor is responsible for. Carried on {@link EventInvocation}. Whether a matching handler exists is a separate
 * concern, captured by {@link Delivery} rather than here.
 *
 * @see Delivery
 */
public enum Relevance {

    /** The raw event and every converted event it upcasts into belong to this processor's partition. */
    YES,

    /** None belongs to this processor's partition &mdash; a wrong-partition event, or upcasting produced no events. */
    NO,

    /** Some of the converted events belong to this processor's partition, some do not. */
    PARTIAL,
}
