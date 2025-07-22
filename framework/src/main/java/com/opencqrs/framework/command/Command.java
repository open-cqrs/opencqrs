/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

/**
 * Interface to be implemented by commands that can be handled by {@link CommandHandler}s.
 *
 * @see CommandRouter#send(Command)
 */
public interface Command {

    /**
     * Specifies the target subject of this command used as default for publishing new events. The specified subject is
     * also used by the {@link CommandHandling.UsingSubject} command handling mode to determine the subject where events
     * are sourced from.
     *
     * @return the subject path
     */
    String getSubject();
}
