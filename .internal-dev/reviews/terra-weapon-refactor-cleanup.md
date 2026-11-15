# Terra weapon refactor cleanup review

## Scope

Review of implementation commit `b35af15222fbf2de45b69b92cf4637192e051da0` and its documentation, performed by `openai-codex/gpt-6.1-terra` at high thinking. Root inspected the returned diff and independently reran focused validation. Cleanup is recorded in commit `06fe765` on the existing PR #9 branch.

## Findings

- `Animation2D.configureAttack` repeated a null guarantee already enforced by runtime weapon construction. Terra replaced the check with assignment.
- Root integration found the same redundant check in `Combat.setSensor`, whose caller supplies the fixture returned by Box2D. Root replaced it with assignment.
- Data and tutorial-atlas tests assumed specific sword hit/sound frames. Terra changed them to verify authored values, with separate nonzero frame-deserialization coverage. Factory timing checks derive boundaries from the runtime weapon and animation.
- Terra reported no other correctness or architecture findings within the reviewed refactor. This is not a whole-project audit.

## Risk Assessment

JSON validation, runtime construction invariants, missing asset lookups, optional attack state, loaded-animation frame limits, and native geometry safety remain. An animated noncombat object can legitimately lack attack metadata, and a pending attack can legitimately lack a sensor. Those null checks are behavioral requirements, not redundant validation.

User edits to sword timing and `WeaponAssets` formatting were preserved and excluded from cleanup commits. Focused checks ran with the current user timing, so their dynamic count is 185 rather than the previous 193.

## Recommendations

Validate external input and meaningful construction boundaries, then trust those invariants in internal assignment methods. Retain checks that model absent optional state or native safety requirements.

## Follow-ups

Passed independently: `./gradlew core:combatSystemTest core:dataLoaderTest core:assetServiceTest core:manifestLoaderTest lwjgl3:classes` and `git diff --check`. No graphical launch or new full-suite run was performed. The previously verified full-suite failure in input movement-event issue #6 remains outside scope. No further refactor defects were identified by this review.
