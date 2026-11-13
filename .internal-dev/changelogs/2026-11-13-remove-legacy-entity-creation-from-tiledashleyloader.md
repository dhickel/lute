# Remove legacy entity creation from TiledAshleyLoader

## Date
2026-11-13

## Git Commit

f13817b68de8d06d36085672c3f877c6d847c21e

## Change Summary
Removed entity creation, asset/atlas lookup, controller, movement, camera, and animation setup from TiledAshleyLoader. Retained static environment collision loading for tile layers. Simplified its constructor to World only and removed dead helper parameters. Updated GameScreen wiring and replaced TiledSystem's legacy entity fallback with an explicit unsupported-class error including object ID.

## Files
- `core/src/main/java/io/mindspice/lute/engine/tiled/TiledAshleyLoader.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `.internal-dev/knowledge/tiled-collision-properties.md`

## Behavioral Impact
Supported entity classes continue through EntityFactory. Unclassified/unsupported tile objects no longer use property flags and instead fail clearly. Tile-layer static bodies retain position, scaling, environment user data, fixture properties, and fixture names. The loader's class name remains unchanged as requested scope did not include renaming.

## Specification Impact
Specification Impact: none. No living specification requires the removed legacy entity path; class-based entity creation is already implemented.

## Risks
Legacy maps with unclassified tile objects must assign a supported class rather than relying on removed flag-based creation.

## Follow-up Items
Core/desktop compilation, non-graphics animation-property regression, manifest tests, and diff checks passed. A temporary native Box2D check confirmed a static tile body, position, environment marker, fixture name, and sensor setting. The standalone check required both libGDX and Box2D native initialization. No game smoke run.
