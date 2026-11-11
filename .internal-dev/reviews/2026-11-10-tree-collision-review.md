## Scope

Investigation only of missing tree collisions in `assets/maps/test_tiled.tmx`; no application code or assets changed. Existing and concurrent edits to `TiledSystem.java` and `TiledAshleyLoader.java` were preserved.

## Findings

The five tree objects have GID 2666, resolving to local tile 7 in `assets/objects/objects.tsx` (firstgid 2659). That tile has one collision ellipse, but `isPhysics=true` and `physicBodyType=static` are properties of the ellipse rather than properties of the parent tile.

`TiledAshleyLoader.loadObject` reads `tile.getProperties().get("isPhysics", false, Boolean.class)`. The missing tile property evaluates to false, so `addEntityPhysic` is never called. `getPhysicBodyType` also reads the parent tile; moving only `isPhysics` would instead cause an unsupported body-type error.

The map's `Collisions` objectgroup contains zero objects. Enabling its dispatch does not affect collision shapes embedded in tree tiles. `loadObjectLayer` currently accepts tile-map objects only, not directly drawn collision primitives.

## Risk Assessment

Primary diagnosis is supported by direct source inspection and an XML parser check resolving all five tree instances, counting the collision ellipse, and verifying property scopes. No game execution or post-fix collision test was performed.

Separate observations: `old_tree1.tsx` declares 480 tiles although its image dimensions imply 12; runtime consequences were not established. `TiledSystem.loadMap` stores a TiledMap in the `mapAsset` property later read as MapAsset on reload; this separate defect is tracked independently.

## Recommendations

In Tiled, select the parent tree tile in the objects tileset, not its collision ellipse, and set Boolean `isPhysics=true` and String `physicBodyType=static`. Keep the ellipse geometry. No collision-loader code change is needed to satisfy the existing contract for these trees.

## Follow-ups

Apply the asset correction only when requested, then verify tree fixtures in the Box2D debug renderer and player collision in game. Review tileset metadata separately if it causes a reproducible problem.
