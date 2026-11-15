# Tiled animation class and single map atlas

## Date
2026-11-15

## Git Commit

026b006cf3fe23512c611515c0ca2bbe18a65cc9

## Change Summary
Use a reusable nested Tiled Animation class and one manifest atlas per map.

## Files
Tiled project and tileset, map manifest, asset definitions and loader, entity factory, Tiled loader and property parsers, tests, specifications, and authoring documentation.

## Behavioral Impact
Animated objects use camelCase nested animation fields. Placement overrides preserve other tile animation fields. Map and atlas use the map name as logical ID; Tiled objects no longer select an atlas asset.

## Specification Impact
Updated animation authoring and map asset ownership decisions for nested classes and a single atlas.

## Risks
Existing external map manifests and Tiled assets require migration. Weapon separate-atlas rendering remains unsupported.

## Follow-up Items
None.
