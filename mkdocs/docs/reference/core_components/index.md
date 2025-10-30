---
title: Core Components
---

{{ custom.framework_name }} offers the following built-in core components for building CQRS applications:

* [ESDB Client](esdb_client/index.md) provides an SDK for direct access to the {{ esdb_ref() }}
* [Event Repository](event_repository/index.md) supports the mapping of Java classes to ESDB events and vice versa
* [Command Router](command_router/index.md) provides support for command execution, including [reconstruction of write models](../../concepts/event_sourcing/index.md#reconstructing-the-write-model) and publication of new events
* [Event Handling Processor](event_handling_processor/index.md) supports asynchronous event processing for [read model projection](../../concepts/event_sourcing/index.md#projecting-a-read-model)
