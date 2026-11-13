## Topic

Tiled tile-object physics configuration and embedded collision shapes.

## Source References

- `core/src/main/java/io/mindspice/lute/engine/assets/tiled/TiledAshleyLoader.java`: `loadObject`, `getPhysicBodyType`, `addEntityPhysic`.
- `core/src/main/java/io/mindspice/lute/engine/assets/tiled/TiledPhysics.java`: `fixtureDefOfMapObjectAndShape`.
- `assets/objects/objects.tsx`: tile ID 7.
- `assets/maps/test_tiled.tmx`: tree GID 2666.

## Key Takeaways

- For map tile objects, `isPhysics` and `physicBodyType` are read from `tile.getProperties()`, not the placed map object's properties or embedded collision shape properties.
- An embedded collision ellipse alone does not enable physics: missing tile-level `isPhysics` defaults to false and skips body creation.
- Shape properties configure fixture friction, restitution, density, and sensor behavior; shape-level `isPhysics` and `physicBodyType` are not consumed.
- Trace map GIDs to external tilesets using `local tile ID = gid - firstgid`; similarly named tree tilesets can otherwise mislead diagnosis.

## Project Relevance

On 2026-11-10 all five placed trees resolve to `objects.tsx` tile 7. The tile has a collision ellipse but both body-enabling properties are incorrectly nested inside that ellipse. The separate map `Collisions` layer has no objects, so enabling its dispatch cannot repair these trees. Set `isPhysics=true` (Boolean) and `physicBodyType=static` (String) on the parent tree tile to satisfy the current loader.

## Class-based Factory Update

- TiledSystem now dispatches `PhysicObject` and `AnimatedPhysicObject` tile objects to dedicated EntityFactory methods. These variants always create static collision bodies from the tile's embedded shapes; they do not read isPhysics, isAnimated, or physicBodyType.
- Placement properties override tile properties, including the object class (`type`). PhysicObject uses the class's `assetKey` property; AnimatedPhysicObject uses `atlasKey` and animation settings.
- Animated scenery has Animation2D, Graphic, and Facing, but no player movement state machine, controller, Move, or camera component.
- animationSpeed can arrive as either an Integer (project default) or Float (explicit tile value); parse it as Number.
- PhysicObjectProps and AnimatedPhysicObjectProps expose fromMapObj helpers that merge tile and placement properties, require every declared field with Objects.requireNonNull, and provide typed record values to EntityFactory. These parsers intentionally do not supply defaults, but the configured libGDX loader already supplies project-class defaults before parsing; requireNonNull therefore does not detect an inherited zero keyFrames. Keep positive-value validation in the factory and identify the animation key and atlas in the error.
- A textureless TextureRegion cannot construct TiledMapTileMapObject: its constructor copies region UVs and dereferences the texture. A parser-only harness must supply an appropriate texture fixture; an empty region is not sufficient.
- Unclassified objects still use the legacy TiledAshleyLoader path described above. The historical flag diagnosis only applies to that path.

## Open Questions

Runtime collision behavior after class-based migration was not exercised; validation used compilation and configuration inspection without smoke runs.
