# Offscreen Composite Rendering

## Date
2026-11-16

## Git Commit

2e80b9813f50eafb9929349f8ba0f1c1d5a3fa87

## Change Summary
Implemented offscreen composite-character rendering with texture-page batching, isolated cells, and premultiplied-alpha handling.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/CompositeCharacter.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/CharacterCompositor.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/RenderState.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/RenderSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `lwjgl3/build.gradle`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeRenderTest.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeGameTest.java`
- Composite-character specification, decisions, index and asset knowledge.

## Behavioral Impact
Visible character layers share animation timing and render as one depth-sorted sprite. The renderer requires an idle batch; raw GL callers must rebind their own vertex inputs.

## Specification Impact
Updated the existing composite-character specification, decisions, index, and asset knowledge. Phases 1–3 are implemented; final integration validation pending. Phase 4 is queued for independent primary and secondary attack sounds using existing audio.

## Risks
Arbitrary external vertex-pointer restoration is not guaranteed and is outside the existing engine rendering boundary.

## Follow-up Items
Implement and validate phase 4 per-attack sounds, then complete final integration validation.

## Validation
Independent phase validator returned PASS. Root independently reran `DISPLAY=:0 ./gradlew :lwjgl3:compositeRenderTest :core:combatSystemTest :core:dataLoaderTest :lwjgl3:compileJava --console=plain` successfully. Real game validation: 9,917 gameplay checks and 384 actual-asset framebuffer comparisons across two weapon pages; synthetic rendering: 1,402 checks. Combat: 1,595 checks. Screenshots inspected, including `/tmp/lute-phase3-characters.png`. Validator strengthened transformed culling and restored both enabled and disabled batch-blending states. Existing unrelated input movement-event failure remains tracked in issue #6.
