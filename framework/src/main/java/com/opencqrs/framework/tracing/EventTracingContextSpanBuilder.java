/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;

public interface EventTracingContextSpanBuilder {

    void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable);
}
