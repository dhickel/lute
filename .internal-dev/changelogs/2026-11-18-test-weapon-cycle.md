# Temporary test weapon cycle

## Date
2026-11-18

## Git Commit

6e351a57bd31b6a48c9a1545893a5d5ca35a14a6

## Change Summary
Bind R to cycle the optional ordered weapon list in player data without hard-coding weapon IDs in gameplay code.

## Files
- Input action and bindings, player data and controller routing, and focused tests.
- Input documentation and composite character specification.

## Behavioral Impact
Press R while idle to swap between the locally configured sword and bow. The existing loadout guard blocks switching during a pending or active attack, and bow aim also blocks cycling. Cooldowns are unchanged.

## Specification Impact
Documented the temporary test binding and `loadout.weapon_cycle`; future inventory and weapon management remain out of scope.

## Risks
External asset bundles without a test cycle leave R inactive. Local source and runtime player data have been configured.

## Follow-up Items
Manual playtest. Runtime asset generation, Gradle checks, and desktop integration tests passed in an isolated copy.
