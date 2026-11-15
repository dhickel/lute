# Animation system review

## Scope

Review of character animation selection, generic sprite playback, combat integration, and rendering in `core/src/main/java/io/mindspice/lute/engine/`. Based on direct code inspection; tests were not run. This records the implementation observed during the review, not a proposed contract.

## Findings

### Flow and responsibilities

- `EntityFactory` gives the player `AnimationPlayer`, `Animation2D`, `Graphic`, and `Facing` components. Animated environment objects can use `Animation2D` without `AnimationPlayer`.
- `AnimationPlayerSystem` updates the finite-state machine held by `AnimationPlayer`. `AnimationState` reads `Combat` and `Move`, selecting `IDLE`, `WALK`, or `ATTACK` and changing the type, mode, and speed on `Animation2D`.
- `AnimationSystem` resolves atlas frames for the selected animation type and facing, advances playback, and writes the current region to `Graphic`. `RenderSystem` draws that region.
- `GameScreen` registers combat before animation selection and playback, allowing the selector to observe a newly started attack in the same engine update.
- Combat is authoritative: `CombatSystem` sets the attack state and ends it based on weapon duration; animation observes that state. Damage is driven by Box2D sensor contacts, not animation frames. Playback speed and combat duration are independent; existing combat tests cover early or late animation completion.

### Cache and shared playback state

- `AnimationSystem` caches animations by atlas asset, atlas key, animation type, and direction, but creates the cached object using the first entity's frame duration. Two entities with the same cache key but different frame durations can therefore share the wrong duration.
- Cached LibGDX `Animation` objects are mutable: each processed entity sets the shared object's play mode. The current frame is calculated immediately after setting the mode, but a later entity can change the object retained by the first. In particular, `Animation2D.isFinished()` consults the shared object.

### Clarity and component requirements

- `AnimationPlayer` holds the selector state machine, not the frame player. `AnimationPlayerSystem` updates that state machine; `AnimationSystem` does frame playback. The names obscure this division.
- `AnimationState` and `Animation2D.AnimationType` both represent idle, walk, and attack. The current FSM performs a simple priority selection, although its enter/exit callbacks also manage mode and speed.
- `AnimationPlayerSystem` matches `AnimationPlayer` alone, while state transitions assume `Animation2D` exists. The player factory currently supplies both, but the system family does not express the dependency.
- The `keyFrames` configuration value is used as a frame rate (`frameDuration = 1 / keyFrames`), while the actual frame count comes from atlas regions. The name is misleading.

## Risk Assessment

- Different configured frame rates on entities sharing a cache key can produce incorrect playback. Shared mutable play mode also makes playback state harder to reason about across entities.
- Invalid component assembly may fail during an animation transition. Independent combat and animation timings can produce a finished animation during an active attack or cut off an unfinished animation; this is current tested behavior, not necessarily a defect.
- Selection depends on system order for same-update response. Reordering systems can change timing.

## Recommendations

1. Correct cache identity for frame duration and avoid per-entity playback settings on a shared mutable animation object; alternatively share frame data while keeping playback state per entity.
2. Make the selector's required components explicit.
3. Rename selection-side components and systems to distinguish character animation selection from generic playback.
4. Consider replacing the FSM with direct selection only if current simple behavior is the actual requirement; retain it if meaningful state-specific transitions justify it. Apply selection changes only when needed so playback does not restart every frame.
5. Keep generic animation playback separate from player-specific combat and movement selection; environment objects also use playback.
6. When changing Tiled properties and consumers together, consider renaming `keyFrames` to reflect frames per second.

## Follow-ups

- Validate cache changes with multiple entities sharing atlas/type/direction but using different frame rates and play modes.
- Decide separately whether future combat requires explicit wind-up, active, and recovery timing; do not assume animation frames should become authoritative for damage.
- Relevant source: `systems/AnimationSystem.java`, `systems/AnimationPlayerSystem.java`, `animation/AnimationState.java`, `components/Animation2D.java`, `components/AnimationPlayer.java`, `systems/CombatSystem.java`, and `screen/GameScreen.java`. Existing coverage includes `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`.
