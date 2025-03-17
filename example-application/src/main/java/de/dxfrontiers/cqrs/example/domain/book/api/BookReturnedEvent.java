/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book.api;

import java.util.UUID;

public record BookReturnedEvent(String isbn, UUID reader) {}
