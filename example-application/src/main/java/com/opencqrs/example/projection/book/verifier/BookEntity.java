/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.book.verifier;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class BookEntity {

    @Id
    public String isbn;

    public Long pages;

    public BookEntity() {}

    public BookEntity(String isbn, Long pages) {
        this.isbn = isbn;
        this.pages = pages;
    }
}
