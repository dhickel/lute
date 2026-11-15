# Loaded asset lookup comparison

This manual benchmark compares the current `AssetService` loaded lookup with two test-only alternatives: a four-typed logical-ID-to-path registry followed by a real libGDX `AssetManager.get`, and a direct `AssetManager.get` when the caller already knows the path and class. It does not propose a production refactor or require a switch.

## Results

Measurements ran on Java 25.0.4.1+1 and an AMD Ryzen 7 PRO 6850U, with a 512 MiB fixed heap. Each cell is nanoseconds per lookup: the median of the fork medians, followed by the minimum and maximum fork median in brackets. Each fork median summarizes seven timed samples; the bracket is **not** the range of individual samples. These selected workloads illustrate the current four-asset bundle and the wider synthetic sets.

### Initial run: five forks

| Dataset / working set | Query strings | AssetService | Registry → manager | Manager direct |
|---|---|---:|---:|---:|
| Bundle 4 / hot | Reference | 14.753 [14.604–15.451] | 22.836 [21.179–23.428] | 14.306 [11.720–15.355] |
| Bundle 4 / hot | Equal, distinct | 24.607 [24.059–24.859] | 31.477 [30.386–33.382] | 27.901 [24.992–28.565] |
| Balanced 256 / wide | Reference | 17.873 [17.042–18.014] | 29.609 [27.784–32.503] | 20.910 [17.306–23.547] |
| Balanced 256 / wide | Equal, distinct | 29.031 [28.166–29.646] | 41.438 [38.473–42.011] | 26.293 [24.211–29.139] |
| Balanced 4096 / wide | Reference | 36.214 [35.813–36.839] | 57.234 [56.115–60.421] | 43.357 [36.069–44.830] |
| Balanced 4096 / wide | Equal, distinct | 42.580 [42.258–43.184] | 63.515 [63.020–67.334] | 45.159 [39.072–45.978] |

### Independent repeat: three forks

| Dataset / working set | Query strings | AssetService | Registry → manager | Manager direct |
|---|---|---:|---:|---:|
| Bundle 4 / hot | Reference | 14.545 [14.384–15.107] | 21.448 [20.718–25.076] | 13.845 [12.349–13.954] |
| Bundle 4 / hot | Equal, distinct | 24.718 [24.140–25.035] | 32.688 [30.846–33.209] | 27.798 [25.308–28.928] |
| Balanced 256 / wide | Reference | 17.810 [14.137–18.082] | 29.282 [26.413–35.312] | 21.085 [15.571–23.816] |
| Balanced 256 / wide | Equal, distinct | 28.844 [28.601–29.095] | 42.995 [38.803–45.778] | 26.489 [25.186–28.600] |
| Balanced 4096 / wide | Reference | 36.056 [35.726–36.223] | 61.562 [56.537–64.359] | 42.452 [36.952–44.282] |
| Balanced 4096 / wide | Equal, distinct | 42.136 [42.124–42.781] | 67.273 [62.878–67.502] | 44.437 [40.247–45.411] |

All fork median allocation measurements were 0.000 bytes per lookup. In these selected workloads the registry plus manager was slower than the service, consistent with the extra lookup in this setup. Direct manager lookup versus the service varied by workload; neither is a categorical winner. These results do not establish a universal ranking or a cache, memory-footprint, or frame-rate effect.

## Method and limitations

The benchmark source is `core/src/test/java/io/mindspice/lute/engine/assets/AssetManagerLookupBenchmark.java`. Each fork uses four warmup and seven measured samples, with 16,384 shuffled queries repeated 64 times per sample. The order of the three implementations rotates across rounds and forks. Each lookup contributes the identity hash of the returned resource to a checksum, checked against the same expected resource references for all three paths. Identity, missing lookup, type, and synthetic cross-type ID collision checks passed, as did the independent three-fork run.

The four-entry dataset comes from the current `test_tiled` bundle manifest. The larger balanced datasets use distinct synthetic paths per asset. Hot queries cover up to 16 assets; wide queries cover the whole dataset. Reference queries reuse the stored ID and path strings. Equal-distinct queries use separate equal strings with hashes computed before timing. IDs and paths have different contents and lengths, and the registry passes its retained path references to the manager even in the equal-distinct case. Thus these are useful comparisons of complete lookup paths, not identical string operations.

Outside the timed region, `AssetService` actually loads the resources using mock GL and audio. A test-only `AssetManager` subclass injects those same objects via protected `addAsset`, bypassing manager loading and dependency tracking. Its `clear` override prevents disposal of borrowed resources while `dispose` shuts down the manager executor; the service remains the sole resource owner. The timed region is single-threaded and excludes I/O and loader costs. Real manager methods are synchronized, but this does not measure contention.

The alternatives also perform different validation: the service checks open state, type, ID, and loaded state; the registry checks supported type and missing ID but not blank ID or open state; direct manager lookup requires the caller to know the path and validates the manager's loaded path/type. Differences in validation and input shape matter when interpreting the timings.

The full project check has a known unrelated baseline input failure (#6); these benchmark checks are not a claim that the full suite is green.

## Reproduction

From the repository root, with the appropriate Java runtime:

```sh
./gradlew :core:assetManagerLookupBenchmark -PassetManagerBenchmarkArgs='5 4 7 64 /tmp/lute-manager-lookup-repeat'
```

Arguments are fork count, warmups, samples, repetitions, and output directory. The task writes per-fork logs and CSV files plus `summary.csv`, which contains per-fork sample medians and sample ranges. The reported initial and independent runs used `/tmp/lute-manager-lookup-final/summary.csv` and `/tmp/lute-manager-lookup-independent/summary.csv`, respectively; these local temporary files are not durable project artifacts.
