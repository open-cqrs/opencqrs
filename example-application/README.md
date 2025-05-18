# Library Sample Application

The library sample application is a very simple application for managing a library.
It serves as a demonstration of the basic features of OpenCQRS.

## Starting the EventSourcingDB

To get the application running, first start the EventsourcingDB.
You can use the docker-compose file in this directory to run the EventsourcingDB.
Execute `docker-compose up event-store` in your terminal to start the event store.
You can then start the application via your preferred IDE, for example IntelliJ IDEA.
You can also start the application via Gradle from the OpenCQRS root directory by executing `./gradlew clean :example-application:bootRun`.

## Interacting with the Library Application

You can access an OpenAPI UI to easily interact with the Library Application's API.
After starting the application, open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) in your browser.

Use the APIs exposed via `reader-controller` and `book-controller` to register readers in your library and purchase books.
When at least one reader is registered and one book is purchased, the reader can borrow and return the book.

All APIs directly expose the command classes, including internal fields from the command interface.
For production use cases, this is discouraged, and this shortcut only serves quick setup purposes.
Therefore, please remove the fields `subjectCondition` and `subject` from the request bodies in the OpenAPI UI.

## Running Multiple Instances

The sample application can be run in a multi-JVM setup with leader election for the event-handling groups.
To test the multi-JVM setup, it is best to run the application as a container via docker-compose.

First, build the docker image by executing `./gradlew clean :example-application:jibDockerBuild` from the OpenCQRS root directory.
Then you can start the application, including an event store and a database server, via docker-compose.
Either run `docker-compose up` in the `example-application` directory or run `env -C example-application docker compose up` in the OpenCQRS root directory.

## Understanding the Code

The `domain` package contains most of the OpenCQRS-specific code, especially all `CommandHandling` and `StateRebuilding` methods.
The `CommandHandling` methods perform updates in the system by publishing events via `CommandEventPublisher`.
The `StateRebuilding` methods restore the write model from the event store by applying all events for a subject on a write model instance.

In the `configuration` package, you find some Spring Beans configuring the framework.

The `projection` package contains `EventHandling` methods to build some read models and perform other operations upon events.

To get a feeling for the powerful testing capabilities of OpenCQRS, refer to the tests.

The hierarchical subjects of EventSourcingDB are also supported.
Have a closer look at the `Book` and `Page` classes and how they work together.

## More Information

Our documentation is nowhere near complete and not really reviewed yet.
The tutorial, however, might serve as a good starting point to explore the functionality of OpenCQRS.
You find it in the `mkdocs/tutorial` directory.

Have fun exploring OpenCQRS!
All feedback is welcome.
Feel free to contact us either via `opencqrs (at) digitalfrontiers.de` or via GitHub.
