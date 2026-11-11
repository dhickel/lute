# Ashley entity visibility during event dispatch

## Topic

Entity creation inside an event listener does not immediately update Ashley families during an engine update.

## Source References

- Ashley 1.7.4 Engine.addEntity() and Engine.update(), verified in the cached sources JAR.
- core/src/main/java/io/mindspice/lute/engine/systems/EventSystem.java
- core/src/main/java/io/mindspice/lute/engine/systems/CameraSystem.java

## Key Takeaways

Engine.addEntity() queues additions while updating. Engine.update() commits pending entity/component operations after each system update returns. A second event pass inside EventSystem.update() does not cross this boundary. Emitting a completion event after creating entities therefore does not guarantee family visibility to other listeners in that same dispatch invocation.

## Project Relevance

Camera map notifications should update bounds without querying a newly populated family. Normal camera iteration later in the engine tick sees committed entities. Moving an event-listening system does not relocate its handler execution out of EventSystem.update().

## Open Questions

None for the verified startup failure; map replacement lifecycle is separate.
