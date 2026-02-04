package com.opencqrs.framework.command;

import java.util.List;
import java.util.function.Consumer;

public interface ExpectDsl {

    Initial when(Object command);

    interface Common {
        All allEvents();
        Next nextEvents();
    }

    interface Initial extends Common{
        Succeeds succeeds();
        Fails fails();

        // all old (now deprecated) methods ???
        @Deprecated
        Initial expectSuccessfulExecution();
    }

    interface Succeeds {
        Succeeds withNoEvents();
        Succeeds returning(Object result);
        Succeeds returnsSatisfying(Consumer<Object> result);
        Succeeds withState(Object state);
        Succeeds withStateSatisfying(Consumer<Object> assertion);
        Common and();
    }

    interface Fails {
        Fails throwing(Class<? extends Throwable> exception);
        Fails throwsSatisfying(Consumer<Throwable> assertion);
    }

    interface All {

        All count(int count);
        All exactly(Object... events);
        All inAnyOrder(Object... events);
        All any(Object e);
        All anySatisfying(Consumer<Object> assertion);
        All none(Object e);
        All noneSatisfying(Consumer<Object> assertion);


        // TODO
        All allSatisfying(Consumer<Object> assertion);
        All satisfying(Consumer<List<Object>> assertion);

        Common and();
    }

    interface Next {
        Next skip(int num);
        Next andNoMore();
        Next exactly(Object... events);
        Next inAnyOrder(Object... events);
        Next satisfying(Consumer<Object> consumer);
        Next any(Object e);

        Common and();
    }
}
