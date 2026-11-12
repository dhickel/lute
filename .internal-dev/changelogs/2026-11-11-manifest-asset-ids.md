# Manifest asset IDs

## Date
2026-11-11

## Git Commit

34411686b2d04bed9121da6e14fcaa548a902bbc

## Change Summary
Converted atlas, music, and sound lists in `AssetManifest` and `MapAssets` to string-ID maps. Updated `ManifestLoader`, the test manifest, and the existing test for `OBJECTS`, `TOWN`, and `SWORD_HIT`.

## Files
- `core/src/main/java/io/mindspice/lute/engine/assets/AssetManifest.java`
- `core/src/main/java/io/mindspice/lute/engine/assets/MapAssets.java`
- `core/src/main/java/io/mindspice/lute/engine/assets/tiled/ManifestLoader.java`
- `assets/maps/test_tiled.manifest.json`
- `core/src/test/java/io/mindspice/lute/engine/assets/tiled/ManifestLoaderTest.java`

## Behavioral Impact
Assets are represented by string IDs rather than list positions.

## Specification Impact
None; no applicable asset specification exists.

## Risks
The application migration remains incomplete. Existing array-shaped manifests must be converted to maps.

## Follow-up Items
Complete the application migration. No validation was run, as requested.
