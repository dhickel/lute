# Promote benchmarked typed asset storage

## Date

2026-11-14

## Git Commit

9a8cde4429de952c334ed0632a18bd632614f457

## Change Summary

Promoted typed asset storage with direct class selection and a separate ordered pending queue after initial and independent benchmark runs favored it overall. Retained the original complete composite implementation in test scope for continued comparison and documented the benchmark evidence and limitations.

## Files

- `core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`.
- Test-only `CompositeAssetService.java`, `AssetStorage.java`, and shared `AssetServiceTest.java`; removed the promoted test-only `PerTypeAssetService.java`.
- `docs/asset-service.md`, `docs/asset-storage-benchmark.md`.
- `.internal-dev/specifications/decisions.md`, `.internal-dev/knowledge/asset-loading-and-ownership.md`.

## Behavioral Impact

Public logical identity remains `(Class, ID)`. The interface, synchronous loading semantics, ordering, error handling, disposal, and no-reference-count ownership contract remain unchanged. Production now matches the measured typed implementation, with only class naming and visibility changed; the test baseline likewise matches the original composite implementation.

## Specification Impact

Updated the custom asset-service decision to record the physical storage choice and measurement-based justification without changing the public asset lifetime contract.

## Risks

Microbenchmark results are JVM- and workload-dependent, with noisy short smoke runs and some reversals. They do not establish CPU cache-miss, retained-memory, frame-rate, or gameplay improvements. Mocked-GL lifecycle coverage is not live-desktop validation.

## Follow-up Items

Independent phase validation passed asset lifecycle checks for both implementations, manifest, combat (768 checks), Tiled animation, and trigger-debug checks. Final integration validation remains to run. The pre-existing input issue remains tracked as GitHub #6.
