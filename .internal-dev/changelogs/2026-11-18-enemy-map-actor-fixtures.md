# Enemy Map Actor Fixtures

## Date
2026-11-18

## Git Commit

77c07004e1b1d6b4089f532a64062a4c9cad5a68

## Change Summary
The demo enemy now shares the basic NPC’s stationary solid feet, separate sensor hurtbox, and 100 health, using map-actor defaults independent of player JSON. Neither actor has AI or attacks.

## Files
- `EntityFactory.java`, `CompositeGameTest.java`, and `MapTravelTest.java`.
- `README.md`, `.internal-dev/specifications/composite-characters.md`, and `.internal-dev/knowledge/point-player-spawning.md`.

## Behavioral Impact
Enemy feet block movement, while the separate hurtbox can receive damage. Map travel recreates the enemy's body, both fixtures, and health.

## Specification Impact
The composite character specification now applies the shared map-actor fixture contract to both NPC and enemy spawns.

## Risks
Manual placement and actor-specific combat damage were not tested.

## Follow-up Items
`./gradlew --offline check :lwjgl3:compositeRenderTest :lwjgl3:mapTravelTest` passed, covering real-map fixture alignment, fixture roles, and body recreation without leaks.
