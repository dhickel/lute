# Meridian Court hoard-mode arena

## Date

2026-11-23

## Git Commit

e7ada068059e03d2e2933fbb09d2bafcab1b1cb2

## Change Summary

Authored Meridian Court, a finite 100 × 100 orthogonal map with 32 px tiles, using desert terrain and buildings. A continuous tiled perimeter encloses the central court, quarry, dye market, cisterns and customs arcade. Cover includes walls, rock formations, building footprints and 197 scenery tile objects. The outer road connects districts without requiring passage through the center or across a bridge.

## Files

- `raw_assets/maps/hoard_mode/`: source TMX, object tileset and sprites, manifest, pack settings and preview.
- `raw_assets/tilesets/hoard_mode/`: terrain and masonry tilesets, source artwork and licenses.
- `assets/maps/hoard_mode/`, `assets/tilesets/hoard_mode/` and `assets/atlases/hoard_mode*`: generated runtime bundle. The generator ledger was reconciled; the contents of previously present assets were unchanged.

## Behavioral Impact

The arena can be opened in Tiled and loaded by ID `hoard_mode`. Startup still selects `test_tiled`. No spawns, triggers or mode logic were added. Ground and Buildings remain tile layers; scenery uses the Objects layer. Solid tiles and objects carry embedded collision shapes. Bridges and pavement inlays are walkable. The empty Collisions layer is retained for parity with the reference; the current object-layer loader does not accept bare rectangle objects there.

## Specification Impact

Specification Impact: none. The arena uses the existing map, tileset, collision, atlas and asset generation contracts without altering them.

## Risks

Both asset trees are Git-ignored by policy; the map files live in the local asset trees and must be provisioned separately for another checkout. Tiled rendered all 3200 × 3200 pixels; the production loader and native Box2D matched 1,229 fixtures, including the four generated map bounds. The authored walkable area was connected at sampled player clearance, and a desktop smoke test rendered 100 frames across five viewpoints. Both teardown cycles cleared bodies, fixtures and entities.

## Follow-up Items

Add spawn markers in `Triggers` and regenerate with `./generate-runtime-assets.sh` when ready to test gameplay.
