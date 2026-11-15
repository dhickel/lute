# User asset load null-check cleanup and premerge validation

## Date

2026-11-14

## Git Commit

82bf4e77e7890c566fe793ca23bd175863b1b2be

## Change Summary

Commit the user's small `loadEntry` edit: apply `Objects.requireNonNull` to `loadDirect(asset)` before assigning its result, then perform the load-boundary class cast on the checked value.

## Files

`core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`.

## Behavioral Impact

No intended behavior change. Null loader results still fail, failed registrations are removed, and returned resources still undergo type validation and cleanup on failure.

## Specification Impact

None. Existing loading, lookup, and lifecycle contracts remain unchanged.

## Risks

No live desktop launch performed. Full validation is not green because of the previously verified unrelated baseline input failure, tracked as GitHub #6.

## Follow-up Items

Premerge `./gradlew check :lwjgl3:build --continue --offline --console=plain` passed asset lifecycle checks for both implementations, partial-load cleanup, combat (768 checks), data, manifest, Tiled animation, and debug-render checks. Its sole failing task was unchanged `InputSystemTest.controllerMovementEvent` (expected 1, got 0). Separate `./gradlew :lwjgl3:build -x test --offline --console=plain` passed. Diff whitespace and global policy synchronization checks passed. User explicitly requested adding this edit to PR #7 and merging to master. Preserve the two pre-existing untracked exploration review files.
