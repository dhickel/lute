# Composite Characters

## Purpose
A character consists of a base sprite and equipped head, body, hands, feet, and weapon layers. Phases 1–4 are implemented and accepted by [final integration validation](../reviews/2026-11-16-composite-character-integration.md).

## Intended Contract
### Composition and loadout
The default loadout uses the basic item in all four equipment slots and `basic_sword`. Loadout data and a runtime equipment representation support equipment switching. Inventory and UI are not part of this work.

Use one shared animation clock to align the facing, action, and frame of every visible layer. A character without a weapon renders without the weapon layer.

### Assets
Build sprite assets from `raw_assets/sprites`: the character directory is flat; equipment is organized in its respective head, body, hands, feet, and weapons folders. Generate separate character, head, body, hands, feet, and common weapons atlases under `assets/atlases`. Atlases may have multiple texture pages. Weapon region names retain their `melee` or `ranged` prefix in the common weapons atlas.

Generated runtime resources are build outputs; raw assets are the source. Map-owned environmental atlases remain separate.

### Attacks
Space triggers the primary attack: sword slash or bow shot. Shift triggers the secondary jab. Without a weapon, both keys trigger a hand jab.

Weapon `attack_stats` contains separate `primary` and `secondary` records, each with animation, bounds, stats, collision type, and its own `sounds.attack` and `sounds.hit` paths. Sound IDs are `weapon/<id>/<primary|secondary>/<attack|hit>`. Initial loading includes both sounds for every configured attack, including unarmed. Existing WAV files are approved for reuse; hit sounds are loaded but not played. Keep projectile collision data for future bow execution. A bow shot plays its animation but creates no projectile and deals no damage.

Melee attacks retain animation-driven hit timing and sensor lifetime. Validate authored data at its loading boundary and animation and geometry constraints where data meets loaded assets.

## Constraints
The composition design is reusable for future NPCs. Enemies remain full sprites with weapon variants. This work does not add NPC or enemy spawning.

There is no magic requirement. `cast` is not a runtime dependency and may be absent from future atlases.

## Decisions
Compose visible characters into distinct offscreen cells. Batch atlas layers across characters by their actual texture page while preserving the intended layer order within each cell. Render each completed character as one sprite, world-depth sorted with ordinary objects; do not independently world-sort its weapon or other layers.

Use correct premultiplied-alpha handling so transparent edges remain correct during both composition and final rendering.

## Validation
Verify asset generation and multipage region resolution, default and switched loadouts, aligned animation in all directions, both attack keys with sword, bow, and no weapon, and bow's lack of projectile and damage. Verify overlapping characters and ordinary objects at different depths, including weapon overlap and transparent edges. Exercise resource loading and disposal. The final integration report records 9,917 gameplay checks, 384 framebuffer comparisons across two weapon pages, 1,402 synthetic real-GL checks, and per-attack sound selection and ID validation. The unfiltered check fails only the separately tracked controller movement-event test; the check excluding that task passes.

## Open Questions
None needed to establish the accepted contract.
