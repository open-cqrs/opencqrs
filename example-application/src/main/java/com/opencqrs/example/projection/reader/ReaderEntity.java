/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.reader;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class ReaderEntity {
    @Id
    public UUID id;
}
