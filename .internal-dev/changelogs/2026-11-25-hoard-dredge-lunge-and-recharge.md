# Bring Dredge’s lunge into Hoard and delay stamina recharge

## Date
2026-11-25

## Git Commit

1cb9110dfdd0c40a1c4889e49a7236d78e4eb99f

## Change Summary
Use the tested duel lunge for Hoard Dredge guards and delay stamina recovery until two active simulation seconds after the last drain.

## Files
Hoard spawn and stamina logic, Dredge lunge and combat integration, Hoard integration tests, README, and Hoard decisions.

## Behavioral Impact
Hoard Dredge commits in clear sight within 80 logical pixels and advances on the active hammer frames without steering. Solid cover blocks damage. Stamina refill rate and sprint ramps are unchanged; pauses freeze the delay.

## Specification Impact
Updates the intended Hoard guard and sprint behavior. The isolated duel remains on its original branch.

## Risks
Requires controller playtesting for feel. The existing composite sword-contact failure reproduced on the unchanged baseline.

## Follow-up Items
Playtest the first Dredge encounter at round five.
