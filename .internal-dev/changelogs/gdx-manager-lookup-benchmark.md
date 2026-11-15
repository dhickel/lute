# Compare custom and LibGDX asset lookups

## Date

2026-11-14

## Git Commit

3fef4db57b8e1e41c7ca5979e6601ff6433f5aa9

## Change Summary

Added and documented a manual loaded-asset lookup comparison between the current typed-map service, a logical-ID registry followed by LibGDX AssetManager, and direct LibGDX AssetManager lookup. Initial five-fork and independent three-fork measurements completed successfully. No production behavior changed.

## Files

- `core/src/test/java/io/mindspice/lute/engine/assets/AssetManagerLookupBenchmark.java`
- Manual `assetManagerLookupBenchmark` task in `core/build.gradle` (not part of `check`).
- `docs/asset-manager-lookup-benchmark.md`
- `.internal-dev/reviews/asset-service-versus-gdx-manager-lookup.md`

## Behavioral Impact

Benchmark/test tooling only. Service-owned loaded resources are injected into a real manager for retrieval measurements, not loaded through its dependency pipeline. The service remains sole resource owner; the test manager shuts down without disposing borrowed assets.

## Specification Impact

None. Benchmark observations do not change an asset contract or choose a replacement implementation.

## Risks

Workload- and environment-specific results include differing logical IDs/paths and validation. Single-thread retrieval does not establish loading cost, contention, cache misses, retained memory or gameplay benefit. Graphics/audio backends are mocked; no live desktop launch. Temporary raw CSV/log output may be cleaned up; the comparison code and summary are retained.

## Follow-up Items

Independent phase PASS and correctness checks succeeded. The registry-plus-manager path was slower in measured workloads; direct manager versus custom service varied. Final integration must distinguish the known unrelated baseline input failure (#6) from benchmark validation and preserve the user's dirty audio/screen files and untracked artifacts. No production refactor is requested.
