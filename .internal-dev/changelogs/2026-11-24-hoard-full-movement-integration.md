# Hoard full movement integration

## Date
2026-11-24

## Git Commit

0ec3d38d3ca3bbb4b0a38a0c2695f63bb3206649

## Change Summary
Merge the approved full-movement combat behavior from master into Hoard.

## Files
Combat, facing, movement, combat and map-travel tests, and the composite-character specification.

## Behavioral Impact
Reconciled player melee movement and recovery validation with committed-facing combat behavior. Hoard archers and wave and scoring rules remain unchanged.

## Specification Impact
Replaced the old stationary melee-steering contract with committed attack facing during movement and recovery cancellation.

## Risks
Core, desktop arena, and desktop map-travel tests passed.

## Follow-up Items
None.
