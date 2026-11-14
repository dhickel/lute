# Controller movement-event test fails on baseline

## Summary

`InputSystemTest.controllerMovementEvent` expects a movement event on a movement press, but `ControllerSystem.moveEntity` changes direction without emitting that event. This predates the logical-key asset service work.

## Scope

`core/src/main/java/io/mindspice/lute/engine/systems/ControllerSystem.java` and `core/src/test/java/io/mindspice/lute/engine/input/InputSystemTest.java`.

## Reproduction

Run `./gradlew :core:inputSystemTest --console=plain`. Independently reproduced in a baseline source snapshot at `/tmp/lute-asset-baseline-MXfj5o`; relevant controller and test SHA-256 hashes match baseline commit `14c009ed40d94f8173d57f4454f18b6b6c4bca9b`.

## Expected

The current test expects one movement event after pressing movement. Whether that expectation or the implementation should change requires reconciling the intended movement-event contract.

## Actual

`java.lang.AssertionError: press emits once: expected 1, got 0` at `InputSystemTest.controllerMovementEvent` line 119. Output: `InputSystemTest: 1235 checks, 1 failed groups`.

## Evidence

Controller source SHA-256: `a00965b54e9888086f873a18c4e5b75fef2116e390de081b95980eade0a64fee`. Input test SHA-256: `1428a7f1a668e1fa0d598897b6af859beaa842d79e769faf10226483640ff01d`. Root reproduced the focused baseline failure; full check also reports this failure.

## Impact

The existing input validation and full `check` gate fail independently of the asset changes. No input behavior is changed by the asset implementation.

## Status

Open; out of scope for the asset service replacement. GitHub mirror: https://github.com/dhickel/lute/issues/6. Related open/closed issues were checked before creation.

## Next Action

Reconcile the intended movement-event behavior, repair either implementation or stale assertion accordingly, and rerun input and full validation. Do not silently add gameplay behavior as part of the asset service change.
