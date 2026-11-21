/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Annotation that can be used for Spring Boot tests that focus <strong>only</strong> on CQRS
 * {@link CommandHandlerDefinition}s in favor of initializing {@link CommandHandlingTestFixture} manually. This
 * annotation provides a {@linkplain org.springframework.context.annotation.Lazy lazy}
 * {@link CommandHandlingTestFixture} per {@link CommandHandlerDefinition} bean or {@link CommandHandling} annotated
 * method, which may be auto-wired into test methods directly. The fixture is configured
 * {@linkplain CommandHandlingTestFixture.Builder#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
 * with} all {@link StateRebuildingHandlerDefinition}s and
 * {@link com.opencqrs.framework.command.interceptor.CommandInterceptor}s found within the context and the
 * {@link CommandHandlerDefinition} under test. A typical test annotated with {@link CommandHandlingTest} may look like
 * this:
 *
 * <pre>
 * {@literal @CommandHandlingTest}
 * public class BookAggregateTest {
 *
 *     {@literal @Test}
 *     public void bookAdded({@literal @Autowired CommandHandlingTestFixture<BookAggregate, AddBookCommand, UUID> fixture}) {
 *          UUID bookId = UUID.randomUUID();
 *          fixture
 *              .given()
 *              .nothing()
 *              .when(
 *                  new AddBookCommand(
 *                      bookId,
 *                      "Tolkien",
 *                      "LOTR",
 *                      "DE234723432"
 *                  )
 *              )
 *              .succeeds()
 *              .allEvents()
 *              .exactly(
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
 * i.e. {@link CommandHandlerDefinition}s, {@link com.opencqrs.framework.command.interceptor.CommandInterceptor}s, and
 * {@link StateRebuildingHandlerDefinition}s, but not {@link org.springframework.stereotype.Component} or
 * {@link org.springframework.context.annotation.Bean}s.
 *
 * <p>In order for this annotation to be able to initialize {@link CommandHandlerDefinition}, {@link CommandHandling}
 * methods, {@link com.opencqrs.framework.command.interceptor.CommandInterceptor}s, and
 * {@link StateRebuildingHandlerDefinition} beans, these should be defined within a {@link CommandHandlerConfiguration}.
 * Any dependent beans required for initializing them are typically provided by defining them as
 * {@link org.springframework.test.context.bean.override.mockito.MockitoBean @MockitoBean} within the test annotated
 * using {@code this}.
 *
 * <p>Since this annotation deliberately does <strong>not</strong> component-scan the application (see above), a
 * {@link com.opencqrs.framework.command.interceptor.CommandInterceptor} declared as a plain
 * {@link org.springframework.stereotype.Component} is <em>not</em> picked up automatically. This keeps every slice lean
 * and free from the transitive dependencies of interceptors a test does not exercise. There are three ways to supply
 * interceptors, each explicit about what a given test pays for:
 *
 * <ol>
 *   <li><strong>As a {@link org.springframework.context.annotation.Bean} within a
 *       {@link CommandHandlerConfiguration}</strong> - suitable for interceptors that conceptually belong to the
 *       command-handling configuration under test; they are loaded together with the handlers.
 *   <li><strong>By {@link org.springframework.context.annotation.Import importing} them into the test</strong> - the
 *       idiomatic way to reuse a production interceptor declared as a plain {@code @Component} without annotating it as
 *       a configuration. {@code @Import} registers the bean directly, bypassing the slice's component filter, so only
 *       the importing test pays for the interceptor and its (typically
 *       {@link org.springframework.test.context.bean.override.mockito.MockitoBean @MockitoBean}-provided) dependencies:
 *       <pre>
 *       {@literal @CommandHandlingTest}
 *       {@literal @Import(TracingCommandInterceptor.class)}   // a plain @Component in production
 *       public class BookHandlingTest {
 *
 *           {@literal @MockitoBean} Tracer tracer;             // only this test satisfies its dependencies
 *
 *           {@literal @Test}
 *           public void bookAdded({@literal @Autowired CommandHandlingTestFixture<AddBookCommand> fixture}) { ... }
 *       }
 *       </pre>
 *   <li><strong>Programmatically per fixture</strong> via
 *       {@link CommandHandlingTestFixture#withAdditionalInterceptors(com.opencqrs.framework.command.interceptor.CommandInterceptor[])
 *       withAdditionalInterceptors(...)} - layers ad-hoc interceptors (e.g. recording probes) on top of whatever base
 *       set the slice contributed, without registering them as beans.
 * </ol>
 *
 * <p>Interceptors contributed as beans through the first two mechanisms form the <em>base set</em> applied to every
 * auto-wired {@link CommandHandlingTestFixture}; {@link #withInterceptors()} toggles whether that base set is applied
 * at all, while {@code withAdditionalInterceptors(...)} always layers on top.
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
public @interface CommandHandlingTest {

    /**
     * Whether the {@link com.opencqrs.framework.command.interceptor.CommandInterceptor} beans found within the slice
     * are applied as the base interceptor set of every auto-wired {@link CommandHandlingTestFixture}. Set to
     * {@code false} for tests focused solely on command handling; per-method
     * {@link CommandHandlingTestFixture#withAdditionalInterceptors(com.opencqrs.framework.command.interceptor.CommandInterceptor[])
     * withAdditionalInterceptors(...)} still layers interceptors on top.
     *
     * @return {@code true} (default) to apply the slice's interceptors, {@code false} to start from an empty base set
     */
    boolean withInterceptors() default true;
}
