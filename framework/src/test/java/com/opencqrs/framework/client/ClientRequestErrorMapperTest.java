/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.opencqrs.esdb.client.ClientException;
import com.opencqrs.framework.CqrsFrameworkException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ClientRequestErrorMapperTest {

    private final ClientRequestErrorMapper subject = new ClientRequestErrorMapper();

    @Test
    public void runtimeExceptionPropagated() {
        var throwable = mock(RuntimeException.class);

        assertThatThrownBy(() -> subject.handleMappingExceptionsIfNecessary(() -> {
                    throw throwable;
                }))
                .isSameAs(throwable);
    }

    @Test
    public void errorPropagated() {
        var throwable = mock(Error.class);

        assertThatThrownBy(() -> subject.handleMappingExceptionsIfNecessary(() -> {
                    throw throwable;
                }))
                .isSameAs(throwable);
    }

    @Test
    public void unknownClientExceptionPropagated() {
        var throwable = mock(ClientException.class);

        assertThatThrownBy(() -> subject.handleMappingExceptionsIfNecessary(() -> {
                    throw throwable;
                }))
                .isSameAs(throwable);
    }

    @ParameterizedTest
    @MethodSource({
        "specialErrorCases",
        "unhandledStatusCodes",
        "persistentClientErrorStatusCodes",
        "serverErrorStatusCodes",
    })
    public void exceptionCaughtAndMapped(RuntimeException source, Class<? extends CqrsFrameworkException> destination) {
        assertThatThrownBy(() -> subject.handleMappingExceptionsIfNecessary(() -> {
                    throw source;
                }))
                .isInstanceOf(destination)
                .hasCause(source);
    }

    public static Stream<Arguments> specialErrorCases() {
        return Stream.of(
                Arguments.of(
                        new ClientException.InvalidUsageException("test"),
                        CqrsFrameworkException.NonTransientException.class),
                Arguments.of(
                        new ClientException.TransportException("test"),
                        CqrsFrameworkException.TransientException.class),
                Arguments.of(
                        new ClientException.MarshallingException("test"),
                        CqrsFrameworkException.NonTransientException.class),
                Arguments.of(
                        new ClientException.HttpException.HttpClientException("test", 408),
                        CqrsFrameworkException.TransientException.class),
                Arguments.of(
                        new ClientException.HttpException.HttpClientException("test", 409), ConcurrencyException.class),
                Arguments.of(new ClientException.InterruptedException("test"), ClientInterruptedException.class));
    }

    public static Stream<Arguments> unhandledStatusCodes() {
        return IntStream.concat(IntStream.rangeClosed(100, 199), IntStream.rangeClosed(201, 399))
                .mapToObj(statusCode -> Arguments.of(
                        new ClientException.HttpException("test", statusCode),
                        CqrsFrameworkException.NonTransientException.class));
    }

    public static Stream<Arguments> persistentClientErrorStatusCodes() {
        return IntStream.concat(IntStream.rangeClosed(400, 407), IntStream.rangeClosed(410, 499))
                .mapToObj(statusCode -> Arguments.of(
                        new ClientException.HttpException.HttpClientException("test", statusCode),
                        CqrsFrameworkException.NonTransientException.class));
    }

    public static Stream<Arguments> serverErrorStatusCodes() {
        return IntStream.rangeClosed(500, 599)
                .mapToObj(statusCode -> Arguments.of(
                        new ClientException.HttpException.HttpServerException("test", statusCode),
                        CqrsFrameworkException.TransientException.class));
    }
}
