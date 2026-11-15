# Typed player sound definitions

## Date
2026-11-15

## Git Commit

6d61831f522844d9952a5982ca8b1cbafc6cbe8f

## Change Summary
Represent player move and damage sound definitions with a typed record, consistent with weapon sound definitions.

## Files
`PlayerData.java` and existing `DataLoaderTest.java` assertions.

## Behavioral Impact
The JSON structure and data-only placeholder behavior are unchanged. Player sound values remain required and nonblank.

## Specification Impact
None. The existing contract for player sound placeholders and JSON fields remains unchanged.

## Risks
Java callers of `PlayerData.sounds()` must use record accessors instead of map lookups.

## Follow-up Items
None.
