## Date
2026-11-27

## Git Commit

bfdfb3f26c8cf7e0bbd8bc8fc8fb5828c15dd0c0

## Change Summary
Added chainable `GameData` methods for bulk and single-enemy infighting chance overrides before spawning.

## Files
- `GameData.java`, `ActorsData.java`, and `MapActorData.java`
- `LightingDataTest.java`
- Infighting specifications and asset guide

## Behavioral Impact
Both methods return a new snapshot and validate chances from 0 through 100. The bulk method updates every definition in one pass; a subsequent named override changes only that ID. Gameplay configuration, existing actors, and the demo opt-out remain unchanged.

## Specification Impact
Updated the startup override contract and validation criteria.

## Risks
Callers must use the returned snapshot before spawning actors.

## Follow-up Items
None. Data, awareness, and Hoard arena tests passed.
