## Date
2026-11-27

## Git Commit

e788935fa2c0fa6a55a670d54c5ceb8f8e0cf176

## Change Summary
Scope programmatic infighting overrides to one enemy definition ID. Remove the shared numeric override.

## Files
- `ActorsData.java`, `AwarenessSystem.java`, and `GameScreen.java`
- Awareness, enemy data, and map sensor fixtures
- Infighting specifications and asset guide

## Behavioral Impact
The victim uses its own definition's percentage. A code override returns a copy of the catalogue with only the named definition changed. A mode can disable infighting without changing percentages.

## Specification Impact
Records the definition-scoped code override and the demo's opt-out.

## Risks
Overrides affect definitions used for spawning, not actors already spawned.

## Follow-up Items
None. Enemy data, awareness, map sensor, and Hoard arena tests passed.
