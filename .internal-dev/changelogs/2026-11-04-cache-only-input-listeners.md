# Cache-only input listeners

## Date
2026-11-04

## Git Commit

ad34b0978cbe296b7729241ade23bf4a5d01aba5

## Change Summary
Replaced immediate-delivery input listener implementations with concrete cache-only `KeyListener` and `MouseListener` types. Updated the controller, documentation, and regression tests to use the concrete caches.

## Files
- `core/src/main/java/io/mindspice/lute/engine/input/`: listener implementations and removed obsolete callback and cache class names, including callback-only `BiDoubleConsumer`.
- `core/src/main/java/io/mindspice/lute/engine/components/Controller.java`: concrete key listener construction.
- Input regression tests: adapted to concrete caches; `MouseCacheListenerTest` renamed to `MouseListenerTest`.
- `docs/input-system.md`: current listener and release behavior.
- `.internal-dev/specifications/decisions.md` and `index.md`: cache-only decision and index.
- `.internal-dev/knowledge/input-listeners.md`: concrete-cache and queue-consumer distinction.

## Behavioral Impact
Input consumers drain queued key, button, and scroll events explicitly. Mouse position updates the latest cached position. Balancing releases are queued for enabled listeners selecting the captured action and require draining. LibGDX event callbacks and queue-drain consumers remain.

The removed names and listener implementations have no compatibility aliases; this is an intentional API break.

## Specification Impact
Recorded the cache-only listener delivery contract and its tradeoffs in `specifications/decisions.md`.

## Risks
Bounded queue overflow can lose transitions. Existing callers using removed listener types must migrate.

## Follow-up Items
None for this change.

## Validation
- `./gradlew :core:compileJava :lwjgl3:compileJava --console=plain`: PASS.
- `./gradlew clean build --console=plain`: PASS; 1,234 checks, zero failed groups. Existing tests now use real cache listeners and explicit drains rather than immediate callback listeners; registration identity/growth/removal, disabled listeners, whitelists, mouse snapshots/interleaving, queues, binding isolation, held releases and rebinding remain covered.
- Root compared both new concrete classes against the previous cached implementations after normalizing names and removing interface override annotations: both behavior bodies are unchanged.
- Manual ECS exercise: W press remains queued with zero movement before update; consuming through ControllerSystem moves by one unit and drains; release remains queued until consumed and then clears direction. Mouse latest position (30,40) does not alter a queued click captured at (10,20).
- SHA-256 checks confirm the user's pre-existing GameCore.java, InputMap.java and untracked engine/util/Pair.java are unchanged. These files are excluded from implementation commits.
- No changes to managers, queues, filters, state routing or multiplexer implementation. Global policy copies compare equal.
- Implementation branch: `cleanup/cache-only-input-listeners`; integration target: the initially active `master` branch.
