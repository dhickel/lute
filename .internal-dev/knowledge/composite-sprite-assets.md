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
- `core/src/main/java/io/mindspice/lute/engine/systems/{CharacterCompositor,RenderSystem,RenderState}.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/{CompositeRenderTest,CompositeGameTest}.java`

## Key Takeaways
The current raw base and basic equipment inventories contain seven action sets: cast, death, idle, jab, shot, slash, and walk. Each has four directions and eight frames per direction. The sword lacks shot and cast; the bow lacks slash and cast. Cast files exist today, but cast is not a runtime requirement and may disappear from future atlases.

Character atlas region names are flat, such as `idle_down` and `slash_up`. Equipment regions use an item prefix, such as `basic/shot_up`; weapon regions include their category and item prefix, such as `ranged/basic_bow/jab_up`. Cast is not required by runtime layer validation.

`TextureAtlas.findRegions` returns copied `AtlasRegion` objects. Tests that change a source region or remove a frame to exercise validation must mutate entries from `atlas.getRegions()`, not the copies returned by `findRegions`. `CombatSystemTest` uses `sourceFrame` and `getRegions()` for geometry, missing-frame, and replacement-atlas checks.

Raw sprites are source assets; generated atlases and their texture pages are runtime build outputs. Preserve that ownership distinction when regenerating or cleaning outputs.

The compositor batches by actual texture page, not merely atlas identity: an atlas can span pages. It composes each character into a separate offscreen cell with a transparent gutter, then sorts the completed character with world objects. Composition uses straight-alpha sources and produces premultiplied alpha; the world pass uses premultiplied blending and tint for those cells. Tests cover translucent edges, overlapping cells, page changes, multiple targets, gutters and target growth.

Game GL tests must initialize the real `GameCore` and its `EventSystem`: `EventListener` helpers use the global engine, not a separately constructed test engine. The gameplay test starts the real loading and map path before its synthetic renderer checks.

`RenderSystem` requires an idle `Batch` and restores its documented batch and touched GL state. Raw custom GL callers must rebind their own vertex inputs after the renderer; arbitrary external vertex-pointer restoration is not guaranteed. GL3 is not required: VAO and separate read/draw framebuffer restoration are conditional when available. This describes the current engine boundary, not a new feature.

## Project Relevance
The inventory observations describe current raw files. Phases 1, 2, and 3 are implemented; final integration validation pending. Senior focused real-GL evidence reports 9,917 gameplay checks, 384 framebuffer comparisons across two real weapon pages, and 1,402 synthetic checks. The root inspected `/tmp/lute-phase3-characters.png` and reported it correct; independent final validation is still pending.

Continue to test multipage atlases, overlapping characters and world objects at different depths, transparent edges, synchronized layer frames, and switching between sword, bow, and no weapon. Test missing cast assets rather than assuming every action exists for every weapon.

## Open Questions
Independent final integration validation remains pending.
