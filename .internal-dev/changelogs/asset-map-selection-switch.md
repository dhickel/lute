# Switch expression for asset map selection

## Date

2026-11-14

## Git Commit

e847188fabec7b6bd14e164333960d91d828737f

## Change Summary

Replaced the class-selection if/else chain in `AssetService.entries` with a guarded switch expression.

## Files

`core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`.

## Behavioral Impact

No intended behavior change. Class identity still selects the matching typed map. Null and unsupported types still throw `IllegalArgumentException`; ID validation and the unchecked map cast remain unchanged.

## Specification Impact

None. This is a syntax-only refactor of existing map selection.

## Risks

Uses guarded class patterns supported by the project's Java 25 toolchain. The pre-existing local `queue.getFirst()` edit is preserved but excluded from this change's commit.

## Follow-up Items

`./gradlew :core:assetServiceTest --offline --console=plain` passed for both implementations, including supported/null/unsupported types, lifecycle and partial-load cleanup tests. Validation ran with the preserved local queue edit present. Update existing PR #7 with only the switch refactor and this changelog.
