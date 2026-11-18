# Point-based player spawning

## Topic
Tile-independent player creation from a classed Tiled point.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- [Composite character contract](../specifications/composite-characters.md)
- `core/src/main/java/io/mindspice/lute/engine/tiled/MapTriggers.java`
- `core/src/test/java/io/mindspice/lute/engine/tiled/TiledAnimationPropertiesTest.java`
- `raw_assets/data/player.json` and `raw_assets/maps/test_tiled/test_tiled.tmx`

## Key Takeaways
- `MapTriggers` parses named `SpawnPoint` objects in trigger layers and permits at most one `PLAYER` point. `TiledSystem` passes its position to `EntityFactory.getPlayerEntity` and inserts the returned entity once.
- The map loader supplies the point in libGDX coordinates. The factory scales the pixel position once and subtracts half the world-space `logical_size` from each axis. This centers the logical frame on the point; the physics body uses the same bottom-left position.
- Player animation, logical size, collision and hitbox bounds, and initial loadout come from player data, not the spawn point. The factory creates a dynamic body with a collision ellipse and a sensor hitbox, plus the player controller and camera target. The current property test requires the spawn point to have neither legacy `keyFrames` nor `atlasAsset`.
- Named map travel, even to the current map ID, stages a fresh runtime and accepts a named `PLAYER` or `TELEPORT` point. `THIS` instead uses the existing player and a `TELEPORT` point. Failed preparation leaves the active runtime intact.
- `TiledSystem` unregisters map-owned controllers and removes map entities on replacement. Session-owned live health, loadout, and facing components attach to the new player; combat and input are fresh. Hide/show retains the session and its current map entry, while a new session starts from JSON defaults.
- Map-owned basic NPCs and demo enemies share a static solid feet ellipse and separate sensor hurtbox on the centered logical transform, not a player controller. Their explicit 24×10 and 32×50 bounds match the initial player footprints but do not depend on player JSON. Each has 100 health and can take damage, but has no AI or attacks.
- Validate both actors' fixture placement, static collision, sensor-only damage and absence of a controller. Exercise map replacement and named travel away and back: old map-owned fixtures must be removed and both actors rebuilt without duplicates. Failed travel preparation must leave the active runtime intact. For a live check, provision the raw assets, regenerate them, and inspect collision and damage at both points before and after travel. These are validation steps, not a claim that they have passed.

## Historical Incident
A former player class default of zero caused a `keyFrames` crash; an explicit point value was used as a repair at the time. That property is not part of the current player spawn configuration. A temporary desktop harness from that earlier implementation reported movement, animation, collider and camera checks; those results are historical, not a current smoke run.

## Project Relevance
The saved map has a `PLAYER` point and a separate `TELEPORT` point. Player rendering uses the logical frame and composite loadout rather than the former tile-player setup.

## Open Questions
None specific to the current point-spawning path.
