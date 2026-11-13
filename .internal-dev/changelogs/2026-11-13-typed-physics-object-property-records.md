# Typed physics object property records

## Date
2026-11-12

## Git Commit

6b9c9529cdb5a03edb61729e220cf916651dace1

## Change Summary
Added PhysicObjectProps and AnimatedPhysicObjectProps records with explicit constructors and fromMapObj helpers matching the PlayerCharProps structure. Every field is extracted with Objects.requireNonNull and a property-name error message. TiledSystem parses the records and supplies them to EntityFactory; factory methods now use record accessors rather than MapProperties lookups.

## Files
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/PhysicObjectProps.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/AnimatedPhysicObjectProps.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `.internal-dev/knowledge/tiled-collision-properties.md`

## Behavioral Impact
Missing required fields fail during parsing, before entity construction. Tile properties remain inherited and placement values override them. Animation speed accepts numeric integer or float values. Existing player property parsing is unchanged.

## Specification Impact
Specification Impact: none. No existing living specification governs these property records; this follows the user's explicit fail-fast requirement.

## Risks
Physics object property parsers intentionally require explicit values rather than applying Tiled project-class defaults omitted from TMX.

## Follow-up Items
Core and desktop compilation passed. Temporary isolated parser checks passed for inheritance, placement overrides, integer/float speed, and named failures for every missing field. The temporary fixture required mocked graphics interfaces because an empty TextureRegion cannot construct a tile-map object. No game smoke runs or permanent tests added; user asset changes remain untouched.
