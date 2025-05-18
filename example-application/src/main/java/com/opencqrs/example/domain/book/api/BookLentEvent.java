/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import java.util.UUID;

public record BookLentEvent(String isbn, UUID reader) {}
