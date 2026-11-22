# Smooth NPC and enemy wandering

## Date
2026-11-22

## Git Commit

3bff469e51ea1853430c441223b8f80c138e91cc

## Change Summary
Replaced frequent cardinal wander changes with random pauses and smoothly turning walks for NPCs and enemies. Acquisition preempts wandering; returning from investigation starts a fresh pause.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/ActorAi.java`
- `core/src/main/java/io/mindspice/lute/engine/ai/decisiongraph/Action.java`
- `core/src/test/java/io/mindspice/lute/engine/systems/AwarenessSystemTest.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/MapTravelTest.java`
- `.internal-dev/specifications/composite-characters.md`
- `.internal-dev/specifications/decisions.md`

## Behavioral Impact
Wander alternates 0.35–1.2 second pauses with 1–2 second walks that turn by up to 30 degrees. Pauses retain facing. No pathfinding, leash, or asset changes were added.

## Specification Impact
Updated the character behavior and decision records to describe wander timing, turns, preemption, and fresh pauses after investigation.

## Risks
Randomized tests do not exhaust all movement sequences. Direct wandering does not navigate around obstacles or constrain distance from spawn. Subjective visual quality remains unverified.

## Follow-up Items
- Perform a subjective manual playtest.
- Validation passed: `./gradlew --offline -I /tmp/lute-wander-init.G56sH3.gradle :core:check` and `./gradlew --offline -I /tmp/lute-wander-init.G56sH3.gradle :lwjgl3:mapTravelTest`. The desktop test passed 222 real GL and Box2D checks, including physical wandering before engagement, pursuit, and melee. The temporary init script redirected util build output to preserve unrelated tracked files.
