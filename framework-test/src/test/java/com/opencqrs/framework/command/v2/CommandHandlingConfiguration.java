/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.MyEvent;
import com.opencqrs.framework.State;
import com.opencqrs.framework.command.*;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

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
    public static class MyConfig {

        @Bean
        public static BeanDefinitionRegistryPostProcessor programmaticCommandHandlerDefinitionRegistration() {
            return registry -> {
                RootBeanDefinition chd = new RootBeanDefinition();
                chd.setBeanClass(CommandHandlerDefinition.class);
                chd.setTargetType(ResolvableType.forClassWithGenerics(
                        CommandHandlerDefinition.class, State.class, MyCommand4.class, Void.class));

                ConstructorArgumentValues values = new ConstructorArgumentValues();
                values.addGenericArgumentValue(State.class);
                values.addGenericArgumentValue(MyCommand4.class);
                values.addGenericArgumentValue(
                        (CommandHandler.ForCommand<State, MyCommand4, Void>) (command, commandEventPublisher) -> null);

                chd.setConstructorArgumentValues(values);
                registry.registerBeanDefinition("myProgrammaticCommandHandlerDefinition", chd);
            };
        }
    }
}
