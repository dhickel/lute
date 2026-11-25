# Align melee awareness tests with the base delay

## Date
2026-11-25

## Git Commit

61a84c2f6217694e4d7042f170b05a3995b822e1

## Change Summary
While validating the Hoard alpha merge, updated two stale 149 ms hold checks in `AwarenessSystemTest` to 49 ms, just below the documented 50 ms melee minimum. Hoard run, awareness, combat, live arena, and map travel tests passed.

## Files
`core/src/test/java/io/mindspice/lute/engine/systems/AwarenessSystemTest.java`

## Behavioral Impact
None. Test timing only.

## Specification Impact
None. The assertions now match the existing 50–250 ms melee timing.

## Risks
None identified.

## Follow-up Items
None.
