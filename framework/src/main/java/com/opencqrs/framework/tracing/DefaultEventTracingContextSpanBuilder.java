/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;

public class DefaultEventTracingContextSpanBuilder implements EventTracingContextSpanBuilder {
    @Override
    public void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable) {
        runnable.run();
    }
}
