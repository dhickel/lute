# Custom screen lifecycle forwarding

## Topic
LibGDX `Game` lifecycle ownership when using the project's custom `ScreenManager`.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/GameCore.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/ScreenManager.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/LoadingScreen.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/GameScreenLifecycleTest.java`
- LibGDX 1.14.2 `Game` bytecode inspected with `javap -c`.

## Key Takeaways
- `Game.render()`, `resize()`, `pause()`, and `resume()` forward to LibGDX's own screen field, not to a separate manager's current-screen reference. A custom manager needs explicit forwarding from the application.
- Assigning a current-screen reference does not call `show()`. Startup activates the loading screen first, presents a black frame, and loads shared assets before constructing the gameplay screen and triggering map callbacks.
- `GameCore` retains attempt ownership until `ScreenManager.mountScreen` successfully shows and resizes gameplay. A failed attempt disposes its session and newly registered shared assets, preserving pre-existing registrations and unrelated queued work; the menu permits retry while direct-map startup propagates the failure. Switch away from the loader before unloading it on success. Dispose screens before shared input, batch, and asset resources.
- A successful startup reports loading completion, map/entity setup, and continuing FPS output. Atlas-region fallback warnings are distinct from an unloaded-atlas exception.
- `GameScreen` records the systems it installs on the shared engine. Hiding retains them for reuse; disposal hides the screen, then removes and disposes its owned systems in reverse installation order before disposing its physics `World`. This keeps the event bus and world available while dependents detach and prevents later engine updates from reaching disposed physics. Foreign systems are not owned or disposed by the screen.
- Disposal is idempotent, a disposed screen cannot be shown again, and a constructor installation failure invokes the same cleanup for the systems recorded so far. A cleanup failure is suppressed on the original installation failure. Never-shown screens also need disposal.
- `GameScreen` applies pending named travel after the engine update, outside the Box2D step. Hide tears down the map runtime; show rebuilds the current entry with session health, loadout, and facing retained. Transient combat and held input do not carry over. A new screen starts a new session.
- Map preparation loads synchronously and leaves the active runtime intact on preparation failure. An explicit current-map ID rebuilds runtime objects but reuses registered asset data; it is not a disk hot reload.

## Project Relevance
The refactor initially bypassed `LoadingScreen.show()` and never called its `render()` callback. The minimal repair keeps the custom manager and forwards the missing lifecycle calls. A historical 15-second desktop smoke run reached loading completion and game rendering; a separate `old_tree1` region fallback was tracked in `.internal-dev/bugs/missing-old-tree-atlas-region/report.md`. This is not a new smoke run.

PR #21 is merged and issue #16 resolved. Its validation, based on merged PR #19, passed the full check (21 tasks) and real-GL lifecycle 157, gameplay 15,815 checks and 480 comparisons, and synthetic 11,854 checks. The lifecycle checks cover hide/show reuse, recreation, updates after disposal, foreign-system survival, repeated and never-shown disposal, partial installation failure, and normal shutdown. PR #19 cleared the stale input assertion.

PR #22, based on #21, passed the full check and actual generator run. PR #23, based on #22, passed the full check (21 tasks), Linux packaging, and a fresh ZIP launch with bundled Java 25. Both PRs are merged and issues #17 and #18 are closed.

The earlier isolated combined validation of fixes #6, #8, #16, #17, and #18 was historical; it does not imply that audio PR #20 is included in the current merged changes.

## Open Questions
Audio lifecycle issue #8 and PR #20 remain open; PR #20 is not merged. Screen-owned system teardown does not establish that audio subscription internals are fixed. The tree atlas-content issue also remains separate.
