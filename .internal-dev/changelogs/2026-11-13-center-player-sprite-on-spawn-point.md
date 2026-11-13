# Center player sprite on spawn point

## Date
2026-11-12

## Git Commit

8275ad841095a5a8549c3eb7c497d881d79f2d2b

## Change Summary
Subtract half the world-space sprite dimensions from the converted spawn point when building the player Transform.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `.internal-dev/knowledge/point-player-spawning.md`

## Behavioral Impact
The sprite center, rather than its bottom-left corner, starts at the point. The physics body continues to use the same origin as Transform, preserving collider alignment.

## Specification Impact
Specification Impact: none. No existing living specification defines spawn anchoring; this corrects the user-reported placement error.

## Risks
Centering uses the initial animation frame's full region dimensions, including transparent pixels.

## Follow-up Items
Core compilation and diff checks passed. Arithmetic checks covered the current point, zero/negative coordinates, and non-square dimensions. No smoke runs performed.
