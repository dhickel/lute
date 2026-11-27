## Date
2026-11-27

## Git Commit

a12b77e5e13f53225203dfc2ced1735f91041bc3

## Change Summary
Pass only the arriving-enemy check to the arena melee drain and return casualties to Hoard for death handling.

## Files
`CombatSystem.java`, `HoardSystem.java`.

## Behavioral Impact
None. Contact, death, pickup and clock order, plus kill-credit timing, are unchanged.

## Specification Impact
None. This changes the internal handoff, not the fixed-tick contract.

## Risks
Preserve contact order and handle deaths before pickups or rollover.

## Follow-up Items
Combat, awareness, Hoard roster, run, and arena tests passed. Scoring rollover is out of scope.
