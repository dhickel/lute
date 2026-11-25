# Use a shared Hoard wave roster

## Date
2026-11-25

## Git Commit

53bba342b5ea723b7bcd5903a47870895e06faab

## Change Summary
Activate all four wave areas from round 1. Draw each wave enemy uniformly from one roster that grows every fifth round until all eight types are unlocked. Reimport the authored Hoard map with its moved player spawn.

## Files
`HoardRun.java`, `HoardSystem.java`, `TiledSystem.java`, Hoard run and arena tests, and `.internal-dev/specifications/decisions.md`. The imported TMX and its ownership entry are local generated assets.

## Behavioral Impact
Dredge still appears separately every fifth round. Area bounties and wave pacing remain unchanged.

## Specification Impact
The Hoard decision now describes area availability and roster progression separately.

## Risks
Blocked placement can still skip an enemy after all areas have been tried; retries keep the original enemy roll.

## Follow-up Items
None.
