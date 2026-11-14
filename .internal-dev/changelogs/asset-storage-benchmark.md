# Asset storage comparison benchmark

## Date

2026-11-14

## Git Commit

27b251f24e591b17080bacd4a7ffe89532834099

## Change Summary

Completed phase 1 comparison of composite-key and per-type asset storage. Production remains unchanged. The test-only candidate uses typed maps and an ordered pending queue, with adapters, a manual benchmark task, and shared lifecycle coverage. Initial five-fork and independent three-fork runs favored per-type storage overall; full methodology and results are in `.internal-dev/reviews/asset-storage-benchmark-comparison.md`.

## Files

- Test-only `PerTypeAssetService`, `AssetStorage`, and `AssetStorageBenchmark`.
- Shared `AssetServiceTest` lifecycle coverage.
- Manual `assetStorageBenchmark` task in `core/build.gradle`, not wired into `check`.
- `.internal-dev/reviews/asset-storage-benchmark-comparison.md`.

## Behavioral Impact

No production behavior changed. Shared lifecycle tests passed for both complete implementations. Independent bundle hot reused-ID lookups measured 14.938 versus 11.710 ns/get; equal-distinct IDs 24.426 versus 17.693. The widest equal-string case was close rather than a universal win.

## Specification Impact

None. This phase compares alternatives without changing the runtime contract.

## Risks

Simulated loading backend and sequential queue-prefix warmup mean queue timing is not actual file-loading performance. First run measured zero bytes/get for both; one independent composite fork measured 24 B/get while the others and typed candidate measured zero. Hardware cache behavior, retained memory, and frame performance were not measured.

## Follow-up Items

Proceed with the user-authorized conditional refactor after independent benchmark PASS. Preserve generic retrieval, no reference counts, and lifecycle semantics; run the full check at final integration.
