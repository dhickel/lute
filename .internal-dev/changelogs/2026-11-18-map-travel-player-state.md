# Map Travel and Live Player State

## Date
2026-11-18

## Git Commit

6b0488365249002e52b2604e7ac636d10b57e2ad

## Change Summary
Added named map travel with session-owned player state and documented its authoring and ownership contracts.

## Files
Runtime and test files in commit `6b048836`; `docs/asset-service.md`; the teleport decision, horizon ideas, and map-spawning and screen-lifecycle knowledge records.

## Behavioral Impact
Named travel rebuilds the destination runtime while retaining health, loadout, and facing. `THIS` remains in-place; transient combat and input state reset on rebuild.

## Specification Impact
Updated the teleport and spawn decision and replaced the obsolete player-state horizon idea with a save-persistence idea.

## Risks
Travel loads synchronously and does not hot-reload disk changes. Stock-sword assertions fail against the user's bow-default local assets; full checks pass with stock assets in an isolated copy.

## Follow-up Items
Regenerate local runtime assets from the authored test map before manually testing the named teleport.
