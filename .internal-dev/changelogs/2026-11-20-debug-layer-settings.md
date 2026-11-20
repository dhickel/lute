# Independent debug layer settings

## Date
2026-11-20

## Git Commit

8c5ad615e6547ea25649801874a746fa4079c2c5

## Change Summary
Replace the F3 lighting-debug cycle with four independently configurable debug layers. N continues to toggle night as before.

## Files
- `core/src/main/java/io/mindspice/lute/engine/singletons/GameConsts.java`, `screen/GameScreen.java`, and `systems/RenderSystem.java`: validate and apply debug-layer settings.
- `core/src/main/java/io/mindspice/lute/engine/input/InputAction.java` and `assets/input.setting.json`: remove the F3 cycle action and binding.
- Core, desktop GL, and generator test fixtures: cover required settings and concurrent layers.
- `README.md`, `docs/input-system.md`, and `.internal-dev/specifications/decisions.md`: document authoring, behavior, and migration.
- Ignored `raw_assets/game.settings.json` and generated `assets/game.settings.json`: local sample settings and generated runtime copy.

## Behavioral Impact
`debugLayers` requires boolean `physics`, `triggers`, `rawLightMap`, and `lightingOutlines`. Local sample values enable physics and triggers and disable raw lightmap and lighting outlines. Layers can appear together. The raw lightmap replaces world art only at night; lighting outlines appear by day or night. Edit raw settings, regenerate, and restart; F3 no longer cycles views.

## Specification Impact
The lighting debug decision now specifies independent required settings, their concurrent rendering, and the restart workflow.

## Risks
Unmigrated settings fail startup. Local ignored settings must be migrated on each installation. Automated GL tests do not establish a manual visual result.

## Follow-up Items
Manual visual validation of this follow-up was not performed. Root generation and parity and full `check` plus the four GL tasks passed; evidence is in `/tmp/lute-jog.jg2c0W/debug-settings-regen.log` and `/tmp/lute-jog.jg2c0W/debug-settings-root.log`.
