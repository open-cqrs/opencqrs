/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

/** Mixin for Command/Event/State Rebuilding Handlers to provide additional information for tracing */
public interface TracingSpanInformationSource {

    /** @return simple name of the handler's enclosing class */
    String getHandlingClassSimpleName();

    /** @return full name of the handler's enclosing class */
    String getHandlingClassFullName();

    /** @return signature of the handling method */
    String getHandlingMethodSignature();
}
