/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record Book(String isbn, long numPages, Set<Long> damagedPages, Lending lending) {

    public sealed interface Lending {
        record Lent(UUID reader) implements Lending {}

        record Available() implements Lending {}
    }

    public Book with(Lending lending) {
        return new Book(isbn(), numPages(), damagedPages(), lending);
    }

    public Book withDamagedPage(Long page) {
        var damaged = new HashSet<>(damagedPages);
        damaged.add(page);

        return new Book(isbn(), numPages(), damaged, lending);
    }
}
