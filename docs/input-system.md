# Input system

## Current gameplay flow

The canonical implementation is `io.mindspice.lute.engine.input`. `GameCore.create()` creates one shared `InputManager`, binds W/S/A/D to `MOVE_UP`/`MOVE_DOWN`/`MOVE_LEFT`/`MOVE_RIGHT` in its `InputMap` and installs the manager in its existing LibGDX `InputMultiplexer`.

```text
LibGDX → InputMultiplexer → InputManager → InputMap / KeyManager
       → each registered Controller's KeyCacheListener (bounded FIFO)
       → ControllerSystem → Move.direction → MoveSystem → position
```

`TiledAshleyConfigurator` receives that shared manager. For each Tiled object marked `controller`, it creates a `Controller` and registers its enabled key cache. Each component owns its own 32-entry queue for the four movement actions; listeners do not share a queue. `ControllerSystem` consumes each queue in FIFO order: state 1 adds the action's direction, state 0 subtracts it, and other states, including repeat 2, are ignored. An entity without `Move` is ignored. `MoveSystem` normalizes direction and integrates position.

There is no tutorial enum conversion, bridge, extra held-action set, or second command queue. Gameplay currently uses the keyboard cache only. The manager is already registered with LibGDX; do not create another processor or register every entity with LibGDX.

## Screen ownership and lifecycle

| Boundary | Current behavior |
| --- | --- |
| `GameScreen.show()` | Releases held inputs before loading the map and registering incoming controllers, and reinstalls the shared manager in the existing chain. |
| `pause()` | Releases held inputs, then runs `ControllerSystem.update(0f)` to consume queued transitions and synthetic releases. |
| `resume()` | Releases and drains again, including presses queued while paused. A released held key needs a physical release and fresh press. |
| `hide()` | Releases held inputs, drains the controller system, unregisters current controller caches, then removes entities. |
| `dispose()` | Calls `hide()` before disposing screen systems. `GameCore` disposes cached screens before disposing the shared manager. |

The manager remains in the chain while gameplay is hidden or paused so it can observe physical key-ups. Releasing holds does not disable or gate future input; future state routing can use the existing multiplexer but is not implemented here. `GameCore.setInputProcessors(...)` clears the whole multiplexer before adding its arguments; include and order any other desired processors deliberately. `bindToWindow()` instead replaces the global processor and is for standalone ownership.

This lifecycle assumes the current single gameplay screen, Tiled creation path, and screen-wide removal. Callers creating controllers outside that path must register and unregister their listeners explicitly. Removing a component or entity while a key is held has no automatic listener cleanup. Keep listeners enabled during release and drain them before unregistering; bounded queue overflow can still lose transitions.

`LoadingScreen` now activates the cached `GameScreen`. The separate `GameCore.removeScreen(Screen)` wrong-key bug remains open: the cache is keyed by class, but removal uses the instance. See `../.internal-dev/bugs/lute/loading-screen-cache/report.md` relative to the project root.

## Event capture and release

```text
key/button down → device mapping → capture action → enabled listener → callback or cache
key/button up   → captured action → enabled listener → callback or cache
scroll          → MouseManager → enabled listener → callback or cache
```

States are 1 for down and 0 for up. Unmapped keys and keyboard `Input.Keys.UNKNOWN` (0) are ignored by the adapter. There are no action groups or manager filter APIs; key listeners and cached mouse listeners retain their individual action lists.

`InputManager.releaseHeldInputs()` synchronously releases captured keys and buttons and is idempotent. Callback listeners receive releases immediately; cache listeners append them after pending presses, so callers must drain the caches. Call it on the main thread, outside callbacks; there is no callback-reentrant reset guarantee. It does not clear queues or block future input, except that an already-held physical slot cannot produce a fresh press until its actual up.

Each physical slot tracks down state and its accepted action. Duplicate downs and unmatched or late ups are suppressed. Direct repeat state 2 is delivered only while the logical press remains held. A synthetic release leaves the physical slot down until its real up, so repeats cannot reactivate it. A release uses the original action even after rebinding. Separate bindings to the same action produce separate down/up pairs, not per-action aggregation; consumers must account for that binding policy.

## Bindings

`InputMap` maintains independent fixed arrays for keyboard (`Input.Keys.MAX_KEYCODE + 1`) and buttons (`Input.Buttons.FORWARD + 1`, codes 0–4). Overlapping codes do not collide. Keyboard `UNKNOWN` can be mapped but its adapter events are ignored; mouse `LEFT` (0) works independently.

| Operation | Keyboard | Mouse button |
| --- | --- | --- |
| Bind or clear with `null` | `setKey(code, action)` | `setButton(code, action)` |
| Look up | `getKey(code)` | `getButton(code)` |
| Test | `containsKey(code)` | `containsButton(code)` |
| Clear | `removeKey(code)` | `removeButton(code)` |

Setters throw `IllegalArgumentException` for invalid codes. Invalid lookups return `null`, containment checks return `false`, and removals do nothing. Removal clears only the requested device mapping. There are no generic `set`, `get`, `remove`, or `contains` aliases. LibGDX uses touch-named callbacks for desktop mouse buttons.

## Listeners and queues

Managers broadcast mapped actions to all registered, enabled listeners. `KeyCacheListener(int, List<InputAction>)` queues selected actions for later `consume`; `KeyCallBackListener(List<InputAction>, int, consumer)` invokes its consumer for selected actions during broadcast, and its allocated `getQueue()` is not populated by that callback. `MouseCallBackListener()` delivers all mapped buttons, position and scroll immediately. `MouseCacheListener(int, List<InputAction>)` retains latest position and queues selected buttons and scroll through the same `MouseListener` registration API.

Both circular queues are bounded FIFO. A full queue overwrites the oldest entry by default; `setRejectIfFull(true)` rejects the incoming entry instead. Either policy can lose a release and leave movement held. Size and drain queues appropriately; release and listener disabling do not clear them. Cached `consume` works even when the listener is disabled. Register or unregister outside broadcast callbacks, on the single render thread. Listener arrays grow and avoid duplicate identity registration; volatile queue size does not make the queue thread-safe.

### Cached mouse library support

Cached mouse input is available to applications but is not wired into the current gameplay controller. Register a `MouseCacheListener` through the shared manager's `regMousePosListener`, and unregister it when finished; no additional LibGDX processor is needed. Supply a positive capacity and the button actions to retain.

`MouseEventConsumer.accept` receives `BUTTON` with a mapped action and state 1/0, or `SCROLL` with a null action and direction 1 up/0 down. Both share one FIFO and preserve relative order. `getPos()` returns the mutable latest screen-coordinate `Vector2` (origin top-left); copy it for a snapshot and unproject for world coordinates. Movement updates position without occupying queue space. Each queued event snapshots scalar x/y coordinates, so use those coordinates for a click rather than the later cursor position. Pointer 0 down/up/cancel updates position before its button event; an unmatched up or cancel emits no button event. Pointer 0 dragging and mouse movement also update position. This is primary mouse emulation, not multitouch.

Negative LibGDX scroll Y maps to up 1, positive Y to down 0; magnitude and horizontal scrolling are omitted. The queue exposes `isEmpty`, `isFull`, `setRejectIfFull`, `add`, `poll`, `consume`, and `peek`; `hasInputs()` reports pending entries. There is no movement history, timestamp, raw scroll magnitude, or thread-safety guarantee.

## Adapter limits and cleanup

- Adapter callbacks return `false` so later multiplexer processors can run; an earlier processor returning `true` prevents the manager from seeing an event.
- The adapter emits states 0 and 1, not repeat 2. LWJGL3 translates native repeats to `keyTyped`, not a portable per-key repeat notification. Direct manager broadcasts can accept state 2.
- There is no text input, modifier/scancode data, native window lifecycle interception, or game-controller integration. Window-related enum actions can be mapped but do not intercept close or resize.
- `enableRawInput(true)` throws `UnsupportedOperationException`; `false` is a no-op. Cursor capture is not raw mouse motion.
- `cleanup()`/`dispose()` detach the manager only if it is the direct processor or a direct child of the currently active top-level multiplexer. The caller must detach it from nested or formerly active multiplexers.

## Validation

Repeatable regression and build commands:

```sh
./gradlew :core:inputSystemTest
./gradlew clean build
```

The static-main input regression harness is wired into `check`; the default core test task disables `failOnNoDiscoveredTests` for Gradle 9. Focused core and LWJGL3 compilation passed.

A desktop run reached asset loading, Tiled controller creation, map layers, and 56–62 FPS without input exceptions before intentional timeout after 15 seconds (exit 124). Existing libdecor GTK initialization warnings and the intentional `old_tree1` atlas fallback error logs occurred. No physical-key device test or instrumented active/cached screen identity or disposal test was performed.
