# Set fixed Hoard batch sizes

## Date
2026-11-25

## Git Commit

e7830a5290ff768720e505f6c34a5fb6a5df597a

## Change Summary
Start each Hoard visit with 10 regular enemies. Spawn 5 per subsequent refresh, including round transitions.

## Files
`HoardRun.java`, `HoardRunTest.java`, `HoardArenaTest.java`, and `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
Batch totals are spread across the four zones. Refresh timing, roster unlocks, and Dredge's separate schedule are unchanged.

## Specification Impact
Replaces variable batch sizes with fixed opening and refresh counts.

## Risks
Existing placement rules can still skip a spawn when all zones are blocked.

## Follow-up Items
None.
