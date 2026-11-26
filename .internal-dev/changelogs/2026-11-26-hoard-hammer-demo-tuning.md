## Date
2026-11-26

## Git Commit

60fbd12ecac26902bba4d6d7b1ec6046027980ef

## Change Summary
Equipped the demo player with a hammer at launch, increased the opening swarm, and shortened the hammer lunge.

## Files
- `DredgeLunge.java`, `HoardSystem.java`, and `TiledSystem.java`
- `HoardArenaTest.java` and `HoardHammerDemoTest.java`
- `.internal-dev/specifications/decisions.md` and `.internal-dev/specifications/composite-characters.md`

## Behavioral Impact
The demo starts with `basic_hammer` equipped, retains all weapons, and attempts to spawn 48 undead at clear positions, 12 per existing zone. Player and Dredge lunges travel about 32 logical pixels unobstructed, preserving the existing hitbox window. Scored Hoard wave spawns and inventory are unchanged.

## Specification Impact
The Hoard arena and composite character attack contracts reflect the tuned behavior.

## Risks
The number of spawned undead depends on finding clear positions in each zone.

## Follow-up Items
No blockers. The Hoard arena and desktop demo regression tests passed, including 48 spawns on the current demo map.
