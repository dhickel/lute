# Four-area mixed hoard waves

## Date
2026-11-24

## Git Commit

aac8353a5ce17bd7e80cf3091b815c87068e131b

## Change Summary
Use the four authored arena spawn polygons with shared wave logic and shorten rounds to 120 simulation seconds.

## Files
Hoard and Tiled systems, hoard rules, regression tests, arena decision and authoring guide, and local map assets.

## Behavioral Impact
The existing batch budget is shared across four zones. Batches start in a random zone, rotate across zones, and try other zones if blocked without rerolling the enemy. Each slot independently rolls 50/50 melee or archer. The new authored map has no fixed archer. Friendly fire and retaliation are unchanged.

## Specification Impact
Updated the arena spawn and timing contracts.

## Risks
Core and desktop arena tests passed, including real polygon placement and fallback. The broader lighting crosshair assertion also fails on the unchanged baseline.

## Follow-up Items
None for this change.
