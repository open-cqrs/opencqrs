# Concepts

This chapter introduces the core concepts that form the foundation of the design and architecture of CQRS/ES applications. 
Understanding these principles is essential for working effectively with {{ custom.framework_name }} and for building applications that are robust, 
scalable, and easy to evolve over time.

- [Events](events/index.md) represent facts that have happened in the domain. They capture state changes in a way that is immutable, explicit, and traceable.
- [Event Sourcing](event_sourcing/index.md) is a persistence pattern where the system’s state is derived by replaying the sequence of events that have occurred, rather than storing only the current state. This enables powerful features such as auditing, debugging, and the ability to reconstruct past states.
- [Event Upcasting](upcasting/index.md) addresses the challenge of evolving event schemas over time. As the domain changes, existing events may need to be transformed into newer representations to remain compatible with the current application model.

Together, these concepts provide the foundation for building systems that are event-driven, resilient to change, and transparent in their behavior.
