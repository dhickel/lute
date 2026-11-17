# Concrete cached input listeners

## Topic
Concrete input listeners and deferred consumption.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/input/KeyListener.java`
- `core/src/main/java/io/mindspice/lute/engine/input/MouseListener.java`
- `core/src/main/java/io/mindspice/lute/engine/components/Controller.java`
- `.internal-dev/specifications/decisions.md`

## Key Takeaways
`KeyListener` and `MouseListener` are concrete caches, not interfaces. Each listener owns its queue; each controller owns its key listener. Preserve that independence.

`KeyActionConsumer` and `MouseEventConsumer` remain as deferred queue-drain callbacks. They are not the removed immediate input listener callbacks, and managers do not invoke them directly.

`ControllerSystem` updates movement direction directly and does not emit `Event.Player.Moved`; the event type and `EventEmitter` participation remain. Historically, `ed4f952` emitted on presses and `8275ad8` removed that emission. An initial issue #6 fix restored it based on the old test, but the user explicitly approved keeping emission removed and correcting the stale assertion instead. Historical behavior and a test expectation do not override explicit user intent.

The input regression JavaExec task otherwise defaults to the core module working directory. Tests that use repository-root assets need `rootProject.projectDir` as their working directory; `:core:inputSystemTest` is now configured accordingly.

## Project Relevance
Register and drain the concrete caches when integrating input with gameplay or other consumers.

## Open Questions
None.
