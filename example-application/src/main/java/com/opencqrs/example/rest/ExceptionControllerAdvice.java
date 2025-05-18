/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.rest;

import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ExceptionControllerAdvice {

    private Map<String, Object> jsonError(Exception e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(CqrsFrameworkException.TransientException.class)
    @ResponseStatus(value = HttpStatus.CONFLICT)
    @ResponseBody
    public Map<String, Object> transientErrors(CqrsFrameworkException.TransientException e) {
        return jsonError(e);
    }

    @ExceptionHandler(CqrsFrameworkException.NonTransientException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, Object> nonTansientErrors(CqrsFrameworkException.NonTransientException e) {
        return jsonError(e);
    }

    @ExceptionHandler(CommandSubjectDoesNotExistException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ResponseBody
    public Map<String, Object> subjectNotFound(CommandSubjectDoesNotExistException e) {
        return jsonError(e);
    }

    @ExceptionHandler(CommandSubjectAlreadyExistsException.class)
    @ResponseStatus(value = HttpStatus.CONFLICT)
    @ResponseBody
    public Map<String, Object> subjectAlreadyExists(CommandSubjectAlreadyExistsException e) {
        return jsonError(e);
    }
}
