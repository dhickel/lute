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
Open; source-confirmed lifecycle defect, runtime crash not reproduced. Discovered in 2026-11-17 audit. GitHub: https://github.com/dhickel/lute/issues/16 (open; related closed issues checked).

## Next Action
Define explicit ownership and removal order for screen-installed systems; test unload/recreate and callback cleanup before destroying the world. No application code changed by the audit.
