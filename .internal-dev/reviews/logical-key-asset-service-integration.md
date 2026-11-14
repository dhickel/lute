# Logical-key asset service integration validation

## Scope

Custom direct-loading asset service implemented on `feat/logical-key-asset-manager`. Implementation commit `fdbf8b4`; baseline `14c009ed40d94f8173d57f4454f18b6b6c4bca9b`. User requested a pull request, not a local merge. Two earlier exploration reviews remain unrelated, untracked work.

## Findings

- Completed implementation: actual class-plus-ID composite storage; unchecked generic get with checked load boundary; no reference counts; idempotent matching loads and rejected conflicting definitions; synchronous queue/update; explicit status, cancellation, unload and disposal.
- Manifest IDs and custom Tiled project parameters preserved. Runtime audio events and animation atlas references use logical IDs. Maps, atlases, music and sounds load directly through LibGDX APIs/custom loaders rather than AssetManager.
- Initial independent review caught partial-construction texture leaks in stock direct loaders and a loading-screen transition after a failed load. Repairs now retain texture ownership until successful construction, clean partial runtime failures, and latch loading-screen failure until explicit retry. Eight post-allocation failure/retry cases plus shared map dependency coverage passed.
- Latest phase review: PASS. Final integration review: PASS for the requested change. Reviewers used configured `openai-codex/gpt-6.1-terra`, medium thinking. Senior implementation/repair used `openai-codex/gpt-6.1-sol`, high then xhigh. Writer used the configured Terra medium role. Work was sequential except disjoint prose and technical review.
- Root independently ran focused asset/manifest/Tiled/combat/debug checks and reproduced the unrelated baseline input failure. Reviewers inspected actual state and final integration made no edits.

## Risk Assessment

- `./gradlew check --continue` is NOT green: only `InputSystemTest.controllerMovementEvent` fails, expecting one event but observing zero. Relevant source/test are unchanged from baseline. Tracked at https://github.com/dhickel/lute/issues/6 and `.internal-dev/bugs/controller-movement-event-test-failure.md`.
- Assets and dependent references must not be used after unload. Distinct IDs loading the same file own independent resources. Loading is synchronous on the render thread, not background work.
- Graphics tests use mocked GL and native Box2D where applicable; no live GPU/desktop launch was performed.

## Recommendations

Review and merge through the requested GitHub PR workflow. Use `docs/asset-service.md` for the API and lifetime contract. Keep the baseline input repair separate from this asset change.

## Follow-ups

- Final integration executed `./gradlew check --continue` (only baseline failure above), `./gradlew :lwjgl3:build -x test` (PASS), diff whitespace check (PASS), and global policy comparison (PASS).
- Asset, manifest, Tiled animation, combat (768 checks), and trigger-debug checks passed.
- Commit and publish the final evidence with the implementation branch; do not merge locally or claim a fully passing suite.
