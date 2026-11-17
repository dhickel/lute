# Controller movement-event test fails on baseline

## Summary

`InputSystemTest.controllerMovementEvent` expects a movement event on a movement press, but `ControllerSystem.moveEntity` changes direction without emitting that event. This predates the logical-key asset service work.

## Scope

`core/src/main/java/io/mindspice/lute/engine/systems/ControllerSystem.java` and `core/src/test/java/io/mindspice/lute/engine/input/InputSystemTest.java`.

## Reproduction

Run `./gradlew :core:inputSystemTest --console=plain`. Independently reproduced in a baseline source snapshot at `/tmp/lute-asset-baseline-MXfj5o`; relevant controller and test SHA-256 hashes match baseline commit `14c009ed40d94f8173d57f4454f18b6b6c4bca9b`.

## Expected

One movement event is emitted per consumed movement press on an entity with `Move`. Releases, repeats, invalid states, movement input without `Move`, and attack requests emit no movement event. This is an input notification, not confirmation of physical displacement.

## Actual

`java.lang.AssertionError: press emits once: expected 1, got 0` at `InputSystemTest.controllerMovementEvent` line 119. Output: `InputSystemTest: 1235 checks, 1 failed groups`.

## Evidence

Controller source SHA-256: `a00965b54e9888086f873a18c4e5b75fef2116e390de081b95980eade0a64fee`. Input test SHA-256: `1428a7f1a668e1fa0d598897b6af859beaa842d79e769faf10226483640ff01d`. Root reproduced the focused baseline failure; full check also reports this failure. During the weapon asset/settings refactor, the identical 1,235-check failure was reproduced again from preserved baseline commit `a1d69f3` in `/tmp/lute-baseline-0Haiat`. `check` excluding `core:inputSystemTest` and desktop compilation pass for the refactor. During composite-loadout implementation, the same failure was reproduced from an isolated Git archive of baseline `c79e830`; evidence is `/tmp/lute-phase2-baseline-input.log`. The composite attack input tests pass separately; no movement-event contract change is included.

## Impact

The existing input validation and full `check` gate fail independently of the asset changes. No input behavior is changed by the asset implementation.

## Status

Implemented in the pending issue 6 PR; not yet closed or archived. GitHub mirror: https://github.com/dhickel/lute/issues/6. Commit `ed4f952` established press-only emission; `8275ad8` removed it. The fix restores emission through `EventEmitter`. Current gameplay has no subscriber, and audio is unchanged.

Independent focused validation passed: `:core:inputSystemTest` (1,286 checks), `:lwjgl3:compileJava`, and `:core:combatSystemTest` (1,920 checks in that run; an earlier run passed 1,928). Combat check counts can vary with native contact iterations. The final full validation gate and PR are pending.

## Next Action

Complete the final full validation gate and open the PR; retain this record until the issue is resolved.
