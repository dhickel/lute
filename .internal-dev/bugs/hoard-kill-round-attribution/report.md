## Summary
Hoard score attribution reads the round at event delivery rather than at the kill.

## Scope
`HoardRun.enemyKilled`, `HoardSystem` event handling, and the Hoard scoring decision in `.internal-dev/specifications/decisions.md`.

## Reproduction
Not run during this static audit. Commit a credited kill immediately before a round transition and deliver its event after the transition.

## Expected
The documented rule uses the round at kill time.

## Actual
`HoardRun.enemyKilled(int baseXp)` uses its current round; the listener supplies base XP without a kill-time round.

## Evidence
`HoardSystem.java:63`, `HoardRun.java:62`, and the architecture review. The earlier Hoard review also left rollover attribution open.

## Impact
A kill across rollover can use the next round’s multiplier.

## Status
Open; confirm with a focused rollover regression.

## Next Action
Verify the event ordering and settle the scoring discrepancy before changing gameplay.
