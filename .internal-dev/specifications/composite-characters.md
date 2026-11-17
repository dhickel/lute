# Composite Characters

## Purpose
A character consists of a base sprite and equipped head, body, hands, feet, and weapon layers. Phases 1–4 are implemented and accepted by [final integration validation](../reviews/2026-11-16-composite-character-integration.md).

## Intended Contract
### Composition and loadout
The default loadout uses the basic item in all four equipment slots and `basic_sword`. Loadout data and a runtime equipment representation support equipment switching. Inventory and UI are not part of this work.

Use one shared animation clock to align the facing, action, and frame of every visible layer. A character without a weapon renders without the weapon layer.

### Assets
Build sprite assets from `raw_assets/sprites`: base character frames are flat in `character/`; equipment frames are organized as `equipment/<set>/<slot>/`, where slots are `head`, `body`, `hands`, and `feet`. The basic set requires all four nonempty slots. Gather sets by slot to generate separate head, body, hands, and feet atlases, preserving set prefixes in region names such as `basic/shot_up`. An optional `equipment/pack.json` supplies shared settings for these four atlases; reject nested packing overrides and unknown slots, except that `accessories` export folders are reported and skipped for now. Preserve their source files; do not pack an accessories atlas or add an equipable accessories layer. Weapons remain under `weapons/melee/basic_sword/` and `weapons/ranged/basic_bow/`. Generate separate character and common weapons atlases alongside the equipment atlases under `assets/atlases`. Atlases may have multiple texture pages. Weapon region names retain their `melee` or `ranged` prefix in the common weapons atlas.

Generated runtime resources are build outputs; raw assets are the source. Map-owned environmental atlases remain separate. The corrected raw base and each basic equipment set have 192 frames: death, idle, jab, shot, slash, and walk, each in four directions with eight frames. Sword and bow each have 160 frames; sword has no shot, bow has no slash, and neither has cast.

### Canvas and logical bounds
Each source sprite canvas is 128×128 pixels. The character's authored logical size is 64×64 pixels, centered at canvas coordinates [32, 96] on each axis. Render the full canvas at pixel scale, including transparent padding and overhang; do not crop it or shrink it to the logical size. Relative to the 64×64 transform, the canvas render offset is -32 pixels on each axis. Spawn placement and weapon anchors use the logical area. `logical_size` is explicitly configured, not inferred from the source canvas.

Player JSON defines `logical_size` as `{ "width": 64, "height": 64 }`, `collision_bounds` as `{ "x1": 20, "y1": 4, "x2": 44, "y2": 14 }`, and `hitbox_bounds` as `{ "x1": 16, "y1": 6, "x2": 48, "y2": 56 }`. Bounds are bottom-left-origin logical-local pixels and are converted to world units once. Both describe ellipses: the 24×10 physical feet fixture and a separate 32×50 damage-receiving hurtbox. Non-circular Box2D ellipses use the existing eight-vertex approximation. The persistent hurtbox is a sensor, not a movement blocker or map-trigger contributor. Attack sensors target it rather than solid fixtures for explicit hurtbox targets; generic full-sprite targets retain their existing fallback.

### Attacks
Space triggers the primary attack: sword slash or bow shot. Shift triggers the secondary jab. Without a weapon, both keys trigger a hand jab.

Weapon `attack_stats` contains separate `primary` and `secondary` records, each with animation, bounds, stats, collision type, and its own `sounds.attack` and `sounds.hit` paths. Sound IDs are `weapon/<id>/<primary|secondary>/<attack|hit>`. Initial loading includes both sounds for every configured attack, including unarmed. Existing WAV files are approved for reuse; hit sounds are loaded but not played. Keep projectile collision data for future bow execution. A bow shot plays its animation but creates no projectile and deals no damage.

Melee attacks retain animation-driven hit timing and sensor lifetime. Validate authored data at its loading boundary and animation and geometry constraints where data meets loaded assets.

## Constraints
The composition design is reusable for future NPCs. Enemies remain full sprites with weapon variants. This work does not add NPC or enemy spawning.

There is no magic requirement. `cast` is not a runtime dependency and is absent from the corrected raw inventory.

## Decisions
Compose visible characters into distinct offscreen cells. Within each cell, draw back to front: UP uses weapon, base, feet, body, hands, head; DOWN, LEFT, and RIGHT use base, feet, body, hands, head, weapon. Omit the weapon when unarmed. Batch atlas layers across characters by their actual texture page while preserving that order. Render each completed character as one sprite, world-depth sorted with ordinary objects; do not independently world-sort its weapon or other layers. This correction changes neither weapon placement in the order nor canvas, logical geometry, or assets.

Use correct premultiplied-alpha handling so transparent edges remain correct during both composition and final rendering.

## Validation
The canvas and bounds correction is an accepted contract. Focused automated validation passed; the user's live review of the initial fitted bounds is pending. See the [canvas and hitboxes validation report](../reviews/2026-11-16-character-canvas-hitboxes-validation.md) for final gate evidence. Historical integration evidence below predates this correction and does not establish its validation.

Verify full-scale padded rendering and overhang, logical placement and anchors, separate feet and hurtbox contacts, map-trigger exclusion, missing cast, asset generation and multipage region resolution, default and switched loadouts, aligned animation in all directions, both attack keys with sword, bow, and no weapon, and bow's lack of projectile and damage. Verify overlapping characters and ordinary objects at different depths, including weapon overlap and transparent edges. Exercise resource loading and disposal. The final integration report records 9,917 gameplay checks, 384 framebuffer comparisons across two weapon pages, 1,402 synthetic real-GL checks, and per-attack sound selection and ID validation. The unfiltered check fails only the separately tracked controller movement-event test; the check excluding that task passes.

## Open Questions
None needed to establish the accepted contract.
