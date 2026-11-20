# Verify lifecycle with a configured day length

## Date
2026-11-19

## Git Commit

19eba39bbebc78320741365a9a76ea961ca76bb2

## Change Summary
The desktop lifecycle test derives its expected clock advance from the configured day length instead of assuming the 24-minute default. Both ignored local copies of `game.settings.json` were set to a 60-minute day.

## Files
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/GameScreenLifecycleTest.java`
- Local only: `raw_assets/game.settings.json` and `assets/game.settings.json`

## Behavioral Impact
The local game uses one hour of unpaused simulation time per day. The code default and the asset generation workflow are unchanged.

## Specification Impact
None. This selects an existing setting and corrects a test assumption without changing the clock contract.

## Risks
The previous lifecycle assertion expected two in-game seconds per clamped update regardless of configuration. With a 60-minute day the correct increment is 0.8 seconds. The clock, lifecycle, and map travel checks all passed with the 60-minute configuration.

## Follow-up Items
None.
