# Point-based player spawning

## Topic
Tile-independent player creation from a classed Tiled point.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/PlayerCharProps.java`
- `assets/game.tiled-project`

## Key Takeaways
- TMX can omit inherited project-class defaults. The point parser supplies matching defaults; keyFrames must be positive (the player uses eight).
- The map loader already flips point Y into libGDX coordinates. The factory receives pixel coordinates and applies UNIT_SCALE once; do not flip Y again.
- RenderSystem treats Transform.position as the sprite's bottom-left corner. Center point-spawned sprites by subtracting half the world-space sprite size before creating both Transform and the physics body; changing only the graphic position would desynchronize physics.
- The factory returns an uninserted entity. TiledSystem inserts it exactly once; Ashley family visibility follows the current system update boundary.
- AnimationPlayer is not a unique player marker: animated trees also have it. Runtime validation identifies player sprites by Animation2D atlasKey.
- A temporary desktop harness verified two player sprites, one controller and camera target, movement/release, walk-to-idle and same-facing restart, old-player immobility, collider footprint parity, boundary collision, camera following, and invalid asset/frame rejection.

## Project Relevance
The old tile player remains visible with per-placement controller and camera flags disabled. The point player uses an explicit dynamic feet collider without resolving a Tiled tile.

## Open Questions
Map replacement/player persistence is outside this startup-spawning change.
