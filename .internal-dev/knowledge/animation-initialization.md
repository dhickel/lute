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
- Frame cues must use a reached-boundary check with a per-attack consumed flag, rather than current-frame equality. A large delta can cross a sound frame without displaying it. Frame 0 is emitted on initialization; a zero-delta update must not emit it twice.
- `AnimationPlayerSystem` must require both `AnimationPlayer` and `Animation2D`. Removing the animation during an active attack otherwise leaves its FSM trying to exit through a missing component. Combat's family-removal callback separately cleans up its optional sensor, including before the hit frame.
- Validate required attack data at the JSON boundary, not again in runtime record constructors or internal assignments such as `configureAttack`. Noncombat animations can lack attack metadata, and pending attacks have no fixture until their hit frame; these are meaningful optional states.
- Cue limits depend on the loaded atlas, so `EntityFactory` checks hit and sound frames and weapon geometry in every facing direction at composition, where the sprite anchor is known. It caches immutable validated catalogues by definition and atlas identities, actor prefix, anchor, and unit scale; new inputs are checked again and failures are not cached. Actor components remain independent.
- Animation does not poll for atlas replacement each frame. Shared character frame caches refresh on atlas identity change when a new animation is installed; environmental frames belong to their entities. The compositor keeps its layer cache until disposal. Stop existing consumers before unloading or replacing an atlas, then rebuild them; live replacement is not supported.
- Integration tests should derive authored weapon cue frames and timing from the loaded data instead of fixing tutorial sword tuning in assertions. Keep independent nonzero-frame fixtures so a zero-valued authored cue cannot conceal broken JSON field mapping.
- In bow draw tests, read the draw frame only after the first engine update installs `shot_00`; the preceding idle frame index can otherwise skip the full-draw loop.
- Animation now owns attack progress, while combat retains its hit-frame threshold and fixture lifecycle. See the animation-driven attack decision in `../specifications/decisions.md`.
- Tiled nested `Animation` properties are loaded as `MapProperties`. LibGDX fills unspecified placement members from class defaults, not the tile; `FixedTmxMapLoader` preserves explicitly authored members and merges them with the tile's class values. An explicit override equal to a class default must still win.
- This failure can be reproduced without a graphics context by subclassing `AnimationSystem`, overriding `updateAnimation` to provide a single empty `TextureRegion`, and exposing `processEntity` to a temporary test harness. This tests initialization control flow, not real atlas loading.

## Project Relevance
The original failure occurred at `animation.setPlayMode(...)` on the first update. A focused regression check verified all facing directions and that a second unchanged update reuses the animation.

## Open Questions
None within the null-animation initialization repair.
