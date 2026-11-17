## Topic

Tiled tile-object physics configuration and embedded collision shapes.

## Source References

- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/TiledPhysics.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/AnimatedPhysicObjectProps.java`
- `raw_assets/maps/test_tiled/objects/objects.tsx` and `raw_assets/maps/test_tiled/test_tiled.tmx`
- `core/src/test/java/io/mindspice/lute/engine/tiled/TiledAnimationPropertiesTest.java`

## Key Takeaways

- `TiledSystem` dispatches `PhysicObject` and `AnimatedPhysicObject` tile objects to `EntityFactory`. Placement properties override tile properties, including the object class. Unsupported tile classes fail with the class and object ID.
- Both classes create static bodies from embedded tile collision shapes and require at least one shape. They do not use the former `isPhysics` or `physicBodyType` flags. `TiledPhysics.fixtureDefOf` reads shape friction, restitution, density and sensor properties.
- Animated scenery uses nested `animation` properties parsed as `AnimationData`: `atlasKey`, `speed`, `frameInterval` and `soundFrame`. It does not use legacy `keyFrames`. `PhysicObject` uses `atlasKey` directly. Both resolve regions through the map atlas.
- Map GIDs resolve to external tilesets using `local tile ID = gid - firstgid`. In the saved map, five GID 2666 trees resolve to tile 7 in `raw_assets/maps/test_tiled/objects/objects.tsx`; that tile is an `AnimatedPhysicObject` with an embedded ellipse. The separate `Collisions` layer is empty.
- Standalone non-graphics Box2D checks need both `GdxNativesLoader.load()` and `Box2D.init()`. A parser-only harness constructing a `TiledMapTileMapObject` also needs a texture fixture rather than an empty region.

## Historical Flag Diagnosis

The former entity loader read `isPhysics` and `physicBodyType` from tile properties, not placement or embedded shape properties. An embedded ellipse alone did not enable physics, and a missing `isPhysics` defaulted to false. In the 2026-11-10 investigation the flags were nested in the ellipse, so the former loader skipped the tree bodies. Moving flags to the parent tile was advice for that removed loader, not a repair for the current class-based path.

## Project Relevance

`TiledAnimationPropertiesTest` checks the five trees' nested animation properties and placement overrides; it does not prove tree collision behavior. Sensor tests cover sensor behavior, not tree collisions. Earlier smoke or compilation results are historical and are not evidence of a new collision run.

## Open Questions

Current runtime tree collision behavior has not been established by the tests cited here.
