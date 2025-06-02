package com.opencqrs.framework.command;

public class Main {

    public static void main(String[] args){
        ExpectDsl dsl = null;

        dsl
                .when(new Object())

                .succeeds()
                .returning(42L)
                .withState(new Object())
                .and()


                .allEvents()
                .count(42)
                .any(new Object())
                .and()

                .nextEvents()
                .skip(2)
                .exactly(new Object());
    }
}
