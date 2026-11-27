## Date
2026-11-27

## Git Commit

9443cab1b4df785ada5a29396cd9723f9c490b2b

## Change Summary
Apply the gameplay viewport to the world and debug overlay draws and restore the caller viewport afterward.

## Files
`GameScreen.java`, `MenuFlowTest.java`.

## Behavioral Impact
Debug overlays align with gameplay after menu handoff and resize. Camera and simulation are unchanged.

## Specification Impact
None; restores expected debug alignment.

## Risks
Standalone renderer state restoration must remain intact.

## Follow-up Items
Menu flow, trigger debug, gameplay lifecycle, map travel and Hoard tests passed. Lighting and composite failures also reproduced on the unchanged baseline.
