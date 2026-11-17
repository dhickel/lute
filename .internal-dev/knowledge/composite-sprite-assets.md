# Composite Sprite Assets

## Topic
Raw sprite inventory and composition rendering constraints.

## Source References
- `raw_assets/sprites/character`
- `raw_assets/sprites/equipment/basic/{head,body,hands,feet,accessories}`
- `raw_assets/sprites/weapons/{melee/basic_sword,ranged/basic_bow}`
- `../specifications/composite-characters.md`
- `../specifications/decisions.md`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/{CharacterCompositor,RenderSystem,RenderState}.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/{CompositeRenderTest,CompositeGameTest}.java`

## Key Takeaways
The current raw inventory contains 1,472 PNGs: 1,280 supported frames and 192 additional accessories export frames preserved but skipped by generation. All source canvases are 128×128. The base and each supported basic equipment slot have 192 frames across six actions: death, idle, jab, shot, slash, and walk, each with four directions and eight frames. Sword and bow each have 160 frames: sword lacks shot, bow lacks slash, and both lack cast. The 160 cast files were removed; cast is not a runtime requirement.

The user corrected the assumption that a 128×128 canvas is a 128×128 logical character. Every source canvas is 128×128, with a centered 64×64 logical area at [32, 96] on each axis. Render the entire canvas at pixel scale with a -32 pixel offset around the logical transform; do not shrink to 64×64 or trim transparent padding. Spawn and weapon anchoring remain logical, and logical size comes from configuration rather than canvas dimensions. Test full-scale source rendering, padding and visible overhang against the naive shrink, as well as layer alignment across pages.

The accepted initial player bounds separate a 24×10 feet collision ellipse from a 32×50 persistent damage-sensor ellipse. Check that map triggers use feet rather than the hurtbox, attack sensors hit the hurtbox rather than solids for explicit hurtbox targets, and generic full-sprite fallback remains intact. Focused automated validation passed; these fitted bounds still need live user review. See the [canvas and hitboxes validation report](../reviews/2026-11-16-character-canvas-hitboxes-validation.md) for final gate evidence.

Character atlas region names are flat, such as `idle_down` and `slash_up`. Equipment sources are set-first at `equipment/<set>/<slot>/`, but generation stages them by slot and set so the four separate equipment atlases retain item-prefixed regions such as `basic/shot_up`. The basic set requires all four slots; nested `pack.json` overrides and unknown slots are rejected, except that an `accessories` folder under a set is reported and skipped for now. Its source bytes remain untouched, and it produces no atlas or equipable layer. Check both source preservation and absence of an accessories atlas when validating exports; retain tests that reject other unknown slots. Optional `equipment/pack.json` settings apply to all four equipment atlases. Weapon regions include their category and item prefix, such as `ranged/basic_bow/jab_up`. Cast is not required by runtime layer validation.

Required primitive JSON fields must use `required = true`: zero is a legitimate bounds coordinate, but an omitted field must not silently become zero.

OpenGL object IDs may be reused after disposal. A disposal test should inspect the disposed texture object rather than rely on `glIsTexture(oldId)`, which can refer to a newly allocated texture.

`TextureAtlas.findRegions` returns copied `AtlasRegion` objects. Tests that change a source region or remove a frame to exercise validation must mutate entries from `atlas.getRegions()`, not the copies returned by `findRegions`. `CombatSystemTest` uses `sourceFrame` and `getRegions()` for geometry, missing-frame, and replacement-atlas checks.

Required sprite input validation must include the top-level `sprites` tree: an absent tree cannot bypass the checks for required character, equipment, and weapon directories. Generator test fixtures need minimal valid sprite inputs even when testing other generation behavior. Exercise missing and empty required directories against both a fresh destination and an existing publication: failure must publish nothing fresh and preserve existing owned and unowned bytes, inventory, and manifest; restoring valid input must succeed. The focused `:util:texturePackerToolTest` and normal generator passed with the existing 29-output inventory unchanged; root independent gates and full integration have not yet run.

Raw sprites are source assets; generated atlases and their texture pages are runtime build outputs. Preserve that ownership distinction when regenerating or cleaning outputs. The ownership guard does not accept modified owned outputs through adopt-identical. When a user explicitly authorizes preserving live generated-data edits, back up exact bytes, pause editors and confirm the edits are stable, verify the old owned baseline against the manifest, and copy the approved edits into source. Temporarily restore only the affected runtime output to its verified baseline, run the normal generator, and verify the regenerated output and ownership hashes. Do not weaken the guard or forge hashes.

Per-attack sound tests must distinguish logical ownership from filenames: assign different paths to attack slots to verify selection, and also test shared paths with distinct `weapon/<id>/<slot>/<cue>` IDs. Matching WAV filenames alone cannot prove the right slot was selected; different IDs sharing a path own independent loads.

The compositor batches by actual texture page, not merely atlas identity: an atlas can span pages. It composes each character into a separate offscreen cell with a transparent gutter, then sorts the completed character with world objects. Composition uses straight-alpha sources and produces premultiplied alpha; the world pass uses premultiplied blending and tint for those cells. Tests cover translucent edges, overlapping cells, page changes, multiple targets, gutters and target growth.

The former head-before-hands order let gloves cover the helmet. The corrected back-to-front order puts hands before head in every direction, retaining weapon-first for UP and weapon-last for DOWN, LEFT, and RIGHT. Raw supersampled 128×128 alpha-mask inspection and visual contact samples found dense UP overlaps and smaller RIGHT/DOWN boundary overlaps; overlap alone is a mechanical observation, not proof that a pixel is visually wrong. This fixed ordering does not establish correctness for every future equipment asset.

A manually composed reference of actual frames and a synthetic top-pixel test both repeated the old ordering and therefore passed despite the visible defect. Keep an independent real-GPU assertion for opaque helmet pixels overlapping nontransparent gloves, with nonvacuous UP jab, slash, shot, and death examples. The pre-fix regression failed on slash frame 2 at (61, 58). Also test isolated two-layer markers for every pair, direction, frame, and supported armed and unarmed action using semantic priority rules independent of the production order array. Retain mixed-facing, paging, and framebuffer resource checks; matching a reference that duplicates production assumptions is not sufficient.

Game GL tests must initialize the real `GameCore` and its `EventSystem`: `EventListener` helpers use the global engine, not a separately constructed test engine. The gameplay test starts the real loading and map path before its synthetic renderer checks.

Rendering reads equipment and weapon from `Loadout`, not `Combat`. When the death-pose fixture was made Combat-free, the first GL run failed because `worldFrame` still unconditionally copied its absent `Combat` component to another entity. Remove stale optional-component copies rather than adding dummy combat state that hides a rendering dependency. The final root rerun passed the GL and filtered project checks; the unfiltered check retained the separately tracked input failure.

`RenderSystem` requires an idle `Batch` and restores its documented batch and touched GL state. Raw custom GL callers must rebind their own vertex inputs after the renderer; arbitrary external vertex-pointer restoration is not guaranteed. GL3 is not required: VAO and separate read/draw framebuffer restoration are conditional when available. This describes the current engine boundary, not a new feature.

Sorted path-order staging of pack inputs is necessary for reproducible atlas output. Current outputs have 29 ownership hashes: character and each equipment atlas use one page, and weapons use two. For repeat-generation checks, compare the owned inventory and manifest, or account separately for ignored, unowned `assets/assets.txt`: `processResources` runs `generateAssetList` and can update this catalog after page filenames change. A change to that file alone is not evidence of generator nondeterminism. The desktop test source set uses JavaExec tests rather than discovered JUnit tests, so `lwjgl3:test` permits no discovered tests; run `:lwjgl3:compositeRenderTest` for the actual GL integration checks.

Sprite packing isolates source folders on atlas pages, but does not implement animation grouping: an oversized folder can still span pages and split a sequence. The current 2048×2048 weapons pages contain sword and bow separately; verify page membership and every current animation group rather than assuming isolation implies sequence integrity. Map object packing still requires `combineSubdirectories=true`; sprites reject it.

Both asset trees are ignored and must be externally provisioned in a fresh checkout. The root generator creates `assets/` before Gradle because `generateAssetList` requires it; missing `raw_assets/` must fail before creating it. When changing branches, an older branch that tracks these paths can overwrite local ignored files. Preserve and hash the local trees, and use a separate worktree for integration when needed; verify hashes before and after any branch transition. Ignoring paths is not a substitute for runtime ownership checks or a Git history purge.

## Project Relevance
The inventory observations describe the corrected raw files. Phases 1–4 and their earlier final integration validation passed before the canvas and bounds correction; they do not validate that correction. The integration report records 9,917 gameplay checks, 384 framebuffer comparisons across two real weapon pages, and 1,402 synthetic checks. The unfiltered check retains the separately tracked controller movement-event failure.

Continue to test multipage atlases, overlapping characters and world objects at different depths, transparent edges, synchronized layer frames, and switching between sword, bow, and no weapon. Test missing cast assets rather than assuming every action exists for every weapon.

## Open Questions
None identified for this asset inventory.
