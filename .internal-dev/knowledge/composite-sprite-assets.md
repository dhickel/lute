# Composite Sprite Assets

## Topic
Raw sprite inventory and composition rendering constraints.

## Source References
- `raw_assets/sprites/character`
- `raw_assets/sprites/{head,body,hands,feet}/basic`
- `raw_assets/sprites/weapons/{melee/basic_sword,ranged/basic_bow}`
- `../specifications/composite-characters.md`
- `../specifications/decisions.md`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`

## Key Takeaways
The current raw base and basic equipment inventories contain seven action sets: cast, death, idle, jab, shot, slash, and walk. Each has four directions and eight frames per direction. The sword lacks shot and cast; the bow lacks slash and cast. Cast files exist today, but cast is not a runtime requirement and may disappear from future atlases.

Character atlas region names are flat, such as `idle_down` and `slash_up`. Equipment regions use an item prefix, such as `basic/shot_up`; weapon regions include their category and item prefix, such as `ranged/basic_bow/jab_up`. Cast is not required by runtime layer validation.

`TextureAtlas.findRegions` returns copied `AtlasRegion` objects. Tests that change a source region or remove a frame to exercise validation must mutate entries from `atlas.getRegions()`, not the copies returned by `findRegions`. `CombatSystemTest` uses `sourceFrame` and `getRegions()` for geometry, missing-frame, and replacement-atlas checks.

Raw sprites are source assets; generated atlases and their texture pages are runtime build outputs. Preserve that ownership distinction when regenerating or cleaning outputs.

The accepted design batches by the actual texture page, not merely by atlas identity: an atlas can span pages. A naive global painter pass over independent character layers can break overlap between characters and world objects. The selected design first composes each character into its own offscreen cell, then sorts the completed character with world objects. Correct premultiplied-alpha handling is necessary to avoid edge artifacts.

## Project Relevance
The inventory observations describe current raw files. Runtime loadout switching and layer validation are implemented but await independent phase validation. Offscreen composition, page batching, and alpha handling remain planned, not verified implementation.

Validation should include multipage atlases, overlapping characters and world objects at different depths, transparent edges, synchronized layer frames, and switching between sword, bow, and no weapon. Test missing cast assets rather than assuming every action exists for every weapon.

## Open Questions
None needed to establish the accepted contract; implementation-specific rendering and resource-management choices remain to be validated.
