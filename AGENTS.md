# Project Engineering Conventions

## Project Scope and Readability

This is a hobbyist project. Keep the code simple, grokkable, and easy to follow. Prefer direct, readable flow over elaborate abstractions, overly verbose implementations, or defensive handling of every conceivable edge case. Validate meaningful boundaries and handle realistic failures without repeating checks throughout trusted internal code.

Format code for readability, with consistent indentation, sensible line breaks, and whitespace that separates logical steps. Favor clear names and straightforward expressions over clever or compressed code.

## Event System Interfaces

Use `EventListener` and `EventEmitter` as marker-style interfaces to declare a class's participation in the event system. Implement `EventListener` when subscribing or unsubscribing, and `EventEmitter` when emitting events. Use their provided event helpers.

Direct access to `EventSystem` could perform the same operations, but these interfaces make event participation explicit. Do not replace them with direct system access merely to simplify the call path. They also provide helpers, so they are not pure marker interfaces in the strict Java sense.
