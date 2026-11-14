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

## Project Relevance

The custom service uses direct map loading and disposes the map on unload. Callers must not retain or use the map or dependent resources afterward. Logical IDs and loader ownership are separate concerns; lookup keys need not expose file paths or LibGDX descriptors.

## Open Questions

None for these verified LibGDX ownership distinctions.
