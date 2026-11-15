# Asset storage comparison before production refactor

## Scope

Compare the original `AssetService` composite-key `LinkedHashMap` against a complete test-only `PerTypeAssetService` using four typed maps selected by class equality, plus separate ordered pending/ownership collections. Production remained unchanged during measurement. Baseline: `27b251f` on `perf/asset-storage-benchmark`; integration target is the existing PR branch `feat/logical-key-asset-manager`.

## Findings

### Method

OpenJDK 25.0.4.1+1 on AMD Ryzen 7 PRO 6850U, Linux x86_64. Child JVM heap: 512 MiB initial/max. First run: five JVM forks; independent validator rerun: three. Each lookup workload uses four warmup rounds and seven measurement rounds, 16,384 deterministic shuffled queries repeated 64 times per sample. Both adapters add equivalent delegation; checksums and a volatile sink consume returned resource identities. Setup/loading is outside lookup timing. Query cases include the actual four-entry manifest bundle, balanced 256/4,096-entry sets, hot subsets and wide access, reused string references and prehashed equal-but-distinct strings. Mixed types include the same ID under different classes. No added benchmark dependencies; benchmark is not part of `check`.

Command: `./gradlew :core:assetServiceTest :core:assetStorageBenchmark --offline -PassetBenchmarkArgs='5 4 7 64 /tmp/lute-asset-storage-benchmark-reproduction'`. Independent command used `3 4 7 64 /tmp/lute-asset-storage-independent`.

### Lookup results

Values are ns/get, median of per-fork sample medians. First run and independent run both favor typed storage overall; the widest equal-string case is close and one first-run fork reversed that comparison.

| Workload | First run composite | First run typed | Independent composite | Independent typed |
|---|---:|---:|---:|---:|
| Bundle, hot, reused IDs | 14.504 | 12.076 | 14.938 | 11.710 |
| Bundle, hot, equal-distinct IDs | 23.963 | 17.052 | 24.426 | 17.693 |
| 256, hot, reused IDs | 14.347 | 11.704 | 14.437 | 11.614 |
| 256, hot, equal-distinct IDs | 24.034 | 16.994 | 23.342 | 17.066 |
| 256, wide, reused IDs | 16.865 | 13.953 | 16.859 | 13.851 |
| 256, wide, equal-distinct IDs | 27.379 | 20.866 | 27.288 | 21.115 |
| 4096, hot, reused IDs | 13.726 | 11.462 | 14.535 | 11.746 |
| 4096, hot, equal-distinct IDs | 24.029 | 17.268 | 23.738 | 17.246 |
| 4096, wide, reused IDs | 35.332 | 29.416 | 35.390 | 29.493 |
| 4096, wide, equal-distinct IDs | 37.594 | 36.159 | 38.653 | 36.185 |

Independent per-fork median ranges (composite versus typed): bundle hot reused 14.111–23.808 versus 9.378–11.835; bundle hot equal-distinct 23.126–24.684 versus 17.285–18.501; 4096 wide reused 22.778–36.012 versus 18.952–29.756; 4096 wide equal-distinct 28.570–38.772 versus 26.060–36.664 ns/get.

### Queue results, separate from lookup

Independent ns/update over 128 queued loads: loaded prefix 0: 2454.703 → 667.117; prefix 256: 6342.648 → 356.844; prefix 4096: 31050.680 → 131.734. This measures simulated audio loading and queue bookkeeping. Avoiding scans of already loaded entries is a separate algorithmic benefit, NOT evidence about hashing alone. Prefix cases run sequentially and later cases benefit from extra JVM warmup; these values must not be presented as actual disk/GPU loading-time savings.

### Allocation and validation

First run measured zero current-thread allocated bytes/get for both. In the independent run one fork measured 24 B/get for composite across measured lookup cases, while other composite forks and all candidate forks measured zero. Therefore source-level key construction does not imply inevitable runtime allocation, nor does one zero-allocation run prove allocation never happens.

Both complete implementations passed shared lifecycle coverage, including real direct atlas/map paths with mocked GL, partial-load cleanup, cross-type identity, conflicts, order, cancellation/requeue, failure/retry, and disposal. Independent phase verdict: PASS, no repairs. Manifest and Tiled-animation regressions passed. Full suite reserved for final integration; unchanged baseline input issue remains tracked in GitHub #6.

## Risk Assessment

Standalone microbenchmark, not JMH or hardware-counter measurement. No retained-memory size or CPU cache-miss measurement; no demonstrated frame-rate or gameplay improvement. Identity-hash/checksum work is included. JVM optimization and host load contribute variation. Raw first-run files are in `/tmp/lute-asset-storage-benchmark-verified/`; independent files in `/tmp/lute-asset-storage-independent/`, including fork CSV samples/logs/commands and summary CSV. Temporary files may not survive cleanup; the tables above retain the decision evidence.

## Recommendations

Proceed with the user-authorized conditional refactor to typed maps and separate pending queue: benefits reproduced for relevant small/hot lookup patterns and queue scans, with lifecycle equivalence tested. Keep the original complete implementation in test scope so comparison remains runnable. Do not claim typed storage is universally faster.

## Follow-ups

Refactor production only after this phase's commit. Rerun shared tests and independent phase/final integration validation. Preserve the generic public API and no-reference-count lifetime contract. Add the benchmark reproduction instructions and caveats to documentation after production integration.
