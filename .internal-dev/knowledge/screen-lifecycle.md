# Custom screen lifecycle forwarding

## Topic
LibGDX `Game` lifecycle ownership when using the project's custom `ScreenManager`.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/GameCore.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/ScreenManager.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/LoadingScreen.java`
- LibGDX 1.14.2 `Game` bytecode inspected with `javap -c`.

## Key Takeaways
- `Game.render()`, `resize()`, `pause()`, and `resume()` forward to LibGDX's own screen field, not to a separate manager's current-screen reference. A custom manager needs explicit forwarding from the application.
- Assigning a current-screen reference does not call `show()`. The initial loading screen must be activated before game-screen map callbacks retrieve atlas assets.
- Switch away from a loading screen before unloading and disposing it. Dispose screens before shared input, batch, and asset resources.
- A successful startup reports loading completion, map/entity setup, and continuing FPS output. Atlas-region fallback warnings are distinct from an unloaded-atlas exception.
- Audio retains its actual `EventSystem` owner and exact consumers for `Map.Load`, `Sound.Play`, and `Sound.PlayAtVolume`. Detach queues their removal via `EventListener` helpers and clears the owner; callbacks guard against asset access while removal awaits a safe boundary. Ordinary queued unsubscription does not suppress callbacks in the current batch. Same-instance reattachment can reuse active callbacks once in that batch; boundary processing leaves exactly one registration.
- Correction: synchronous active-list removal, dispatch snapshots, and per-event membership scans are not needed for teardown and add mutation or dispatch cost. Changes remain queued until batch boundaries or non-dispatching bus removal. A retired bus stops remaining dispatch, drops pending events, and flushes cleanup in `finally` even on callback failure, without a future update; unrelated listeners remain owned by their own systems. See the queued event listener decision in `../specifications/decisions.md`. This does not resolve shared screen-system ownership in issue #16.

## Project Relevance
The refactor initially bypassed `LoadingScreen.show()` and never called its `render()` callback. The minimal repair keeps the custom manager and forwards the missing lifecycle calls. A 15-second desktop smoke run reached loading completion and game rendering; a separate `old_tree1` region fallback is tracked in `bugs/missing-old-tree-atlas-region/report.md`.

## Open Questions
None for the screen lifecycle repair; the tree atlas-content issue remains separate.
