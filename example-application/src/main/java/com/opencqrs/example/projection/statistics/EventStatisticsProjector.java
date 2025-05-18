/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.statistics;

import com.opencqrs.esdb.client.Event;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics/events")
public class EventStatisticsProjector {

    private static final ZoneId zoneId = ZoneId.of("Europe/Berlin");
    private final Map<LocalDate, Stats> statistics = new HashMap<>();

    @GetMapping
    public Map<LocalDate, Stats> fetchStatistics() {
        return statistics;
    }

    @StatisticsHandling
    public void on(Event event) {
        Stats stats = statistics.computeIfAbsent(LocalDate.ofInstant(event.time(), zoneId), localDate -> new Stats());
        stats.total++;
        stats.eventTypes.merge(event.type(), 1L, Long::sum);
        stats.subjects.merge(event.subject(), 1L, Long::sum);
    }

    public class Stats {
        public long total = 0;
        public SortedMap<String, Long> eventTypes = new TreeMap<>();
        public SortedMap<String, Long> subjects = new TreeMap<>();
    }
}
