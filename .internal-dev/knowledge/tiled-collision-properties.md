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

## Open Questions

A repair was not requested or applied during the investigation. In-game collision behavior after correcting the asset remains to be validated.
