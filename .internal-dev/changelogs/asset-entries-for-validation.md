# Separate asset map selection from ID validation

## Date

2026-11-14

## Git Commit

f62d99972626b8215a67142eea768caf0ef7d897

## Change Summary

Renamed the map selector to `entriesFor(Class<T>)` and extracted `validateId(String)` so selection depends only on resource class.

## Files

`core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`.

## Behavioral Impact

`get`, `isLoaded`, and `unload` validate their supplied ID separately after selecting the map, retaining previous type-before-ID validation order and errors. Internal load/queue paths use IDs already validated by the sealed asset definition constructors. Public API, guarded switch, unchecked cast, and resource lifetime are unchanged.

## Specification Impact

None. This separates private helper responsibilities without changing intended behavior.

## Risks

Internal asset IDs rely on the existing validated, sealed load definitions; no new input path bypasses validation.

## Follow-up Items

`./gradlew :core:assetServiceTest --offline --console=plain` passed for both storage implementations, including null/blank IDs, unsupported/null classes, lifecycle and partial-load cleanup checks. Update existing PR #7 after committing the dedicated refactor branch.
