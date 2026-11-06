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

## Project Relevance
Register and drain the concrete caches when integrating input with gameplay or other consumers.

## Open Questions
None.
