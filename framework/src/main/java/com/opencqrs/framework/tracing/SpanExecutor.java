package com.opencqrs.framework.tracing;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface SpanExecutor {

    <R> R execute(Supplier<Map<String, String>> initialSpanInformation, Function<Callback, R> execution);

    interface Callback {
        void enrichSpan(String key, String value);

        void markSuccess();
        void markFailure(String error);
    }
}
