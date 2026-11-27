## Summary
Terrain collision body origins assume one world unit per tile.

## Scope
`core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`, terrain layer collision placement.

## Reproduction
Static calculation, not a runtime reproduction: load a 16 px tile layer at `UNIT_SCALE = 1/32` with solid terrain in column 3.

## Expected
Collision starts at the rendered tile origin, world x=1.5 plus the shape's local offset.

## Actual
The native body starts at x=3; fixture-local coordinates are scaled, but the cell origin is not.

## Evidence
`TiledSystem.java:559–574` vs map bounds at `:516–532` and `RenderSystem.java:85–87` at `7d2cc90`.

## Impact
Rendered terrain and collision separate for maps where scaled tile size is not one world unit. Current local maps mask the issue.

## Status
Open; source-verified, runtime regression pending.

## Next Action
Scale cell origins using layer tile dimensions; validate against native body bounds with a non-32-pixel fixture.
