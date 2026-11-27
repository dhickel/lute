## Date
2026-11-26

## Git Commit

08834a7d55ff8cb3cc95ab8ac796fdd27a568ae1

## Change Summary
The Hammer Demo spawns a 100-undead swarm instead of a Dredge encounter.

## Files
- `HoardSystem.java`, `HoardDemoCombatant.java`, and `AwarenessSystem.java`
- `HoardHammerDemoTest.java`
- `.internal-dev/specifications/decisions.md`

## Behavioral Impact
The demo starts with the basic hammer equipped and retains all weapons. The swarm pursues the player. Dredge does not spawn in the demo; scored Hoard is unaffected.

## Specification Impact
Records 25 undead per existing zone, placed only at clear positions, and a player-focused swarm.

## Risks
Spawn count depends on available clear positions in the arena zones.

## Follow-up Items
None. The Hammer Demo, Hoard arena, and awareness regressions passed.
