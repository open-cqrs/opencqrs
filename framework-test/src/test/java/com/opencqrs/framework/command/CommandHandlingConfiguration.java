/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.MyEvent;
import com.opencqrs.framework.State;
import java.util.UUID;
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
    public CommandHandlerDefinition<State, MyCommand, Void> chdNoDependency() {
        return new CommandHandlerDefinition<>(
                State.class, MyCommand.class, (CommandHandler.ForCommand<State, MyCommand, Void>)
                        (command, commandEventPublisher) -> null);
    }

    @Bean
    public CommandHandlerDefinition<State, MyCommand, UUID> chdUnresolvableDependency(MyCommand noSuchBean) {
        return new CommandHandlerDefinition<>(
                State.class, MyCommand.class, (CommandHandler.ForCommand<State, MyCommand, UUID>)
                        (command, commandEventPublisher) -> null);
    }

    @CommandHandling
    public String handle(State instance, MyCommand command) {
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
                        CommandHandlerDefinition.class, State.class, MyCommand.class, Boolean.class));

                ConstructorArgumentValues values = new ConstructorArgumentValues();
                values.addGenericArgumentValue(State.class);
                values.addGenericArgumentValue(MyCommand.class);
                values.addGenericArgumentValue((CommandHandler.ForCommand<State, MyCommand, Boolean>)
                        (command, commandEventPublisher) -> null);

                chd.setConstructorArgumentValues(values);
                registry.registerBeanDefinition("myProgrammaticCommandHandlerDefinition", chd);
            };
        }
    }
}
