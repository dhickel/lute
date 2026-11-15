# Project Engineering Conventions

## Event System Interfaces

Use `EventListener` and `EventEmitter` as marker-style interfaces to declare a class's participation in the event system. Implement `EventListener` when subscribing or unsubscribing, and `EventEmitter` when emitting events. Use their provided event helpers.

Direct access to `EventSystem` could perform the same operations, but these interfaces make event participation explicit. Do not replace them with direct system access merely to simplify the call path. They also provide helpers, so they are not pure marker interfaces in the strict Java sense.
