# Class-based physics object factories

## Date
2026-11-12

## Git Commit

6f1d3ce7001b96912f0bff68a0d2a87cae396245

## Change Summary
Added dedicated EntityFactory methods for PhysicObject and AnimatedPhysicObject and wired TiledSystem to select them by placement or tile class. Preserved the legacy loader for other objects and existing point-player spawning.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `.internal-dev/knowledge/tiled-collision-properties.md`

## Behavioral Impact
Both physics classes always create static bodies from embedded tile collision shapes, without isPhysics/isAnimated/physicBodyType flags. Placement properties override tile properties. PhysicObject uses assetKey and atlasAsset; AnimatedPhysicObject uses atlasKey and animation settings. Animated scenery gets animation and facing without player movement state machines or input. Shared helpers resolve atlas assets, initialize animations, create graphic/transform components, and construct fixtures with shape disposal and body cleanup on failure. Missing collision geometry or invalid atlas/animation configuration produces an explicit error.

## Specification Impact
Specification Impact: none. No existing living specification defines these class variants; implementation follows the user's current Tiled class definitions.

## Risks
Physics objects are static scenery. Unclassified tiles still use the property-based legacy loader. Animated class keyFrames must be explicitly positive; its project default is zero. Runtime defaults and Tiled class definitions must remain aligned.

## Follow-up Items
Core and desktop compilation, diff checks, and configuration inspection passed. Configuration checks verified class property names and the tree's animation/collision geometry without legacy flags. No smoke runs or permanent tests added. User-edited Tiled assets remain untouched and uncommitted by this change.
