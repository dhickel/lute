# Typed asset storage final integration

## Scope

User requested two complete implementations, a benchmark, and conditional promotion of the faster alternative. Work performed on `perf/asset-storage-benchmark`, targeting existing PR branch `feat/logical-key-asset-manager`. Phase commits: `9a8cde4` (comparison), `050683a` (promotion). Initial baseline `27b251f`.

## Findings

- Phase 1 independent PASS: complete typed candidate and actual original composite production implementation, shared lifecycle tests and equivalent adapters, forked warmup/measurement benchmark. Five initial and three independent forks favored typed storage overall, with widest equal-string cases close and some reversals. Method, results, ranges, and limitations are in `asset-storage-benchmark-comparison.md` and `docs/asset-storage-benchmark.md`.
- Phase 2 independent PASS: production exactly matches benchmarked typed candidate after normalizing class name/visibility and removing obsolete test comment. Frozen `CompositeAssetService` exactly matches original after class/constructor renaming and visibility normalization. Root independently confirmed both normalized comparisons. Adapter-wiring regression checks prevent measuring the same implementation twice.
- Public API, global class-plus-ID logical identity, synchronous direct loaders, no-reference-count lifetime, conflicts, ordering, cancellation, retry, and cleanup remain unchanged. Physical lookup uses typed maps; pending entries have an independent ordered set instead of scanning loaded entries.
- Final independent integration verdict: PASS for the requested change, no edits or unresolved in-scope defects. All reviews and writing used configured `openai-codex/gpt-6.1-terra` medium. Initial benchmark implementation required senior escalation (`openai-codex/gpt-6.1-sol` high) after the ordinary implementer returned no changes. Root checked source, evidence and normalized promotion independently. Two phases were sequential; only bounded prose work ran alongside disjoint technical work.

## Risk Assessment

- `./gradlew check --continue --offline` is NOT green: only unchanged `InputSystemTest.controllerMovementEvent` expected-one/observed-zero failure remains. Final validator reproduced it in the baseline snapshot; issue https://github.com/dhickel/lute/issues/6 tracks it.
- No retained-memory, hardware-cache-miss or frame-rate claims are supported. Current-thread allocation varied by JVM fork for composite keys. Queue timing includes a simulated audio backend and has sequential-prefix warmup bias, not measured disk/GPU savings.
- Graphics tests use mocked GL, no live desktop launch. Resource lifetime restrictions remain unchanged.

## Recommendations

Merge this validated performance unit into the existing PR branch, update PR #7, and retain the original implementation in test scope for reproducible future comparison. Do not merge the open PR into master automatically.

## Follow-ups

Final checks: asset lifecycle coverage for BOTH implementations, including eight partial failure/retry cases each and shared TMX dependency (PASS); manifest/Tiled/combat/debug checks (PASS, combat 768); `./gradlew :lwjgl3:build -x test --offline` (PASS); diff whitespace and global policy comparison (PASS). Root separately reran `:core:assetServiceTest --offline` successfully. Full check only has the evidenced unrelated failure above. Preserve two previously untracked exploration reviews outside these commits.
