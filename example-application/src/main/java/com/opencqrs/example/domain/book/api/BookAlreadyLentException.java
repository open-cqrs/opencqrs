/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class BookAlreadyLentException extends RuntimeException {}
