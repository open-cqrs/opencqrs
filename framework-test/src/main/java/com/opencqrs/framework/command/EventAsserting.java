package com.opencqrs.framework.command;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public interface EventAsserting {

    EventAsserting payloadType(Class<?> type);

    <E> EventAsserting payload(E expected);

    <E, R> EventAsserting payloadExtracting(Function<E, R> extractor, R expected);

    <E> EventAsserting payloadSatisfying(Consumer<E> assertion);

    EventAsserting metaData(Map<String, ?> expected);

    EventAsserting metaDataSatisfying(Consumer<Map<String, ?>> assertion);



    EventAsserting noMetaData();

    EventAsserting subject(String expected);

    EventAsserting subjectSatisfying(Consumer<String> assertion);

    EventAsserting commandSubject();
}