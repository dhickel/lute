## Date
2026-11-26

## Git Commit

f222ef3021d2440fd34de003134767e07161b27b

## Change Summary
Changed the Hammer Demo’s starting weapon from `basic_hammer` to `basic_axe`.

## Files
- `TiledSystem.java`: demo starting weapon.
- `HoardHammerDemoTest.java`: launch and remount coverage.
- `.internal-dev/specifications/decisions.md`: updated starting weapon.

## Behavioral Impact
All weapons remain available. Scored Hoard is unchanged.

## Specification Impact
The demo specification now names `basic_axe` as the equipped starting weapon.

## Risks
None identified.

## Follow-up Items
None. Hoard Hammer Demo and Hoard arena tests passed.
