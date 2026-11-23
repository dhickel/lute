# Actor death, respawn and bow retaliation

## Date
2026-11-23

## Git Commit

70f8ce4a749fadb448224863214107589db0d451

## Change Summary
Add death playback, corpse removal, player respawn, independently randomized wander and wait periods, and bow retaliation gated by sight and range.

## Files
- `DeathSystem.java`, `AnimationState.java`, and `Animation2D.java`: play death once and track the final pose.
- `TiledSystem.java`, `GameScreen.java`, and `MapSensorSystem.java`: retire corpses, respawn the player and clear stale interactions.
- `ActorAi.java`, the decision graph, `AwarenessSystem.java`, `ArrowFlight.java`, and `CombatSystem.java`: sample wander and wait periods; check impact-time visibility and guard attacks.
- Core awareness and combat tests and desktop map travel tests: cover the lifecycle and retaliation.

## Behavioral Impact
Each actor plays the death animation once, holds the final pose for five simulation seconds, and disappears. The player returns with full HP and the same loadout at the map's player point or named arrival without resetting the map survivors or clock. NPCs and enemies independently wander and wait for one to three seconds at a time. When damaged by an arrow, a surviving victim can pursue the actual living shooter if visible within three times its detection radius. Cover blocks sight and actors cannot attack blindly.

## Specification Impact
`composite-characters.md` specifies the lifecycle and retaliation; the historical awareness decision now points to the revised contract.

## Risks
Desktop graphics validation was on Java 25 and Mesa. No manual playtest or other GPU validation was run.

## Follow-up Items
Provision the ignored raw test fixture if absent. Offline full `check` and all four desktop tasks passed, with `assetServiceTest` redirected to a temporary copy containing the missing `idle_up_02.png`; the same missing-fixture failure reproduced at clean HEAD without the redirect.
