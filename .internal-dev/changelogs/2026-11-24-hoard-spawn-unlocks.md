# Hoard spawn area unlocks

## Date
2026-11-24

## Git Commit

a76e5a64d3c2eb5f3853f639fe516626bea85d96

## Change Summary
Hoard spawn areas now unlock every five completed rounds, increasing the base batch budget as the arena expands.

## Files
Hoard run and spawn system, core and desktop arena tests, the arena decision, and the asset guide.

## Behavioral Impact
Areas 2, 3, and 4 join the opening wave on rounds 6, 11, and 16. Each unlock adds one enemy to the baseline before existing batch modifiers. Locked zones are never used for placement retries. A fresh run starts with only Area 1.

## Specification Impact
Updated the documented spawn-area progression and batch baseline.

## Risks
Actual spawns remain subject to available space. Focused core and desktop arena tests passed.

## Follow-up Items
None.
