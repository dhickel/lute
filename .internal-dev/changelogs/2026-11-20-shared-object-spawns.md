## Date

2026-11-20

## Git Commit

0f1eb9341f2fa2ab8fd36f1ece69610650a1015a

## Change Summary

Added map-authored OBJECT and ITEM spawning using a shared object atlas and required object-definition catalogue. Objects can be static or animated, with optional solid collision ellipses. Existing NPC and ENEMY markers prefer `spawnKey` while retaining legacy `actorId` fallback.

## Files

- `core/src/main/`: startup asset registration, object definitions, trigger parsing, entity creation, fixture geometry, and static-body interpolation.
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`: shared atlas generation.
- `core/src/test/`, `lwjgl3/src/test/`, and `util/src/test/`: data, trigger, spawning, packing, combat, and real-GL map coverage.
- `docs/asset-service.md`: object authoring, local campfire provisioning, and regeneration instructions.

## Behavioral Impact

OBJECT and ITEM points require a `spawnKey` and facing; spawn areas remain data-only. Object definitions require explicitly nullable `animation` and `collision_bounds` fields. The sprite and optional collision rotate around the spawn point. The shared atlas remains loaded when a map is unloaded.

## Specification Impact

Updated actor-key and map-spawning contracts in `composite-characters.md` and `decisions.md`.

## Risks

- Fresh checkouts and existing local asset trees require the documented ignored inputs and migration before asset-dependent generation or execution.
- Existing ITEM markers now require a key and facing.

## Follow-up Items

- Provision the documented local artwork, data, Tiled project, and map on other machines, then regenerate runtime assets.
- Manual gameplay walkthrough was not run. Asset generation and all eight focused regression tasks passed, including 111 real GL/Box2D map checks and 190 real GL lifecycle checks.
