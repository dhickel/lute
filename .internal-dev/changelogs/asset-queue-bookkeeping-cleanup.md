# Remove redundant asset ownership bookkeeping

## Date

2026-11-14

## Git Commit

2d541155bfbc43622eca5873c695edc1cd11ee79

## Change Summary

Removed the redundant `owned` set from production `AssetService`. Renamed the ordered loading collection from `pending` to `queue`, matching the requested terminology.

## Files

- `core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`
- `core/src/test/java/io/mindspice/lute/engine/assets/AssetServiceTest.java`
- `docs/asset-service.md`, `docs/asset-storage-benchmark.md`
- `.internal-dev/specifications/decisions.md`, `.internal-dev/knowledge/asset-loading-and-ownership.md`

## Behavioral Impact

Diagnostics and shutdown disposal now enumerate the four existing typed maps directly. Cross-type insertion order is no longer preserved for those operations; loading queue order, cancellation, retries, exactly-once cleanup, and the public API remain unchanged. The original composite implementation stays frozen in test scope for benchmark comparison.

## Specification Impact

Updated the existing asset-service decision to make the maps the sole registration index and remove the incidental diagnostic/disposal ordering guarantee. No asset lifetime or lookup contract changes.

## Risks

Diagnostic and disposal ordering follows map iteration rather than registration order. Historical benchmark numbers predate this bookkeeping cleanup; the benchmark documentation now says so. No new performance claim is made.

## Follow-up Items

`./gradlew :core:assetServiceTest :core:assetStorageBenchmark --offline --console=plain -PassetBenchmarkArgs='2 1 3 1 /tmp/lute-asset-queue-cleanup-smoke'` passed. Both complete implementations passed lifecycle coverage including partial-load cleanup, cancellation/requeue, diagnostic completeness and disposal failures. Both smoke benchmark forks passed; timings are not treated as new benchmark evidence. Update existing PR #7 after committing and merging the dedicated cleanup branch into its head branch.
