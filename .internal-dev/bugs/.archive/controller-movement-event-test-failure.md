# Controller movement-event test fails on baseline

## Summary

The baseline `InputSystemTest.controllerMovementEvent` expected a movement event on a press, but `ControllerSystem.moveEntity` changed direction without emitting it. The user approved keeping emission removed and correcting the stale assertion.

## Scope

`core/src/main/java/io/mindspice/lute/engine/systems/ControllerSystem.java` and `core/src/test/java/io/mindspice/lute/engine/input/InputSystemTest.java`.

## Reproduction

On the historical baseline, `./gradlew :core:inputSystemTest --console=plain` reproduced the failure in `/tmp/lute-asset-baseline-MXfj5o`; relevant controller and test SHA-256 hashes matched baseline commit `14c009ed40d94f8173d57f4454f18b6b6c4bca9b`.

## Expected

The controller updates movement direction directly without emitting `Event.Player.Moved`. The revised test checks zero movement events and retains direction, release, invalid input, missing `Move`, and attack-routing coverage.

## Actual

On the historical baseline: `java.lang.AssertionError: press emits once: expected 1, got 0` at `InputSystemTest.controllerMovementEvent` line 119. Output: `InputSystemTest: 1235 checks, 1 failed groups`.

## Evidence

Historical controller source SHA-256: `a00965b54e9888086f873a18c4e5b75fef2116e390de081b95980eade0a64fee`. Historical input test SHA-256: `1428a7f1a668e1fa0d598897b6af859beaa842d79e769faf10226483640ff01d`. The baseline full check also reported this failure. The same failure was reproduced from baseline commit `a1d69f3` in `/tmp/lute-baseline-0Haiat` and from an isolated archive of `c79e830` (`/tmp/lute-phase2-baseline-input.log`). The asset refactor's check excluding `core:inputSystemTest` and desktop compilation passed; composite attack input tests passed separately. These are historical results.

Commit `ed4f952` emitted on presses; `8275ad8` removed that emission. An earlier issue #6 fix restored it, but that restoration was superseded by the user's explicit decision. The existing event type and `EventEmitter` marker remain.

For the corrected revision, `./gradlew :core:inputSystemTest :lwjgl3:compileJava --console=plain -Dorg.gradle.logging.level=lifecycle` passed, with 1,286 input checks and zero failures. The unfiltered `./gradlew check --continue --console=plain -Dorg.gradle.logging.level=lifecycle` also passed for the current revision (21 actionable tasks; 1,286 input checks, zero failures). Prior combined five-fix validation exercised the superseded restoration and is historical; it does not establish a current GL rerun.

## Impact

The historical stale assertion failed input and full-check validation independently of the asset changes. The correction changes the test, not controller behavior.

## Status

Resolved as a stale assertion; validated and ready for PR merge. GitHub: https://github.com/dhickel/lute/issues/6.

## Next Action

Retain this report as resolved history and verify GitHub issue closure when the PR is merged.
