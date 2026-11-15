# AssetService versus LibGDX loaded lookup benchmark

## Scope

Benchmark-only comparison requested on `sound-refactor`, implemented on dedicated branch `benchmark/asset-manager-lookups` from `3fef4db`. Three paths: production typed-map `AssetService.get(Class, ID)`; a test-only four-map ID-to-path registry followed by real LibGDX 1.14.2 `AssetManager.get(path, Class)`; and direct manager lookup where callers already know the path. No production refactor. User's dirty `GameScreen`/`AudioSystem` edits and untracked audio bug/review files are outside scope.

## Findings

### Setup and interpretation

OpenJDK 25.0.4.1+1, AMD Ryzen 7 PRO 6850U, Linux x86_64, 512 MiB child heap. Five initial independent JVM forks and three fresh validator forks; four warmup rounds, seven measured rounds per workload, 16,384 deterministic shuffled queries repeated 64 times per sample. Three implementation orders rotate by round/fork. Each sample consumes resource identity checksums and a volatile sink. Correctness checks verify the same expected resource in all three variants, missing IDs/paths, unsupported types, cross-type IDs and wrong manager type.

Resources load through the actual service before timing with mocked GL/audio. A test-only subclass injects the same loaded resources through protected `AssetManager.addAsset`, intentionally bypassing manager loading and dependency tracking. Only the service owns resource disposal; validator repaired manager teardown to close its executor without disposing borrowed resources. This is a single-thread already-loaded retrieval benchmark, NOT disk/GL/audio loading or dependency/lifetime comparison.

The registry uses direct class dispatch into four `HashMap<String,String>` tables. This avoids an unnecessary class-map lookup. It checks type and missing ID but not every validation performed by the service. Direct callers supply paths; logical IDs and path strings differ in length/content. Equal-distinct query strings are prehashed, and registry-resolved paths remain stored references. These are intentional API-path differences, not isolated counts of hash operations.

### Results

All values ns/lookup, median of per-fork sample medians. Columns are service / registry-manager / direct-manager.

| Workload | Initial five forks | Independent three forks |
|---|---|---|
| Four-asset bundle, hot reused strings | 14.753 / 22.836 / 14.306 | 14.545 / 21.448 / 13.845 |
| Four-asset bundle, hot equal-distinct strings | 24.607 / 31.477 / 27.901 | 24.718 / 32.688 / 27.798 |
| 256 assets, wide reused strings | 17.873 / 29.609 / 20.910 | 17.810 / 29.282 / 21.085 |
| 256 assets, wide equal-distinct strings | 29.031 / 41.438 / 26.293 | 28.844 / 42.995 / 26.489 |
| 4096 assets, wide reused strings | 36.214 / 57.234 / 43.357 | 36.056 / 61.562 / 42.452 |
| 4096 assets, wide equal-distinct strings | 42.580 / 63.515 / 45.159 | 42.136 / 67.273 / 44.437 |

Independent fork-median ranges: bundle reused service14.384–15.107, registry20.718–25.076, direct12.349–13.954; bundle equal-distinct service24.140–25.035, registry30.846–33.209, direct25.308–28.928. For4096 wide reused: service35.726–36.223, registry56.537–64.359, direct36.952–44.282. For4096 wide equal-distinct: service42.124–42.781, registry62.878–67.502, direct40.247–45.411. Some individual sample outliers were substantially larger; use per-fork summaries rather than claiming fixed latency.

All measured fork median allocation values rounded to0.000 B/lookup. This is not a retained-memory measurement or proof of allocation behavior for other JVM/workload configurations.

### Validation

Independent phase verdict PASS. Validator inspected real manager usage/methodology, repaired fixture teardown, and reran three full benchmark forks. Implementer ran compileTestJava and asset lifecycle checks successfully. Its full check reproduced only the already known baseline input event assertion issue#6; it did not identify a benchmark or asset failure.

Reproduction: `./gradlew :core:assetManagerLookupBenchmark -PassetManagerBenchmarkArgs='5 4 7 64 /tmp/lute-manager-lookup-repeat'`. This manual task is not part of `check`. Raw samples, commands, logs and per-fork summary CSVs are in `/tmp/lute-manager-lookup-final/` and `/tmp/lute-manager-lookup-independent/`; temporary files may be cleaned up. Benchmark code and this retained summary make the comparison reproducible.

## Risk Assessment

Standalone microbenchmark, not JMH; identity hashing/checksum and differing string representations/validation affect absolute timings. Real synchronized manager methods are measured, but no contention or synchronization-cost isolation experiment was performed. No hardware cache-miss, retained-memory, loading-speed, frame-rate or gameplay claim is supported. No live GPU/desktop launch.

## Recommendations

Two-tier registry-plus-manager lookup is slower in these tested workloads. Direct manager is competitive and sometimes faster than the service; there is no universal winner between those two. Results do not justify changing production on their own, and user requested curiosity/measurement only.

## Follow-ups

Final independent integration verdict: PASS for this benchmark-only change. Two-fork correctness smoke and `:lwjgl3:build -x test --offline` passed. `check --continue --offline` failed only on unchanged baseline input issue #6. Root separately reran `:core:assetServiceTest --offline` successfully for both storage implementations and their partial-load cleanup cases; diff whitespace and global policy comparison passed. Implementation, phase/integration review and writing used configured `openai-codex/gpt-6.1-terra` medium; no senior escalation was needed. Keep production unchanged and merge only benchmark/doc/record files back into `sound-refactor`, preserving the user's dirty audio/screen work and untracked artifacts. No remote push was requested.
