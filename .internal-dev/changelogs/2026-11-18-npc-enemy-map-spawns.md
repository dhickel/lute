## Date

2026-11-18

## Git Commit

521b4207f57acf12238869463a0e5d75e9802b22

## Change Summary

Added map-owned NPC and enemy composite demonstrations using existing local `test_tiled` spawn points. Player and NPC base frames now share a prefixed character atlas; the baked-armored enemy uses a separate prefixed enemy atlas. Common equipment and weapon assets remain shared.

## Files

- `CharacterAssets.java`, `CharacterCompositor.java`, `EntityFactory.java`, `TiledSystem.java`, and `MapTriggers.java`: atlas registration, actor composition and validation, marker spawning and map ownership.
- `TexturePackerTool.java` and `TexturePackerToolTest.java`: required actor inputs, prefixed atlas packing, nested-settings rejection, and generation tests.
- Core and desktop tests: asset and marker expectations, real-asset composition, placement, depth, weapon switching, recreation, and disposal.
- `README.md`, `.internal-dev/specifications/composite-characters.md`, and `.internal-dev/knowledge/composite-sprite-assets.md`: updated setup, contracts, and asset guidance.

## Behavioral Impact

The basic NPC has basic body, hands and feet, no head item and no weapon. The enemy has baked armor, starts with a sword and can switch to a bow through `LoadoutSystem`. Neither actor has AI, dialogue, player control, combat, physics or camera ownership. Map replacement recreates actors and disposal removes them. Existing flat character-region consumers must use actor prefixes and regenerate assets.

## Specification Impact

The composite-character specification now describes actor-prefixed shared character and separate enemy atlases, actor loadouts, and map-owned marker spawning. Raw and generated assets remain local and ignored.

## Risks

Provisioning requires the updated local raw asset layout and typed map markers; missing or incompatible assets can block generation or actor creation. Automated generation, real-GL composition and travel checks mitigate regressions, but visual gameplay review remains outstanding.

## Follow-up Items

- Run the manual gameplay walkthrough at the NPC and enemy markers.
- Phase validation passed the full offline check and focused composite render, map travel and game-screen lifecycle tests. Two generation runs, obsolete intermediate output retirement and raw checksums were also verified.
