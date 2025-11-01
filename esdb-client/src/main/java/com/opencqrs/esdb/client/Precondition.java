/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.opencqrs.esdb.client.eventql.EventQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Sealed interface for preconditions used for {@link EsdbClient#write(List, List) event publication} to ensure
 * consistency within the underlying event store.
 */
public sealed interface Precondition
        permits Precondition.SubjectIsOnEventId,
                Precondition.SubjectIsPristine,
                Precondition.SubjectIsPopulated,
                Precondition.EventQlQueryIsTrue {

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

    /**
     * A precondition stating the given subject must already exist within the event store, that is at least one event
     * must have been published for that subject. This precondition is not violated by recursive subjects, that is
     * subjects that are stored within a hierarchy underneath the given one.
     *
     * @param subject the path to the subject that needs to be populated
     */
    record SubjectIsPopulated(@NotBlank String subject) implements Precondition {}

    /**
     * A precondition stating the given {@link com.opencqrs.esdb.client.eventql.EventQuery} must evaluate to
     * {@code true}. This precondition allows for complex conditional logic when publishing events.
     *
     * @param query the EventQL query that must evaluate to {@code true}
     */
    record EventQlQueryIsTrue(@NotNull EventQuery query) implements Precondition {}
}
