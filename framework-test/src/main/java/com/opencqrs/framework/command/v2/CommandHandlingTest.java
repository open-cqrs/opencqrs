/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.*;
import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Annotation that can be used for Spring Boot tests that focus <strong>only</strong> on CQRS
 * {@link CommandHandlerDefinition}s in favor of initializing {@link CommandHandlingTestFixture} manually. This
 * annotation provides a {@linkplain org.springframework.context.annotation.Lazy lazy}
 * {@link CommandHandlingTestFixture} per {@link CommandHandlerDefinition} bean or {@link CommandHandling} annotated
 * method, which may be auto-wired into test methods directly. The fixture is configured
 * {@linkplain CommandHandlingTestFixture.Builder#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
 * with} all {@link StateRebuildingHandlerDefinition}s found within the context and the {@link CommandHandlerDefinition}
 * under test. A typical test annotated with {@link CommandHandlingTest} may look like this:
 *
 * <pre>
 * {@literal @CommandHandlingTest}
 * public class BookAggregateTest {
 *
 *     {@literal @Test}
 *     public void bookAdded({@literal @Autowired CommandHandlingTestFixture<BookAggregate, AddBookCommand, UUID> fixture}) {
 *          UUID bookId = UUID.randomUUID();
 *          fixture
 *              .givenNothing()
 *              .when(
 *                  new AddBookCommand(
 *                      bookId,
 *                      "Tolkien",
 *                      "LOTR",
 *                      "DE234723432"
 *                  )
 *              )
 *              .expectSuccessfulExecution()
 *              .expectSingleEvent(
 *                  new BookAddedEvent(
 *                      bookId,
 *                      "Tolkien",
 *                      "LOTR",
 *                      "DE234723432"
 *                  )
 *              );
 *     }
 * }
 * </pre>
 *
 * <p>Using this annotation will disable full {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * auto-configuration} and instead apply only configurations relevant to initialize {@link CommandHandlingTestFixture},
 * i.e. {@link CommandHandlerDefinition}s and {@link StateRebuildingHandlerDefinition}s, but not
 * {@link org.springframework.stereotype.Component} or {@link org.springframework.context.annotation.Bean}s.
 *
 * <p>In order for this annotation to be able to initialize {@link CommandHandlerDefinition}, {@link CommandHandling}
 * methods, and {@link StateRebuildingHandlerDefinition} beans, these should be defined within a
 * {@link CommandHandlerConfiguration}. Any dependent beans required for initializing them are typically provided by
 * defining them as {@link org.springframework.test.context.bean.override.mockito.MockitoBean @MockitoBean} within the
 * test annotated using {@code this}.
 *
 * @see CommandHandlingTestFixture
 * @see CommandHandlingTestAutoConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(SpringExtension.class)
@BootstrapWith(SpringBootTestContextBootstrapper.class)
@OverrideAutoConfiguration(enabled = false)
@TypeExcludeFilters(CommandHandlingTestExcludeFilter.class)
@ImportAutoConfiguration
public @interface CommandHandlingTest {}
