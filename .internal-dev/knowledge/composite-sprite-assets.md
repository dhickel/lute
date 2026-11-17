# Composite Sprite Assets

## Topic
Raw sprite inventory and composition rendering constraints.

## Source References
- `raw_assets/sprites/character`
- `raw_assets/sprites/equipment/basic/{head,body,hands,feet}`
- `raw_assets/sprites/weapons/{melee/basic_sword,ranged/basic_bow}`
- `../specifications/composite-characters.md`
- `../specifications/decisions.md`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/{CharacterCompositor,RenderSystem,RenderState}.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/{CompositeRenderTest,CompositeGameTest}.java`

## Key Takeaways
The corrected raw inventory contains 1280 PNGs. The base and each basic equipment set have 192 frames across six actions: death, idle, jab, shot, slash, and walk, each with four directions and eight frames. Sword and bow each have 160 frames: sword lacks shot, bow lacks slash, and both lack cast. The 160 cast files were removed; cast is not a runtime requirement.

The user corrected the assumption that a 128×128 canvas is a 128×128 logical character. Every source canvas is 128×128, with a centered 64×64 logical area at [32, 96] on each axis. Render the entire canvas at pixel scale with a -32 pixel offset around the logical transform; do not shrink to 64×64 or trim transparent padding. Spawn and weapon anchoring remain logical, and logical size comes from configuration rather than canvas dimensions. Test full-scale source rendering, padding and visible overhang against the naive shrink, as well as layer alignment across pages.

The accepted initial player bounds separate a 24×10 feet collision ellipse from a 32×50 persistent damage-sensor ellipse. Check that map triggers use feet rather than the hurtbox, attack sensors hit the hurtbox rather than solids for explicit hurtbox targets, and generic full-sprite fallback remains intact. Focused automated validation passed; these fitted bounds still need live user review. See the [canvas and hitboxes validation report](../reviews/2026-11-16-character-canvas-hitboxes-validation.md) for final gate evidence.

Character atlas region names are flat, such as `idle_down` and `slash_up`. Equipment sources are set-first at `equipment/<set>/<slot>/`, but generation stages them by slot and set so the four separate equipment atlases retain item-prefixed regions such as `basic/shot_up`. The basic set requires all four slots; unknown slots and nested `pack.json` overrides are rejected. Optional `equipment/pack.json` settings apply to all four equipment atlases. Weapon regions include their category and item prefix, such as `ranged/basic_bow/jab_up`. Cast is not required by runtime layer validation.

Required primitive JSON fields must use `required = true`: zero is a legitimate bounds coordinate, but an omitted field must not silently become zero.

OpenGL object IDs may be reused after disposal. A disposal test should inspect the disposed texture object rather than rely on `glIsTexture(oldId)`, which can refer to a newly allocated texture.

`TextureAtlas.findRegions` returns copied `AtlasRegion` objects. Tests that change a source region or remove a frame to exercise validation must mutate entries from `atlas.getRegions()`, not the copies returned by `findRegions`. `CombatSystemTest` uses `sourceFrame` and `getRegions()` for geometry, missing-frame, and replacement-atlas checks.

Raw sprites are source assets; generated atlases and their texture pages are runtime build outputs. Preserve that ownership distinction when regenerating or cleaning outputs. The ownership guard does not accept modified owned outputs through adopt-identical. When a user explicitly authorizes preserving live generated-data edits, back up exact bytes, pause editors and confirm the edits are stable, verify the old owned baseline against the manifest, and copy the approved edits into source. Temporarily restore only the affected runtime output to its verified baseline, run the normal generator, and verify the regenerated output and ownership hashes. Do not weaken the guard or forge hashes.

Per-attack sound tests must distinguish logical ownership from filenames: assign different paths to attack slots to verify selection, and also test shared paths with distinct `weapon/<id>/<slot>/<cue>` IDs. Matching WAV filenames alone cannot prove the right slot was selected; different IDs sharing a path own independent loads.

The compositor batches by actual texture page, not merely atlas identity: an atlas can span pages. It composes each character into a separate offscreen cell with a transparent gutter, then sorts the completed character with world objects. Composition uses straight-alpha sources and produces premultiplied alpha; the world pass uses premultiplied blending and tint for those cells. Tests cover translucent edges, overlapping cells, page changes, multiple targets, gutters and target growth.

Game GL tests must initialize the real `GameCore` and its `EventSystem`: `EventListener` helpers use the global engine, not a separately constructed test engine. The gameplay test starts the real loading and map path before its synthetic renderer checks.

`RenderSystem` requires an idle `Batch` and restores its documented batch and touched GL state. Raw custom GL callers must rebind their own vertex inputs after the renderer; arbitrary external vertex-pointer restoration is not guaranteed. GL3 is not required: VAO and separate read/draw framebuffer restoration are conditional when available. This describes the current engine boundary, not a new feature.

Sorted path-order staging of pack inputs is necessary for reproducible atlas output; the earlier [integration report](../reviews/2026-11-16-composite-character-integration.md) recorded 29 ownership hashes for the prior assets. The current outputs have 49 ownership hashes, with seven weapon pages and four pages for each other character layer. The desktop test source set uses JavaExec tests rather than discovered JUnit tests, so `lwjgl3:test` permits no discovered tests; run `:lwjgl3:compositeRenderTest` for the actual GL integration checks.

## Project Relevance
The inventory observations describe the corrected raw files. Phases 1–4 and their earlier final integration validation passed before the canvas and bounds correction; they do not validate that correction. The integration report records 9,917 gameplay checks, 384 framebuffer comparisons across two real weapon pages, and 1,402 synthetic checks. The unfiltered check retains the separately tracked controller movement-event failure.

Continue to test multipage atlases, overlapping characters and world objects at different depths, transparent edges, synchronized layer frames, and switching between sword, bow, and no weapon. Test missing cast assets rather than assuming every action exists for every weapon.

## Open Questions
None identified for this asset inventory.
