# Input system

## Current gameplay flow

The canonical implementation is `io.mindspice.lute.engine.input`. After loading game settings, `GameCore.create()` loads `input.setting.json` through `InputSettings`, creates one shared `InputManager` from the resulting `InputMap`, and installs it in the LibGDX `InputMultiplexer`. Invalid or missing input settings fail startup rather than falling back to hard-coded bindings.

```text
LibGDX → InputMultiplexer → InputManager → InputMap / KeyManager
       → each registered Controller's KeyListener (bounded FIFO)
       → ControllerSystem → Move.direction → PhysicMoveSystem → body velocity
                          → Combat attack requests
```

`TiledSystem` spawns the player through `EntityFactory`, which creates a `Controller` registered with the shared manager. Each controller owns a 32-entry queue for movement and attack actions; listeners do not share a queue. `ControllerSystem` consumes each queue in FIFO order: movement state 1 adds the action's direction, state 0 subtracts it, and other states, including repeat 2, are ignored. Movement input is ignored for an entity without `Move`; attack presses request attacks when `Combat` is present. `PhysicMoveSystem` normalizes movement direction and sets body velocity, or stops it when rooted, idle, or attacking.

`ControllerSystem` updates movement direction directly and does not emit `Event.Player.Moved` for movement input. The event type and event-system interfaces remain available.

Keyboard, gamepad, and mapped mouse-button actions share the existing gameplay listener queues. There is no tutorial enum conversion, bridge, extra held-action set, or second command queue. The manager is already registered with LibGDX; do not create another processor or register every entity with LibGDX.

## Screen ownership and lifecycle

| Boundary | Current behavior |
| --- | --- |
| `GameScreen.show()` | Releases held inputs before loading the map and registering incoming controllers, and reinstalls the shared manager in the existing chain. |
| `pause()` | Releases held inputs, then runs `ControllerSystem.update(0f)` to consume queued transitions and synthetic releases. |
| `resume()` | Releases and drains again, including presses queued while paused. A released held key needs a physical release and fresh press. |
| `hide()` | Releases held inputs, drains the controller system, unregisters current controller caches, then removes entities. |
| `dispose()` | Calls `hide()` before disposing screen systems. `GameCore` disposes cached screens before disposing the shared manager. |

`GameScreen` also owns a separate cached `TOGGLE_NIGHT` listener, independent of the player. It acts on fresh presses before the ECS update, not releases or repeats, and discards stale transitions across inactive contexts. A key held through pause or hide must physically release before a new press can toggle. Day is the default for a new screen; mode and pulse phase persist across travel and hide/show, while pause and hide freeze phase. This toggle does not change world time.

`GameScreen` polls the gamepad before the ECS update only while visible and unpaused. The manager remains in the chain while gameplay is hidden or paused so it can observe physical key-ups. Releasing holds does not disable or gate future input; future state routing can use the existing multiplexer but is not implemented here. `GameCore.setInputProcessors(...)` clears the whole multiplexer before adding its arguments; include and order any other desired processors deliberately. `bindToWindow()` instead replaces the global processor and is for standalone ownership.

This lifecycle assumes the current single gameplay screen, Tiled creation path, and screen-wide removal. Callers creating controllers outside that path must register and unregister their listeners explicitly. Removing a component or entity while a key is held has no automatic listener cleanup. Keep listeners enabled during release and drain them before unregistering; bounded queue overflow can still lose transitions.

`ScreenManager` caches screens by string key. The loading callback switches to `GameScreen` and then unloads `InitialLoading` by key.

## Event capture and release

```text
key/button down → device mapping → capture action → enabled action listener → queue
key/button up   → captured action → enabled action listener → queue
mouse button    → MouseManager → action queue and positional mouse caches
scroll          → MouseManager → enabled mouse listener → queue
```

States are 1 for down and 0 for up. Unmapped keys and keyboard `Input.Keys.UNKNOWN` (0) are ignored by the adapter. There are no action groups or manager filter APIs; key and mouse listeners retain their individual action lists.

`InputManager.releaseHeldInputs()` synchronously releases captured keys and buttons and is idempotent. Each balancing release is queued after pending presses for enabled listeners selecting that action, so callers must drain the queues. Call it on the main thread, outside event dispatch; there is no reentrant reset guarantee. It does not clear queues or block future input, except that an already-held physical slot cannot produce a fresh press until its actual up.

Each physical slot tracks down state and its accepted action. Duplicate downs and unmatched or late ups are suppressed. Direct repeat state 2 is delivered only while the logical press remains held. A synthetic release leaves the physical slot down until its real up, so repeats cannot reactivate it. A release uses the original action even after rebinding. Separate bindings to the same action produce separate down/up pairs, not per-action aggregation; consumers must account for that binding policy.

## Bindings

The input settings contract uses `assets/input.setting.json` with required `keyboard` and `controller` object sections and an optional `mouse` object section; each map may be empty. Each value must be an exact, case-sensitive `InputAction` enum name. Keyboard identifiers must be exact, case-sensitive LibGDX `Input.Keys` key constant names, such as `SPACE`, `SHIFT_LEFT`, `SHIFT_RIGHT`, and `NUM_1`, not display labels such as `Space` or `L-Shift`. Loading rejects unknown names, non-key constants such as `META` masks, duplicate aliases for the same physical key, and malformed or duplicate entries.

The default keyboard bindings map W/S/A/D to movement, `SPACE` to `ATTACK_PRIMARY`, `SHIFT_LEFT` and `SHIFT_RIGHT` to `ATTACK_SECONDARY`, `R` to `CYCLE_WEAPON`, and `N` to `TOGGLE_NIGHT`. These bindings are configured in tracked `assets/input.setting.json`, not hard-coded. `CYCLE_WEAPON` advances the optional player loadout weapon cycle on press only; see [Composite Characters](../.internal-dev/specifications/composite-characters.md) for configuration and restrictions. Controller defaults map the D-pad and left stick to movement and `FACE_SOUTH`/`FACE_WEST` to primary/secondary attack. The complete, case-sensitive controller identifier list is `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `FACE_SOUTH`, `FACE_WEST`, `LEFT_STICK_UP`, `LEFT_STICK_DOWN`, `LEFT_STICK_LEFT`, and `LEFT_STICK_RIGHT`. Unknown names fail startup. These are logical controls, not mouse button codes. The optional `mouse` map binds the exact, case-sensitive identifiers `MButton1`, `MButton2`, `MButton3`, `MButton4`, and `MButton5` to actions, corresponding respectively to left, right, middle, back, and forward. These are mouse bindings, not keyboard aliases. The shipped mouse map is empty. Changes take effect at startup; there is no hot reload.

### Gamepad polling

On LWJGL3, standard-mapped controllers are polled alongside keyboard input. The first connected controller supporting at least one configured control becomes active and retains ownership until disconnected; then the first supported remaining controller is selected. An empty controller map avoids accessing the controller backend. Disconnect releases only the active pad's delivered actions, not keyboard holds.

The first sample after selecting a pad or resetting input establishes a baseline: held controls are ignored until released and pressed again. Each stick axis direction is digital, pressing at 0.50 and releasing at or below 0.35; the gap provides hysteresis and a deadzone. Both axes can be active for diagonal movement, and the existing movement system normalizes direction rather than scaling speed by tilt. Each physical binding produces its own action down/up pair, including when multiple bindings select the same action; there is no per-action aggregation or ECS change. Polling cannot detect a complete press and release between samples.

`InputMap` maintains independent fixed arrays for keyboard (`Input.Keys.MAX_KEYCODE + 1`) and buttons (`Input.Buttons.FORWARD + 1`, codes 0–4). Overlapping codes do not collide. Keyboard `UNKNOWN` can be mapped but its adapter events are ignored; mouse `LEFT` (0) works independently.

| Operation | Keyboard | Mouse button |
| --- | --- | --- |
| Bind or clear with `null` | `setKey(code, action)` | `setButton(code, action)` |
| Look up | `getKey(code)` | `getButton(code)` |
| Test | `containsKey(code)` | `containsButton(code)` |
| Clear | `removeKey(code)` | `removeButton(code)` |

Setters throw `IllegalArgumentException` for invalid codes. Invalid lookups return `null`, containment checks return `false`, and removals do nothing. Removal clears only the requested device mapping. There are no generic `set`, `get`, `remove`, or `contains` aliases. LibGDX uses touch-named callbacks for desktop mouse buttons.

## Listeners and queues

Managers broadcast mapped actions to registered, enabled listeners. `KeyListener(int, List<InputAction>)` queues selected actions for later consumption. `MouseListener(int, List<InputAction>)` updates its latest position and queues selected buttons and scroll. Both are concrete caches, not listener interfaces. The former cache names and immediate-delivery callback listener implementations were removed without compatibility aliases; this is an intentional API break. LibGDX event callbacks remain, as do `KeyActionConsumer` and `MouseEventConsumer` for explicit queue consumption. Managers do not invoke those consumers directly; they run when the caller consumes or polls a queue.

Both circular queues are bounded FIFO. A full queue overwrites the oldest entry by default; `setRejectIfFull(true)` rejects the incoming entry instead. Either policy can lose a release and leave movement held. Size and drain queues appropriately; release and listener disabling do not clear them. Cached `consume` works even when the listener is disabled. Register or unregister outside event dispatch, on the single render thread. Listener arrays grow and avoid duplicate identity registration; volatile queue size does not make the queue thread-safe.

### Cached mouse library support

Mapped mouse buttons feed the same gameplay action queues as keyboard input. `InputManager` and `MouseManager` capture each button pair for both the shared action queue and existing positional mouse caches, including balancing releases. Register a `MouseListener` through the shared manager's `regMousePosListener`, and unregister it when finished; no additional LibGDX processor is needed. Supply a positive capacity and the button actions to retain.

`MouseEventConsumer.accept` receives `BUTTON` with a mapped action and state 1/0, or `SCROLL` with a null action and direction 1 up/0 down. Both share one FIFO and preserve relative order. `getPos()` returns the mutable latest screen-coordinate `Vector2` (origin top-left); copy it for a snapshot and unproject for world coordinates. Movement updates position without occupying queue space. Each queued event snapshots scalar x/y coordinates, so use those coordinates for a click rather than the later cursor position. Pointer 0 down/up/cancel updates position before its button event; an unmatched up or cancel emits no button event. Pointer 0 dragging and mouse movement also update position. This is primary mouse emulation, not multitouch.

Negative LibGDX scroll Y maps to up 1, positive Y to down 0; magnitude and horizontal scrolling are omitted. The queue exposes `isEmpty`, `isFull`, `setRejectIfFull`, `add`, `poll`, `consume`, and `peek`; `hasInputs()` reports pending entries. There is no movement history, timestamp, raw scroll magnitude, or thread-safety guarantee.

## Adapter limits and cleanup

- Adapter callbacks return `false` so later multiplexer processors can run; an earlier processor returning `true` prevents the manager from seeing an event.
- The adapter emits states 0 and 1, not repeat 2. LWJGL3 translates native repeats to `keyTyped`, not a portable per-key repeat notification. Direct manager broadcasts can accept state 2.
- There is no text input, modifier/scancode data, or native window lifecycle interception. Window-related enum actions can be mapped but do not intercept close or resize.
- `enableRawInput(true)` throws `UnsupportedOperationException`; `false` is a no-op. Cursor capture is not raw mouse motion.
- `cleanup()`/`dispose()` detach the manager only if it is the direct processor or a direct child of the currently active top-level multiplexer. The caller must detach it from nested or formerly active multiplexers.

## Validation

Repeatable regression and build commands:

```sh
./gradlew :core:inputSystemTest
./gradlew clean build
```

The static-main input regression harness is wired into `check`; the default core test task disables `failOnNoDiscoveredTests` for Gradle 9. The revised input test and desktop compilation passed `./gradlew :core:inputSystemTest :lwjgl3:compileJava --console=plain -Dorg.gradle.logging.level=lifecycle` (1,286 input checks, zero failures). The current revision also passed `./gradlew check --continue --console=plain -Dorg.gradle.logging.level=lifecycle` (21 actionable tasks; 1,286 input checks, zero failures). Earlier combined five-fix validation tested the superseded notification-restoration version, not this revision; it is historical and does not establish a current GL rerun. See `.internal-dev/bugs/.archive/controller-movement-event-test-failure.md` for the issue history.

A historical desktop run reached asset loading, Tiled controller creation, map layers, and 56–62 FPS without input exceptions before intentional timeout after 15 seconds (exit 124). Existing libdecor GTK initialization warnings and the intentional `old_tree1` atlas fallback error logs occurred. No physical-key device test or instrumented active/cached screen identity or disposal test was performed.

For the gamepad change, an isolated `./gradlew clean build --console=plain` succeeded with 1,365 input checks. A Linux desktop jar initialized the Controllers runtime, reached gameplay, and sustained approximately 62 FPS until an intentional 18-second timeout. No physical gamepad was attached.
