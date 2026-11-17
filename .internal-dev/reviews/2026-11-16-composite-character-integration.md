# Composite character integration validation

## Scope

Reviewed `feature/composite-character-loadouts` at `a656e9e` against `origin/master` `024d3d5`, including the complete feature diff, specifications, knowledge, raw/generated assets, runtime loadout/combat/animation/rendering code, sounds, asset generation, and desktop GL tests.

## Findings

- Composite-character acceptance criteria pass: default basic layers plus `basic_sword`, primary/secondary attack routing, sword/bow/unarmed behavior, bow primary animation-only behavior, independent attack data and sound IDs, loadout safety, animation/facing/timing cleanup, and no NPC/inventory/UI or GL-framework expansion.
- The compositor passes its real-GL coverage for page-aware batching, isolated cells/gutters, resizing/culling/disposal, facing/order, alpha/tint/holes, and world-depth sorting.
- Asset ownership and source consistency pass: 1,440 raw 64x64 PNG frames, six generated atlases, multipage weapons output, 29 ownership entries with matching hashes, identical raw/generated JSON, and no runtime raw-sprite tree.
- Fixed a feature-introduced Gradle failure in `lwjgl3/build.gradle`: the JavaExec desktop test sources caused the ordinary `lwjgl3:test` task to fail on zero discovered JUnit tests. The task now explicitly permits that expected arrangement; `compositeRenderTest` remains the actual desktop integration test.
- Fixed atlas-generation ordering sensitivity in `TexturePackerTool` by staging pack inputs in sorted path order before packing. Repeated generation preserved all asset bytes and ownership hashes.
- The known unrelated `InputSystemTest.controllerMovementEvent` baseline failure remains unchanged and visible. It reports `press emits once: expected 1, got 0` and is tracked by issue #6 and `.internal-dev/bugs/controller-movement-event-test-failure.md`.

## Risk Assessment

- The unfiltered Gradle command still exits nonzero solely because of the pre-existing input baseline failure. The feature-filtered full check passes, so this is not an integration blocker for the composite-character work.
- Bow projectile execution is intentionally deferred; the accepted contract requires animation without projectile creation or damage, which the tests verify.
- The desktop environment reports a `libdecor-gtk.so` warning, but all real-GL checks pass.

## Recommendations

- Accept the composite-character integration.
- Resolve the separate controller movement-event contract in issue #6 before requiring an all-green unfiltered `check`.

## Follow-ups

- Re-run the unfiltered validation after the independent input issue is resolved.
- No composite-character follow-up is required by this review.

## Final Validation

### Criteria Checked

- Loadout defaults, switching, unarmed rendering, attack-slot independence, animation alignment, bounds/stats/collision data, sound loading/IDs, hit-sound load-only behavior, safe attack cleanup, and bow no-projectile/no-damage behavior.
- Six atlas outputs, weapon multipage/category prefixes, frame counts and dimensions, source/generated data consistency, ownership hashes, stale-output handling, and repeat-generation stability.
- Offscreen compositor batching and resource/state behavior, transparent edges, tint/PMA handling, layer order, facing, cell isolation, and world-depth sorting.
- Scope boundaries: no NPC spawning, inventory/UI expansion, projectile execution, GL backend expansion, or movement-event contract changes.

### Commands and Results

- `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`: **FAIL, exit 1**, solely at `:core:inputSystemTest`; `InputSystemTest` reports 1 failed group out of 1,235 checks. All other tasks pass, including `CompositeGameTest` (9,917 checks; 384 framebuffer comparisons; 2 weapon pages) and `CompositeRenderTest` (1,402 real-GL checks).
- `DISPLAY=:0 ./gradlew check -x :core:inputSystemTest :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`: **PASS, exit 0**.
- `DISPLAY=:0 ./gradlew :util:texturePackerToolTest --console=plain`: **PASS**.
- Repeated `./raw_assets/generate-runtime-assets.sh --console=plain`: **PASS**, byte-identical output; 1,440 raw frames and all 29 ownership hashes verified.
- `git diff --check`: **PASS**.
- Global policy synchronization `cmp`: **PASS**.

### Findings

The only remaining failing test is the documented, independently reproduced controller movement-event baseline. The feature-specific validation and the full check with only that task excluded pass. The initial zero-discovered-test failure in `:lwjgl3:test` was repaired and does not recur.

### Edits Made

- Added `failOnNoDiscoveredTests = false` to `lwjgl3/build.gradle` for the JavaExec-only desktop test source set.
- Added deterministic sorted staging of pack inputs in `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`.
- No Git metadata was changed, no commits or branches were created, and no ordinary documentation was edited.

### Remaining Risks

Only the unrelated controller movement-event baseline failure and the accepted deferred bow projectile capability remain outside this integration's resolved path.

**VERDICT: PASS**
