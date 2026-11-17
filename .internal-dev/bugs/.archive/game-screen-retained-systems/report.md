# GameScreen disposal retains systems referencing a disposed World

## Summary
P2: `GameScreen.dispose()` disposes its Box2D world but leaves its installed systems on the shared Ashley engine.

## Scope
`core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java:100–107`; `systems/PhysicSystem.java:27–40`; `screen/ScreenManager.java:27–35`.

## Reproduction
Static reproduction path: construct and show a GameScreen; unload it through ScreenManager while GameCore remains alive; update the shared engine. Do not exercise the disposed native World in-process without crash isolation.

## Expected
A disposed screen no longer has live engine systems referencing its resources.

## Actual
Disposal loops over Disposable systems but never removes systems from the engine, then destroys the World. PhysicSystem remains registered and unconditionally invokes world.clearForces(), even with no entities or physics step.

## Evidence
Source inspection confirms installation in the GameScreen constructor, disposal without removal, and unconditional native access in PhysicSystem.update. No destructive native runtime reproduction was attempted. Normal whole-application shutdown does not demonstrate this scenario. Related audio lifetime defect: GitHub #8.

## Impact
Unloading a game screen while the engine survives permits native use-after-dispose and stale systems/listeners. Current single-screen shutdown is a narrower path and may not expose it.

## Status
Resolved by merged PR #21 (f93a19147e1fc2c99ebf57c45672cc9860508589). GitHub issue #16 is closed; archived after confirmation. The merge candidate passed the unfiltered check and 157 real-GL lifecycle checks, including safe engine updates after disposal.

## Next Action
Retain as resolved audit history. Screen-owned systems are removed/disposed before the World; unrelated audio subscription work remains open in PR #20 / issue #8.
