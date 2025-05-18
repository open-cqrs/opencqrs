/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.book.verifier;

import org.springframework.data.repository.CrudRepository;

public interface BookRepository extends CrudRepository<BookEntity, String> {}
