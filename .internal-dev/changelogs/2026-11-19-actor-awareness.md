# Actor awareness and enemy sight

## Date
2026-11-19

## Git Commit

7560b947185010e6f84fc564f2eac572ea2e5a01

## Change Summary
Add map-actor awareness and enemy line-of-sight facing using the decision graph. Add optional actor-radius data and focused lifecycle and physics coverage.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/Actor.java`, `core/src/main/java/io/mindspice/lute/engine/data/json/ActorsData.java`, and `core/src/main/java/io/mindspice/lute/engine/systems/AwarenessSystem.java`: roles, configuration, range queries, and enemy facing.
- `core/src/main/java/io/mindspice/lute/engine/ai/decisiongraph/FocusState.java`, `data/json/DataLoader.java`, `data/json/GameData.java`, `screen/GameScreen.java`, `systems/EntityFactory.java`, and `systems/TiledSystem.java`: graph state, data loading, scheduling, and map actor construction.
- `core/build.gradle`, core awareness, data loader, map sensor and combat tests, desktop map travel and screen lifecycle tests, and `lwjgl3/src/test/resources/fixtures/actors.json`: test integration and fixtures.
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeGameTest.java`: identify the feet, hurtbox, and awareness fixtures by their properties and verify the contact-free circle is centered at the physical feet.
- `.internal-dev/specifications/composite-characters.md`, `decisions.md`, and `horizon-ideas.md`: awareness contract, spawn correction, and later perceived item types.

## Behavioral Impact
NPCs and enemies own query-only circles; players are detectable without one. Enemies turn toward a visible in-range player but do not move or attack. Missing actor data uses defaults; invalid present data is rejected.

## Specification Impact
The composite character and durable decision contracts describe awareness and current map spawning; additional perceived item types remain a horizon idea.

## Risks
Line of sight depends on solid Box2D fixtures and the previous completed physics/teleport snapshot.

## Validation
Final offline `check` and `:core:check` passed with `LUTE_FIXTURE=/tmp/lute-awareness.Y5eBi0` and `-I /tmp/lute-awareness-final-fixtures.gradle`. Desktop GL lifecycle (190 checks), travel (102 checks), and composite game (11,854 checks) also passed. `git diff --check` passed at final verification.

## Follow-up Items
Additional perceived item types remain outside this change.
