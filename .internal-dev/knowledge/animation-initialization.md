# Animation initialization

## Topic
First-update initialization of `Animation2D`.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/AnimationSystem.java`
- `.internal-dev/changelogs/lute/2026-11-05-animation-first-update.md`

## Key Takeaways
- A Java boolean field defaults to false. When animation starts null and both stored and actual facing are DOWN, the animation system's dirty-or-direction-change condition will not initialize the animation unless dirty starts true.
- The constructor now marks the component dirty; `setAnimation` clears the flag after loading.
- Initialization alone does not cover state transitions: `setType` must also mark the component dirty. Otherwise the FSM can enter IDLE correctly while the renderer keeps WALK frames until facing changes. A regression check should stop without turning and restart in the same direction, not merely verify first-frame loading.
- This failure can be reproduced without a graphics context by subclassing `AnimationSystem`, overriding `updateAnimation` to provide a single empty `TextureRegion`, and exposing `processEntity` to a temporary test harness. This tests initialization control flow, not real atlas loading.

## Project Relevance
The original failure occurred at `animation.setPlayMode(...)` on the first update. A focused regression check verified all facing directions and that a second unchanged update reuses the animation.

## Open Questions
None within the null-animation initialization repair.
