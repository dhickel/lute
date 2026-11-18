# NPC Map Actor Fixtures

## Date
2026-11-18

## Git Commit

decfbc92b31a2ea1901de0621c368aa765470b33

## Change Summary
Add a static solid feet fixture, separate sensor hurtbox, and health to the map-owned basic NPC. Keep the enemy path unchanged. Document the PR branch workflow and NPC fixture contract.

## Files
- `EntityFactory.java`, `CompositeGameTest.java`, and `MapTravelTest.java`.
- `AGENTS.md`, `README.md`, `.internal-dev/specifications/composite-characters.md`, and `.internal-dev/knowledge/point-player-spawning.md`.

## Behavioral Impact
The basic NPC blocks movement at its feet and can receive damage through its hurtbox. It has no controller or AI. Named map travel replaces map-owned actors and their fixtures.

## Specification Impact
The composite character specification defines the NPC's centered, explicit bounds and fixture behavior independently of player JSON.

## Risks
The automated tests check fixture alignment, roles, and reconstruction without duplicates. NPC-specific live combat and visual fit were not manually tested.

## Follow-up Items
`./gradlew --offline check :lwjgl3:compositeRenderTest :lwjgl3:mapTravelTest` passed. Verify the fixture fit during normal gameplay.
