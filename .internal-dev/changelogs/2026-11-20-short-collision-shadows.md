# Short collision-outline shadows

## Date
2026-11-20

## Git Commit

8753990becba40847ac4a0c9a4e5bae72718f4c5

## Change Summary
Replace long stencil shadows with short collision-outline shadows that fade behind each exit surface. Use per-light alpha visibility rather than a stencil attachment.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/{NightOcclusion,NightLightMesh,NightLightMap,NightLighting,RenderState,RenderSystem}.java`
- `core/src/test/java/io/mindspice/lute/engine/systems/LightingBehaviorTest.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/LightingRenderTest.java`
- `.internal-dev/specifications/decisions.md`, `.internal-dev/knowledge/night-lighting-gl-state.md`

## Behavioral Impact
Filled fixtures stay dark through their exit surface; visibility recovers behind the outline over `min(0.75 × blocker size, 24 authored pixels, 0.25 × current radius)`. Overlaps use the darkest visibility and each light remains independent. No new assets, settings, or physics changes.

## Specification Impact
Updates the session night-lighting decision with the user-confirmed short-shadow contract and collision-size rules.

## Risks
Collision outlines may differ from visible art. The alpha-mask pass requires blend-min support on GLES2; driver coverage remains limited to tested configurations.

## Follow-up Items
Review the revised appearance during ordinary play. Full check and GL reruns passed, including 4,592 lighting checks. A live smoke rerun captured day, night, pulse, and player movement with the updated renderer.
