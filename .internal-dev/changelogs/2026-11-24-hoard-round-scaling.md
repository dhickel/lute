# Hoard round scaling

## Date
2026-11-24

## Git Commit

09bb35c090cee7c3fe295ab8dd1ff738b7bbcec3

## Change Summary
Apply a 5% bonus per round number to spawn batches and kill points. Round 10 applies a 50% bonus.

## Files
Hoard run rules, core and desktop arena tests, the durable decision, and the asset authoring guide.

## Behavioral Impact
Round each batch and kill award to the nearest whole number, with halves rounded up. Keep the reinforcement percentage and the night spawn bonus; neither changes how kill points are awarded. Points already earned do not change when a new round starts.

## Specification Impact
Updated the hoard round, scoring, and spawn contracts.

## Risks
Spawn counts can still be below budget if placement cannot find valid positions. Core hoard rules and desktop arena tests passed.

## Follow-up Items
None.
