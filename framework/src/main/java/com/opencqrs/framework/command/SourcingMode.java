/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Option;

/** The type of sourcing affecting which events will be sourced for {@link CommandHandler}s. */
public enum SourcingMode {

    /** No events will be fetched for the {@link Command#getSubject()}. */
    NONE,

    /** Events will be fetched for the {@link Command#getSubject()} non-recursively. */
    LOCAL,

    /**
     * Events will be fetched for the {@link Command#getSubject()} recursively.
     *
     * @see Option.Recursive
     */
    RECURSIVE
}
