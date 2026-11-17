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
- Assigning a current-screen reference does not call `show()`. The initial loading screen must be activated before game-screen map callbacks retrieve atlas assets.
- Switch away from a loading screen before unloading and disposing it. Dispose screens before shared input, batch, and asset resources.
- A successful startup reports loading completion, map/entity setup, and continuing FPS output. Atlas-region fallback warnings are distinct from an unloaded-atlas exception.
- `GameScreen` records the systems it installs on the shared engine. Hiding retains them for reuse; disposal hides the screen, then removes and disposes its owned systems in reverse installation order before disposing its physics `World`. This keeps the event bus and world available while dependents detach and prevents later engine updates from reaching disposed physics. Foreign systems are not owned or disposed by the screen.
- Disposal is idempotent, a disposed screen cannot be shown again, and a constructor installation failure invokes the same cleanup for the systems recorded so far. A cleanup failure is suppressed on the original installation failure. Never-shown screens also need disposal.

## Project Relevance
Historically, the refactor bypassed `LoadingScreen.show()` and never called its `render()` callback. The repair kept the custom manager and forwarded the missing lifecycle calls. A 15-second desktop smoke run reached loading completion and game rendering; a separate `old_tree1` region fallback is tracked in `bugs/missing-old-tree-atlas-region/report.md`.

The focused real-GL `:lwjgl3:gameScreenLifecycleTest` passed 157 checks covering hide/show reuse, unload and recreation, engine updates after disposal, foreign-system survival, repeated and never-shown disposal, partial installation failure, and normal shutdown. MapSensor (1732 checks), combat (1928 checks), and debug renderer checks also passed. No independent phase or full integration gate has completed yet.

## Open Questions
Audio lifecycle issue #8 is separate and pending PR; screen-owned system teardown does not establish that audio subscription internals are fixed. The tree atlas-content issue also remains separate.
