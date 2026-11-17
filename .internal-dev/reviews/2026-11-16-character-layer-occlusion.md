# Character Layer Occlusion Audit

## Scope

Reviewed the current basic character, four equipment layers, sword and bow across all four facings and authored eight-frame idle, walk, jab, slash, shot and death sequences where available. Inspected `CharacterCompositor` and both desktop graphics regression executables. Source assets, generated atlases, animation timing, character bounds and world-depth sorting are outside this correction and remain unchanged.

## Findings

- The old order drew hands after head in every direction. Upward jab, slash and shot frames consequently painted gloves onto the back of the helmet. An independent real-GPU regression reproduced the defect before the production fix: `Opaque upward helmet must cover gloves: slash/2 at 61,58`.
- Nonzero-alpha head/hand intersections occur in 20 of 192 basic frame pairs: 13 upward poses, five right-facing poses and two downward death poses. Upward intersections are the substantial reported artifact; smaller right/down intersections include antialiased boundaries. Alpha intersection alone is not proof of incorrect anatomy.
- Before/after contact sheets were inspected for all directions and the right/down edge cases. The corrected actual-asset showcase was also inspected. No additional sorting defect was confirmed in that review; future equipment still needs its own visual review.
- Both the old manually composed actual-frame reference and the synthetic shared top-pixel expectation repeated the same incorrect ordering. Agreement between those references did not establish correct occlusion.
- Correct back-to-front order is now UP: weapon, base, feet, body, hands, head; DOWN/LEFT/RIGHT: base, feet, body, hands, head, weapon. Unarmed characters omit the weapon. This changes helmet/glove priority, not the existing weapon front/back rule.
- New synthetic markers overlap exactly two layers at a time. All 15 pairs are checked in every facing and frame, with six armed actions and four unarmed actions. Expectations express semantic pair priorities rather than reading the production order array.
- Actual-GPU checks render helmet and gloves separately, then require opaque upward helmet pixels to cover intersecting glove pixels. Coverage explicitly exercises real jab, slash, shot and death overlaps.
- The multipage test retains exact rank and batching assertions: 10 characters, three targets, 18 source-page draws. Its expected bind order was updated for gloves-before-helmet rather than relaxed to unordered membership.

## Risk Assessment

The fixed whole-layer policy addresses current helmet/glove intersections; it is not a guarantee for arbitrary future gear or art requiring interleaved near/far limbs. Existing alpha, gutters, resource cleanup, mixed-facing batching and world sorting regressions remain active. The known unrelated movement-event test failure in issue #6 remains visible.

## Recommendations

Accept the direction-order correction and retain the independent occlusion checks. Review new equipment visually rather than treating mask overlap or a copied reference order as an anatomical oracle.

## Follow-ups

No unresolved defect remains in this corrected path. Review current poses live after restarting the game. Resolve the existing input-test failure separately. This review does not authorize pushing or merging the PR.

## Validation

- Red regression: `DISPLAY=:0 ./gradlew :lwjgl3:compositeRenderTest --console=plain` failed on the expected opaque-helmet assertion before the production fix. Evidence: `/tmp/lute-direction-order-red.log`.
- Senior focused validation: composite rendering, desktop compilation and combat passed after correcting the stale multipage bind-order expectation. Evidence: `/tmp/lute-direction-order-senior-validation.log`.
- Root unfiltered full gate: `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain` exited 1 only for the pre-existing `:core:inputSystemTest` assertion (`expected 1, got 0`). Evidence: `/tmp/lute-direction-order-full-unfiltered.log`.
- Root filtered full gate: the same command with `-x :core:inputSystemTest` exited 0. Evidence: `/tmp/lute-direction-order-full-filtered.log`.
- Actual gameplay: 15,814 checks, 480 actual-asset composition/native-scale comparisons and all seven weapon pages. Synthetic rendering: 11,807 real-GL checks. Map sensors: 1,732 checks. Combat passed; its count varies with execution.
- Source and runtime asset files remain unchanged; senior validation also verified all 49 ownership hashes. Diff whitespace and global policy synchronization checks passed.

**VERDICT: PASS**
