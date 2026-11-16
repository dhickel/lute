# Composite character integration closeout

## Date

2026-11-16



## Git Commit

a656e9eb61d233aa9ca2ad62a15f06ae2ebf65c4



## Change Summary

Accepted all four composite-character phases after independent integration validation. The validator repaired deterministic sorted texture-packer input staging and the desktop JavaExec test source set's zero-discovered-JUnit handling.

## Files

- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`: sorted staging for repeatable atlas packing.
- `lwjgl3/build.gradle`: permit no discovered JUnit tests while retaining the JavaExec GL test.
- `.internal-dev/specifications/composite-characters.md`, `decisions.md`, and `index.md`: reflect accepted integration.
- `.internal-dev/knowledge/composite-sprite-assets.md`: record reproducibility and desktop validation practices.
- `.internal-dev/reviews/2026-11-16-composite-character-pr-targets.md`: complete per-path inventory.
- `.internal-dev/reviews/2026-11-16-composite-character-integration.md`: independent validation and verdict.
- This changelog records closeout; earlier phase changelogs describe implementation and authored assets.

## Behavioral Impact

Characters compose equipment and weapons with page-aware offscreen rendering; switching and primary/secondary attacks use validated data and independent sound IDs. Existing WAV files are approved for reuse; hit cues load but do not play. Bow primary animates without spawning a projectile or dealing damage.

## Specification Impact

The composite-character specification, index and affected decisions now state the implemented contract and accepted integration evidence; the projectile remains deferred.

## Risks

The unfiltered check still fails the pre-existing controller movement-event test tracked by issue #6. GL behavior remains platform-dependent despite real-GL coverage. No guarantee is added for arbitrary external raw-GL vertex pointers beyond the idle Batch engine boundary.

## Follow-up Items

Resolve issue #6 independently and rerun the unfiltered check. Bow projectile execution remains an accepted deferred capability.

## Validation Evidence

The [integration report](../reviews/2026-11-16-composite-character-integration.md) records **VERDICT: PASS** and the following results:

- **FAIL:** `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain` exits 1 solely at `:core:inputSystemTest`, pre-existing issue #6 (`press emits once: expected 1, got 0`). Other tasks pass, including 9,917 gameplay checks, 384 framebuffer comparisons across two weapon pages, and 1,402 synthetic real-GL checks.
- **PASS:** `DISPLAY=:0 ./gradlew check -x :core:inputSystemTest :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`.
- **PASS:** `DISPLAY=:0 ./gradlew :util:texturePackerToolTest --console=plain`.
- **PASS:** repeated `./raw_assets/generate-runtime-assets.sh --console=plain`: byte-identical output, 1,440 raw frames and 29 ownership hashes verified.
- **PASS:** `git diff --check` and global policy synchronization `cmp` at integration validation time.
