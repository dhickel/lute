# Asset loading and ownership

## Topic

LibGDX managed reference counts versus direct map texture ownership.

## Source References

- LibGDX 1.14.2 `AssetManager` and `TmxMapLoader` source jars in the local Gradle dependency cache.
- `core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/FixedTmxMapLoader.java`
- `docs/asset-service.md`
- `docs/asset-storage-benchmark.md`
- `.internal-dev/reviews/asset-storage-benchmark-comparison.md`
- `core/src/test/java/io/mindspice/lute/engine/assets/AssetStorageBenchmark.java`

## Key Takeaways

AssetManager reference counts track managed loads and dependencies, not `get` calls or Java references. Direct `TmxMapLoader` loading gives the map ownership of textures through `setOwnedResources`; this differs from its AssetManager-managed loading branch. Neither approach makes a retained Java reference safe after its owner disposes the underlying resource.

For storage microbenchmarks, use multiple JVM forks and warmup rounds, identical deterministic shuffled query sets, and consumed, checked results; compare per-fork distributions rather than relying on a single number. Setup is outside lookup timing, but identity hashing and checksum work are included. Queue update timings also include simulated loading and reflect the queue algorithm, not hashing alone; sequential cases can receive unequal warmup. Source-level composite key construction does not guarantee allocation: JIT behavior varied across forks, including one composite fork at 24 B/get when other composite forks and all typed forks measured zero. The first run measured zero for both. These results contain no hardware cache-miss measurements and cannot establish a cache mechanism or gameplay improvement.

A legacy classpath loader is not precedent for authored gameplay data. Player, weapon, and projectile JSON belong in the externally provisioned `raw_assets/data/` tree, are copied to `assets/data/` by generation, and are loaded at startup through `GameConsts.DATA_PATH` by `DataLoader`. `GameData` supplies the resolved data to gameplay; neither bundled resources nor a classpath fallback are part of this path. Both asset trees remain ignored.

The four typed maps already enumerate every registered asset. An additional `owned` collection duplicated that index merely to preserve incidental diagnostic/disposal order, not a required dependency order. Keep only the separate loading `queue`, whose ordering and cancellation serve actual loading behavior; test diagnostic completeness rather than cross-type insertion order.

A terrain GID alone does not uniquely identify both a sheet-local animation frame and an overlapping placement. Generated atlas regions use `__lute_tile/<firstgid>` with the local tile ID as index. Placement resolution follows tileset declaration precedence, including sparse collection holes; animation frames remain qualified to their own sheet. Regenerate earlier GID-only atlases. The real-GL fixture compared identical framebuffers and measured 8 to 1 draws for its particular four-sheet layout, not a general performance guarantee.

## Recovering blocked runtime asset publication

If a locally modified generated file blocks runtime publication, inspect its semantics and back up the affected file or files outside the repository. Reconcile the intended data in the raw assets, then move only the affected generated file out of the tree and regenerate. Leave the ownership manifest intact. This process has successfully restored publication.

## Project Relevance

The custom service uses direct map loading and disposes the map on unload. Callers must not retain or use the map or dependent resources afterward. Logical IDs and loader ownership are separate concerns; lookup keys need not expose file paths or LibGDX descriptors.

## Open Questions

None for these verified LibGDX ownership distinctions.
