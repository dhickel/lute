# Asset storage benchmark

This manual standalone microbenchmark compares the production `AssetService` four-map storage and ordered loading `queue` with the original complete composite-key service retained as test-only `CompositeAssetService`. Both are exercised through adapters with the same service semantics. It is not JMH and is not part of `check`.

Run from the repository root:

```sh
./gradlew :core:assetStorageBenchmark -PassetBenchmarkArgs='5 4 7 64 /tmp/lute-asset-storage-repeat'
```

Arguments are JVM forks, warmup rounds, measurement rounds, query repetitions, and output directory. The directory receives fork commands, logs and CSV samples, plus `summary.csv` with per-fork medians and sample ranges. Temporary output may be removed by the system.

## Method and results

The phase-one comparison ran on OpenJDK 25.0.4.1+1, AMD Ryzen 7 PRO 6850U, Linux x86_64, with a 512 MiB fixed child heap. The first run used five forks; an independent run used three. Both used four warmups, seven measurements and 64 repetitions of 16,384 deterministic shuffled queries per sample. Load/setup was outside lookup timing. Each implementation received the same mixed-type query order, including same IDs across types, both reused strings and prehashed equal-but-distinct strings. Identity-hash checksums and a volatile sink consume the results. Hot sets contain up to 16 entries; wide sets cover the dataset.

The following are ns/get, medians of per-fork sample medians. The bundle is the actual four-entry manifest bundle; the other sets are balanced across four types.

| Dataset and queries | First composite | First typed | Independent composite | Independent typed |
|---|---:|---:|---:|---:|
| Bundle, hot, reused | 14.504 | 12.076 | 14.938 | 11.710 |
| Bundle, hot, equal-distinct | 23.963 | 17.052 | 24.426 | 17.693 |
| 256, hot, reused | 14.347 | 11.704 | 14.437 | 11.614 |
| 256, hot, equal-distinct | 24.034 | 16.994 | 23.342 | 17.066 |
| 256, wide, reused | 16.865 | 13.953 | 16.859 | 13.851 |
| 256, wide, equal-distinct | 27.379 | 20.866 | 27.288 | 21.115 |
| 4096, hot, reused | 13.726 | 11.462 | 14.535 | 11.746 |
| 4096, hot, equal-distinct | 24.029 | 17.268 | 23.738 | 17.246 |
| 4096, wide, reused | 35.332 | 29.416 | 35.390 | 29.493 |
| 4096, wide, equal-distinct | 37.594 | 36.159 | 38.653 | 36.185 |

After these measurements, redundant ownership bookkeeping was removed and the loading collection was renamed from `pending` to `queue`; those cleanup changes were not separately performance-measured.

Typed storage was favored overall in these runs, but the widest equal-distinct case was close and one first-run fork reversed it. For example, independent fork medians for bundle hot reused ranged from 14.111–23.808 composite versus 9.378–11.835 typed ns/get; for 4096 wide equal-distinct they ranged from 28.570–38.772 versus 26.060–36.664.

The first run measured zero current-thread allocated bytes/get for both implementations. In the independent run one composite fork measured 24 B/get across measured lookup cases; the other composite forks and all typed forks measured zero. Source-level key construction does not guarantee runtime allocation: JIT behavior can vary, and a zero measurement does not prove allocation never occurs.

Queue timing is separate from lookup. In the independent run, over 128 queued loads, composite versus typed ns/update was 2454.703 versus 667.117 with no loaded prefix, 6342.648 versus 356.844 with a 256-entry prefix, and 31050.680 versus 131.734 with a 4096-entry prefix. This includes simulated audio loading and queue bookkeeping; avoiding scans of already loaded entries is an algorithmic difference, not evidence about hashing alone. Prefix cases run sequentially, so later cases receive extra JVM warmup. These are not disk or GPU loading-time savings.

## Validation and limitations

Before migration, both complete implementations passed shared lifecycle tests covering identity across types, conflicts, ordering, cancellation and requeue, failure and retry, disposal, and partial-load cleanup. Final independent integration validation passed for the in-scope changes; a separate root rerun confirmed both asset-service contracts and eight partial-failure cases per implementation. These tests exercise actual direct atlas and map paths with mocked GL, not a live desktop; no live launch was performed. The full `./gradlew check --continue --offline` failed only on the unchanged baseline `InputSystemTest.controllerMovementEvent` (expected 1, got 0), reproduced on the baseline and tracked as GitHub #6. The final gate did not rerun the benchmark; the results above remain the phase-one five- and three-fork measurements.

These measurements include identity hashing and checksum work and vary with JIT optimization and host load. They do not measure retained memory or hardware cache misses, establish a cache mechanism, or demonstrate frame-rate or gameplay improvement. They do not establish that typed storage is universally faster. The retained phase-one review contains the original commands and further results: `.internal-dev/reviews/asset-storage-benchmark-comparison.md`.
