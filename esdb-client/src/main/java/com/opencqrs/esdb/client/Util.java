/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.net.http.HttpHeaders;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class Util {

    private static final String CHARSET_KEY = "charset=";

    /**
     * Determines the {@link Charset} from the {@code Content-Type} {@link HttpHeaders}.
     *
     * @param headers the HTTP headers to inspect
     * @return the charset or {@linkplain StandardCharsets#UTF_8 UTF-8} as fall-back, if none (or an invalid one) was
     *     specified
     */
    public static Charset fromHttpHeaders(HttpHeaders headers) {
        return headers.firstValue("Content-Type")
                .map(it -> {
                    int i = it.indexOf(CHARSET_KEY);
                    if (i >= 0) {
                        return it.substring(i + CHARSET_KEY.length()).split(";")[0];
                    } else {
                        return null;
                    }
                })
                .map(name -> Charset.forName(name, StandardCharsets.UTF_8))
                .orElse(StandardCharsets.UTF_8);
    }
}
