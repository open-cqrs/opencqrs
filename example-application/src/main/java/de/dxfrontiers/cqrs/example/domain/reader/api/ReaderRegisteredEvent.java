/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.reader.api;

import java.util.UUID;

public record ReaderRegisteredEvent(UUID id, String name) {}
