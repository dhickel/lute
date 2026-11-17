# Issue 6: restore established press-only movement notification

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
Restore the Player.Moved notification accidentally removed in readability refactor 8275ad8. History ed4f952 establishes the press-only contract. Expanded negative and attack-routing coverage. Independent phase review after prose correction: PASS.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/ControllerSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/input/InputSystemTest.java`
- `docs/input-system.md`
- `.internal-dev/knowledge/input-listeners.md`
- `.internal-dev/bugs/controller-movement-event-test-failure.md`

## Behavioral Impact
Valid movement presses on an entity with Move emit exactly one movement-input notification through EventEmitter. Releases, repeats, invalid states, missing Move and attack requests do not. No current audio subscriber consumes this notification; it is not physical-displacement reporting.

## Specification Impact
Specification Impact: none. Restores the established historical behavior rather than selecting a new movement or audio feature.

## Risks
Notification semantics remain input-based, not collision/position-based. Native combat assertion counts vary across runs; prose distinguishes the independent 1,920-check run from the prior 1,928-check run. Coordinating Git hash identifies the audit baseline; fix branch starts at code-equivalent parent 33dbd077ff1c66bf211971086b77caf2c8800563.

## Follow-up Items
Baseline input failure reproduced at 1,235 checks/one failed group. Independent fixed input test passed 1,286 checks and desktop compilation passed; combat regression passed independently. A prose-only BLOCKED gate was corrected and the same validation model returned PASS. Final combined integration with #8/#16/#17/#18 passed the unfiltered full check (input 1,286, audio 196), lifecycle 157, gameplay 15,815 / 480 comparisons and synthetic GL 11,854 checks. This is combined evidence, not inclusion of sibling fixes in this branch. Issue #6 remains open until separate PR merge; no merge performed.
