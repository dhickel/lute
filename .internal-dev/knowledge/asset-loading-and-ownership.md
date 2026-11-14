# Asset loading and ownership

## Topic

LibGDX managed reference counts versus direct map texture ownership.

## Source References

- LibGDX 1.14.2 `AssetManager` and `TmxMapLoader` source jars in the local Gradle dependency cache.
- `core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/FixedTmxMapLoader.java`
- `docs/asset-service.md`

## Key Takeaways

AssetManager reference counts track managed loads and dependencies, not `get` calls or Java references. Direct `TmxMapLoader` loading gives the map ownership of textures through `setOwnedResources`; this differs from its AssetManager-managed loading branch. Neither approach makes a retained Java reference safe after its owner disposes the underlying resource.

## Project Relevance

The custom service uses direct map loading and disposes the map on unload. Callers must not retain or use the map or dependent resources afterward. Logical IDs and loader ownership are separate concerns; lookup keys need not expose file paths or LibGDX descriptors.

## Open Questions

None for these verified LibGDX ownership distinctions.
