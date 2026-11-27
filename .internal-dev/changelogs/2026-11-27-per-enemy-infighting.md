## Date
2026-11-27

## Git Commit

ce3c7a30b0d1d0358b983b640ba5e6aeb3f4bc71

## Change Summary
Moved the infighting percentage from a game-wide setting to required per-enemy `infight_chance`. Added an explicit session-level code override.

## Files
- `MapActorData.java`, `ActorAi.java`, `EntityFactory.java`, `AwarenessSystem.java`, `GameScreen.java`, and `GameConsts.java`
- Enemy schema, AI, and integration test fixtures
- Composite-character and durable-decision specifications and the asset-authoring guide

## Behavioral Impact
The victim's authored percentage controls retaliation. All nine local definitions use 30. A null override uses authored values; a 0–100 override replaces them. Damage and existing player and NPC retaliation remain unchanged.

## Specification Impact
Updated per-enemy authoring, migration, and code override contracts.

## Risks
Other provisioned raw asset packs need the new per-enemy field before loading.

## Follow-up Items
Regenerate runtime assets after migrating other local asset packs. Focused schema, awareness, combat, sensors, world-time, and Hoard arena tests passed.
