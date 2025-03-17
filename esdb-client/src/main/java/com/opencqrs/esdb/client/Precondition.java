/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Sealed interface for preconditions used for {@link Client#write(List, List) event publication} to ensure consistency
 * within the underlying event store.
 */
public sealed interface Precondition permits Precondition.SubjectIsOnEventId, Precondition.SubjectIsPristine {

    /**
     * A precondition stating the given subject must not yet exist within the event store. This precondition is not
     * violated by recursive subjects, that is subjects that are stored within a hierarchy underneath the given one.
     *
     * @param subject the path to the subject that needs to be pristine
     */
    record SubjectIsPristine(@NotBlank String subject) implements Precondition {}

    /**
     * A precondition stating the given subject must have been updated by the given event id. The precondition is
     * violated if either the subject does not exist at all or an event with another id has already been published for
     * that subject.
     *
     * @param subject the path to the subject
     * @param eventId the expected event id
     */
    record SubjectIsOnEventId(@NotBlank String subject, @NotBlank String eventId) implements Precondition {}
}
