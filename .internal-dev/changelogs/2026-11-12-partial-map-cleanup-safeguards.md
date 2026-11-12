# Partial map cleanup safeguards

## Date
2026-11-12

## Git Commit

5c91ff39664f5a54a02340a3e67b177e2a698ef9

## Change Summary
Avoid unloading the same map when replacing it, invoke renderer.dispose on shutdown, and prevent duplicate screen hide cleanup.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/RenderSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`

## Behavioral Impact
Same-map reactivation no longer unloads the map before reuse. Renderer disposal invokes renderer.dispose only. Repeated hide calls no longer repeat entity cleanup.

## Specification Impact
None. These changes correct lifecycle handling without changing the intended contract.

## Risks
Map replacement cleanup and asset ownership remain incomplete; these safeguards are not a complete transition implementation.

## Follow-up Items
Complete coordinated entity, input, physics, renderer, animation cache and asset teardown; remove screen-owned systems safely; exercise map transitions and re-entry with focused integration tests.
