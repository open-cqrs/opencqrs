package com.opencqrs.framework.command;

import java.util.function.Consumer;

public class Main {

    public static void main(String[] args){
        ExpectDsl dsl = null;

        dsl
                .when(new Object())
                .succeeds()
                .returning(42L)
                .withState(new Object())
                .withStateSatisfying(System.out::println)

                .and()

                .allEvents()
                .count(42)
                .exactly(new Object())
                .inAnyOrder(new Object())
                .any(new Object())
                .anySatisfying(System.out::println)
                .anyType(String.class)
                .none()
                .notContaining(System.out::println)
                .notContainingType(String.class)
                .and()

                .nextEvents()
                .skip(2)
                .andNoMore()
                .exactly(new Object())
                .matching()
                .inAnyOrder()
                .matchingInAnyOrder(new Object())
                .comparing(new Object())
                .comparing(System.out::println)
                .comparingType(String.class)
                .satisfying(System.out::println)
                .any(new Object());

        dsl
                .when(new Object())
                .fails()
                .throwing()
                .throwsSatisfying()
                // command subject violations here

    }
}
