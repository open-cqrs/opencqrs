/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.MyEvent;
import com.opencqrs.framework.State;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;

@CommandHandlerConfiguration
public class CommandHandlingConfiguration {

    @Bean
    public StateRebuildingHandlerDefinition<State, MyEvent> myStateRebuildingHandlerDefinition() {
        return new StateRebuildingHandlerDefinition<>(
                State.class, MyEvent.class, (StateRebuildingHandler.FromObject<State, MyEvent>)
                        (instance, event) -> instance);
    }

    @Bean
    public CommandHandlerDefinition<State, MyCommand1, Void> chdNoDependency() {
        return new CommandHandlerDefinition<>(
                State.class, MyCommand1.class, (CommandHandler.ForCommand<State, MyCommand1, Void>)
                        (command, commandEventPublisher) -> null);
    }

    @Bean
    public CommandHandlerDefinition<State, MyCommand2, Void> chdUnresolvableDependency(Runnable noSuchBean) {
        return new CommandHandlerDefinition<>(
                State.class, MyCommand2.class, (CommandHandler.ForCommand<State, MyCommand2, Void>)
                        (command, commandEventPublisher) -> null);
    }

    @CommandHandling
    public String handle(State instance, MyCommand3 command) {
        return "test";
    }

    @Configuration
    @Import(MyConfig.MyBeanRegistrar.class)
    public static class MyConfig {

        static class MyBeanRegistrar implements BeanRegistrar {
            @Override
            public void register(BeanRegistry registry, Environment env) {
                registry.registerBean(
                        "myProgrammaticCommandHandlerDefinition",
                        new ParameterizedTypeReference<CommandHandlerDefinition<State, MyCommand4, Void>>() {},
                        spec -> spec.supplier(supplierContext -> new CommandHandlerDefinition<>(
                                State.class, MyCommand4.class, (CommandHandler.ForCommand<State, MyCommand4, Void>)
                                        (command, commandEventPublisher) -> null)));
            }
        }
    }
}
