/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book.api;

public record BookPurchasedEvent(String isbn, String author, String title, long numPages) {}
