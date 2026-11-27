# World-time settings and lifecycle tests

## Topic
Local day-length configuration and test expectations.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/WorldTimeSystem.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/GameScreenLifecycleTest.java`
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`

## Key Takeaways
The game reads `assets/game.settings.json`; the asset generator copies its source from `raw_assets/game.settings.json`. Both files are ignored by Git. Edit the raw setting, run `./generate-runtime-assets.sh`, and restart to apply it. The generator protects owned outputs: do not directly modify the generated copy before regeneration, since that causes an ownership conflict. Day length is simulation minutes per full in-game day. Lifecycle assertions must use the configured rate rather than assume the 24-minute default. For new game data and settings, the project [AGENTS.md](../../AGENTS.md) requires load-time data, updates to both ignored asset trees and fixtures in the same change, and no missing-data fallback. The existing optional day-length contract is unchanged.

## Project Relevance
With `dayLengthMinutes: 60`, a render update clamped to 1/30 simulation second advances the clock by 0.8 in-game seconds, not the 2 seconds seen at the default rate.

## Open Questions
None for this configuration.
