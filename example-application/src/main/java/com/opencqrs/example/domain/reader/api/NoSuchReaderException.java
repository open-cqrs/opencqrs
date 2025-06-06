/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.reader.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoSuchReaderException extends RuntimeException {}
