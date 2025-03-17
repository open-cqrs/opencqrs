/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes(@JsonSubTypes.Type(value = BookPageDamagedEvent.ByReader.class, name = "by-reader"))
public sealed interface BookPageDamagedEvent {

    String isbn();

    Long page();

    record ByReader(String isbn, Long page, UUID reader) implements BookPageDamagedEvent {}
}
