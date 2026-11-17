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

`Event.Player.Moved` is a controller input notification, not evidence of physical displacement. The controller emits it once per consumed movement press on an entity with `Move`, not on releases, repeats, invalid states, attack requests, or movement input without `Move`. Commit `ed4f952` established the press-only behavior; `8275ad8` removed the emission. The issue 6 fix restores it through `EventEmitter`. Current gameplay has no subscriber, and the fix does not change audio.

## Project Relevance
Register and drain the concrete caches when integrating input with gameplay or other consumers.

## Open Questions
None.
