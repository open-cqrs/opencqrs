/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework;

public record BookAddedEvent(String isbn) implements MyEvent {}
