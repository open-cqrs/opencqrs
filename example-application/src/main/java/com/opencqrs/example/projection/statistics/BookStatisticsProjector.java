/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.statistics;

import com.opencqrs.example.domain.book.api.BookLentEvent;
import com.opencqrs.example.domain.book.api.BookPageDamagedEvent;
import com.opencqrs.example.domain.book.api.BookPurchasedEvent;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics/books")
public class BookStatisticsProjector {

    private final Map<String, BookStats> bookStats = new HashMap<>();

    @GetMapping
    public Map<String, BookStats> fetchBookStatistics() {
        return bookStats;
    }

    @StatisticsHandling
    public void on(BookPurchasedEvent event) {
        bookStats.merge(event.isbn(), new BookStats(0L, event.numPages(), 0L), this::merge);
    }

    @StatisticsHandling
    public void on(BookLentEvent event) {
        bookStats.merge(event.isbn(), new BookStats(1L, 0L, 0L), this::merge);
    }

    @StatisticsHandling
    public void on(BookPageDamagedEvent event) {
        bookStats.merge(event.isbn(), new BookStats(0L, 0L, 1L), this::merge);
    }

    private BookStats merge(BookStats a, BookStats b) {
        return new BookStats(a.lent() + b.lent(), a.numPages() + b.numPages(), a.damagedPages() + b.damagedPages());
    }

    record BookStats(Long lent, Long numPages, Long damagedPages) {}
}
