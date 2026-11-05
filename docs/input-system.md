# Input system

## Current gameplay flow

The canonical implementation is `io.mindspice.lute.engine.input`. `GameCore.create()` creates one shared `InputManager`, binds W/S/A/D to `MOVE_UP`/`MOVE_DOWN`/`MOVE_LEFT`/`MOVE_RIGHT` in its `InputMap`, starts with the `WINDOW` filter, and installs the manager in its existing LibGDX `InputMultiplexer`.

```text
LibGDX → InputMultiplexer → InputManager → InputMap / KeyManager
       → each registered Controller's KeyCacheListener (bounded FIFO)
       → ControllerSystem → Move.direction → MoveSystem → position
```

`TiledAshleyConfigurator` receives that shared manager. For each Tiled object marked `controller`, it creates a `Controller` and registers its enabled `GAME_INPUT` key cache. Each component owns its own 32-entry queue for the four movement actions; listeners do not share a queue. `ControllerSystem` consumes each queue in FIFO order: state 1 adds the action's direction, state 0 subtracts it, and other states, including repeat 2, are ignored. An entity without `Move` is ignored. `MoveSystem` normalizes direction and integrates position.

There is no tutorial enum conversion, bridge, extra held-action set, or second command queue. Gameplay currently uses the keyboard cache only. The manager is already registered with LibGDX; do not create another processor or register every entity with LibGDX.

## Screen ownership and lifecycle

| Boundary | Current behavior |
| --- | --- |
| `GameScreen.show()` | Reinstalls the shared manager in the existing chain, loads the Tiled map and registers its controllers, then selects `GAME_INPUT`. |
| `pause()` | Selects `WINDOW`, then runs `ControllerSystem.update(0f)` to consume queued transitions and synthetic releases. |
| `resume()` | Selects `GAME_INPUT`. A key held across the transition needs a physical release and fresh press. |
| `hide()` | Selects `WINDOW`, drains the controller system, unregisters current controller caches, then removes entities. |
| `dispose()` | Calls `hide()` before disposing screen systems. `GameCore` disposes cached screens before disposing the shared manager. |

The manager remains in the chain while gameplay is hidden or paused so it can observe physical key-ups. `GameCore.setInputProcessors(...)` clears the whole multiplexer before adding its arguments; include and order any other desired processors deliberately. `bindToWindow()` instead replaces the global processor and is for standalone ownership.

This lifecycle assumes the current single gameplay screen, Tiled creation path, and screen-wide removal. Callers creating controllers outside that path must register and unregister their listeners explicitly. Removing a component or entity while a key is held has no automatic listener cleanup. Keep listeners enabled across filter changes and drain them before unregistering; bounded queue overflow can still lose transitions.

`LoadingScreen` now activates the cached `GameScreen`. The separate `GameCore.removeScreen(Screen)` wrong-key bug remains open: the cache is keyed by class, but removal uses the instance. See `../.internal-dev/bugs/lute/loading-screen-cache/report.md` relative to the project root.

## Event capture and filtering

```text
key/button down → device mapping → capture accepted action → group filter → enabled listener → callback or cache
key/button up   → captured action → group filter → enabled listener → callback or cache
scroll          → MouseManager → group filter → enabled listener → callback or cache
```

States are 1 for down and 0 for up. Unmapped keys and keyboard `Input.Keys.UNKNOWN` (0) are ignored by the adapter. `ActionType` (`WINDOW`, `GUI`, `GAME_INPUT`) selects a group; a cache listener's action list selects entries within it. `setFilter` applies to both managers; `disableFilter` allows all groups, not none.

A different filter releases accepted held inputs through the outgoing filter before installing the new one. Callback listeners receive releases immediately; cache listeners queue them after pending presses. Consume inactive caches before updating dependent state. Filters do not clear queues. The same filter is a no-op. Outside callbacks a change applies synchronously; requests during dispatch are deferred, with the latest nested request winning. Direct manager filter changes reset only that manager. No guarantee is made for arbitrary nested raw-input injection or multithreading.

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

Managers broadcast to registered, enabled listeners in the selected group. `KeyCacheListener` queues selected actions for later `consume`; `KeyCallBackListener` invokes its consumer during broadcast, and its allocated `getQueue()` is not populated by that callback. `MouseCallBackListener` delivers position, buttons and scroll immediately. `MouseCacheListener` retains latest position and queues selected buttons and scroll through the same `MouseListener` registration API.

Both circular queues are bounded FIFO. A full queue overwrites the oldest entry by default; `setRejectIfFull(true)` rejects the incoming entry instead. Either policy can lose a release and leave movement held. Size and drain queues appropriately; filters and listener disabling do not clear them. Cached `consume` works even when the listener is disabled. Register or unregister outside broadcast callbacks, on the single render thread. Listener arrays grow and avoid duplicate identity registration; volatile queue size does not make the queue thread-safe.

### Cached mouse library support

Cached mouse input is available to applications but is not wired into the current gameplay controller. Register a `MouseCacheListener` through the shared manager's `regMousePosListener`, and unregister it when finished; no additional LibGDX processor is needed. Supply a group, positive capacity, and the button actions to retain.

`MouseEventConsumer.accept` receives `BUTTON` with a mapped action and state 1/0, or `SCROLL` with a null action and direction 1 up/0 down. Both share one FIFO and preserve relative order. `getPos()` returns the mutable latest screen-coordinate `Vector2` (origin top-left); copy it for a snapshot and unproject for world coordinates. Movement updates position without occupying queue space. Each queued event snapshots scalar x/y coordinates, so use those coordinates for a click rather than the later cursor position. Pointer 0 down/up/cancel updates position before its button event; an unmatched up or cancel emits no button event. Pointer 0 dragging and mouse movement also update position. This is primary mouse emulation, not multitouch.

Negative LibGDX scroll Y maps to up 1, positive Y to down 0; magnitude and horizontal scrolling are omitted. The queue exposes `isEmpty`, `isFull`, `setRejectIfFull`, `add`, `poll`, `consume`, and `peek`; `hasInputs()` reports pending entries. There is no movement history, timestamp, raw scroll magnitude, or thread-safety guarantee.

## Adapter limits and cleanup

- Adapter callbacks return `false` so later multiplexer processors can run; an earlier processor returning `true` prevents the manager from seeing an event.
- The adapter emits states 0 and 1, not repeat 2. LWJGL3 translates native repeats to `keyTyped`, not a portable per-key repeat notification. Direct manager broadcasts can accept state 2.
- There is no text input, modifier/scancode data, native window lifecycle interception, or game-controller integration. `WINDOW` enum actions can be mapped but do not intercept close or resize.
- `enableRawInput(true)` throws `UnsupportedOperationException`; `false` is a no-op. Cursor capture is not raw mouse motion.
- `cleanup()`/`dispose()` detach the manager only if it is the direct processor or a direct child of the currently active top-level multiplexer. The caller must detach it from nested or formerly active multiplexers.

## Validation

Repeatable regression and build commands:

```sh
./gradlew :core:inputSystemTest
./gradlew clean build
```

The static-main input regression harness is wired into `check`; the default core test task disables `failOnNoDiscoveredTests` for Gradle 9. Three existing test files were mechanically moved to the canonical package and the obsolete bridge-only case was removed with the deleted tutorial path; no new tests were added. Focused core and LWJGL3 compilation passed, and `./gradlew clean build --console=plain` passed with 1,320 existing checks and zero failed groups (previously 1,369 checks).

Manual JShell validation wired a real `Controller` listener directly to the canonical manager, `ControllerSystem`, and unchanged `MoveSystem`. A queued W press moved only after update; a GUI filter change queued a synthetic release that stopped movement, and the late physical up queued nothing. Fresh W+D produced direction (1, 1) and normalized position (0.70710677, 1.7071068); repeat state 2 added no direction, and releases cleared it. This was interactive validation, not a new automated test suite.

A desktop run reached asset loading, Tiled controller creation, map layers, and 56–62 FPS without input exceptions before intentional timeout after 15 seconds (exit 124). Existing libdecor GTK initialization warnings and the intentional `old_tree1` atlas fallback error logs occurred. No physical-key device test or instrumented active/cached screen identity or disposal test was performed.
