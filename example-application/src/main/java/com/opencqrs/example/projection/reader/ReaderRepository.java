/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.reader;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface ReaderRepository extends CrudRepository<ReaderEntity, UUID> {}
