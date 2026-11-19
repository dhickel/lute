# World-time settings and lifecycle tests

## Topic
Local day-length configuration and test expectations.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/WorldTimeSystem.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/GameScreenLifecycleTest.java`
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`

## Key Takeaways
The game reads `assets/game.settings.json`; the asset generator copies its source from `raw_assets/game.settings.json`. Both files are ignored by Git. To apply a local setting now and keep it after regeneration, update both. Day length is simulation minutes per full in-game day. Lifecycle assertions must use the configured rate rather than assume the 24-minute default.

## Project Relevance
With `dayLengthMinutes: 60`, a render update clamped to 1/30 simulation second advances the clock by 0.8 in-game seconds, not the 2 seconds seen at the default rate.

## Open Questions
None for this configuration.
