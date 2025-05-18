/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.reader.api;

import java.util.UUID;

public record ReaderRegisteredEvent(UUID id, String name) {}
