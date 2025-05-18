/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

public record BookPurchasedEvent(String isbn, String author, String title, long numPages) {}
