# Point-based player spawning

## Topic
Tile-independent player creation from a classed Tiled point.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/PlayerCharProps.java`
- `assets/game.tiled-project`

## Key Takeaways
- TMX can omit inherited project-class defaults, but Asset.Map configures projectFilePath and libGDX populates those values when loading the map. Parser fallbacks apply only when a runtime property is absent, not when its inherited value is zero. The spawn point now explicitly defines keyFrames=8, overriding even a zero project-class default; its obsolete tile definition's value does not configure the spawn point.
- A zero PlayerCharacter class default reproduced the keyFrames crash while all five trees correctly loaded their explicit value of ten. The first fix changed only the class default; that value returned to zero in a later working copy and the crash recurred. An explicit per-point value fixes the saved instance without relying on the mutable class default. The external writer of the recurring default change was not established. Regression checks now cover class defaults zero/eight with absent, zero, negative, and positive point values; explicit invalid overrides still fail.
- The map loader already flips point Y into libGDX coordinates. The factory receives pixel coordinates and applies UNIT_SCALE once; do not flip Y again.
- RenderSystem treats Transform.position as the sprite's bottom-left corner. Center point-spawned sprites by subtracting half the world-space sprite size before creating both Transform and the physics body; changing only the graphic position would desynchronize physics.
- The factory returns an uninserted entity. TiledSystem inserts it exactly once; Ashley family visibility follows the current system update boundary.
- AnimationPlayer is not a unique player marker: animated trees also have it. Runtime validation identifies player sprites by Animation2D atlasKey.
- A temporary desktop harness verified two player sprites, one controller and camera target, movement/release, walk-to-idle and same-facing restart, old-player immobility, collider footprint parity, boundary collision, camera following, and invalid asset/frame rejection.

## Project Relevance
The old tile player remains visible with per-placement controller and camera flags disabled. The point player uses an explicit dynamic feet collider without resolving a Tiled tile.

## Open Questions
Map replacement/player persistence is outside this startup-spawning change.
