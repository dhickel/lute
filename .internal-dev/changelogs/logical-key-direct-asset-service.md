# Logical-key direct asset service

## Date

2026-11-14

## Git Commit

14c009ed40d94f8173d57f4454f18b6b6c4bca9b

## Change Summary

Replaced AssetManager-backed asset handling with a directly owned, logical-keyed asset service. Assets are keyed by resource class and logical ID, with unchecked generic retrieval and checked load-boundary validation.

## Files

- Asset service, asset definitions, direct texture and atlas loading helpers, and map manifest resolution.
- Custom Tiled loader, loading screen, audio/events, animation components/system, and entity/map consumers.
- Focused asset service test and existing manifest, combat, Tiled, and debug-render test consumers; minimal Gradle test registration.
- `docs/asset-service.md`, `.internal-dev/specifications/decisions.md`, and `.internal-dev/knowledge/asset-loading-and-ownership.md`.
- `.internal-dev/bugs/controller-movement-event-test-failure.md` records the independently reproduced baseline input validation failure (GitHub #6).

## Behavioral Impact

Loading is synchronous on the render thread; each queue update loads at most one entry. Repeated matching definitions are idempotent, conflicting definitions fail, and there are no reference counts. Unload cancels queued work or disposes a loaded resource. Sound/music events and animation components retain logical IDs rather than LibGDX descriptors. Direct atlas and map loading owns textures and cleans up partially constructed resources on runtime failure. Loading-screen update failures remain latched until `show()` retries.

## Specification Impact

Recorded the asset identity, loading, lookup, and ownership contract in `.internal-dev/specifications/decisions.md` and documented usage in `docs/asset-service.md`.

## Risks

Consumers must stop using resources and their dependents before unload. Separate logical keys for the same path own independent loads. Validation uses mocked GL, not a live desktop. The unrelated baseline input failure is tracked at https://github.com/dhickel/lute/issues/6; the full suite must not be reported as passing.

## Follow-up Items

Independent phase validation passed the asset, manifest, Tiled animation, combat (768 checks), and trigger-debug checks, including partial-load cleanup tests. Final integration validation and full check results will be recorded separately. No desktop launch is claimed.
