# Filter-free input delivery

## Date
2026-11-04

## Git Commit

d9ec6d4cae57985ee52218bd407ca4ced3a84513

## Change Summary
Removed input group filters and documented explicit held-input release.

## Files
- `lute/core/src/main/java/io/mindspice/lute/engine/input/`: input managers, listeners and actions; removed `ActionType.java`.
- `lute/core/src/main/java/io/mindspice/lute/engine/{GameCore.java,components/Controller.java,screen/GameScreen.java}`: direct filter references and required cleanup call sites.
- `lute/core/src/test/java/io/mindspice/lute/engine/input/`: existing regression adaptations; replaced filter-specific cases with retained held-input checks in `HeldInputTest.java`.
- `lute/docs/input-system.md`: removed obsolete filter instructions.
- Shared `specifications/{decisions,index}.md` and `knowledge/lute/libgdx-input-integration.md`: filter-specific contract and correction only.

## Behavioral Impact
Enabled listeners receive mapped input without group filtering. Held keys and buttons can be released synchronously while physical state remains tracked until the actual up. Screen lifecycle boundaries release and drain held input.

## Specification Impact
The filter-based transition decision is superseded by the accepted filter-free design. Future state routing may use the existing input multiplexer separately; it is not implemented here.

## Risks
Individual key and cached mouse action whitelists remain. Release does not gate input, and callers must invoke it on the main thread outside callbacks and drain caches. Bounded queues and disabled listeners can still lose releases.

## Follow-up Items
No implementation follow-ups.

## Validation
- `./gradlew :core:compileJava :lwjgl3:compileJava --console=plain`: PASS.
- `./gradlew clean build --console=plain`: PASS; 1,170 checks, zero failed groups. Obsolete group-filter/reentrant switching checks were removed; unrelated adapter, queue, mouse capture, registration and mapping coverage remains, with held-release/rebinding checks adapted to the simplified API.
- Manual JShell using actual InputManager, Controller, ControllerSystem and MoveSystem: W movement produces direction (0,1) and position (0,1); release and zero-delta controller update restore direction (0,0) and empty the queue; queued inactive presses are drained on resume without moving; releasing holds before registering an incoming controller prevents orphan ups from a hidden press; a fresh incoming press works and final release clears direction and queue.
- Root diff review: no changes to ControllerSystem, MoveSystem, TiledAshleyConfigurator, LoadingScreen, mappings, queues, dependencies or multiplexer implementation. No replacement state-routing or gating framework was added.
- Global policy copies compare equal. Work is isolated on `cleanup/remove-input-filters`, targeting integration branch `screen_refactor`.
