# Move attack speed handling to animation

## Date
2026-11-13

## Git Commit

268e49e5643e749f196210f727bd03aafef4506b

## Change Summary
Moved attack animation speed application and restoration from combat state into the attack animation state and component.

## Files
- `core/src/main/java/io/mindspice/lute/engine/animation/AnimationState.java`
- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`
- `core/src/main/java/io/mindspice/lute/engine/components/Combat.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`

## Behavioral Impact
The animation state applies weapon attack speed on entry and restores the prior speed on exit. Restoration after combat removal occurs on the next animation state update.

## Specification Impact
None; this changes ownership of existing animation behavior without changing a living specification.

## Risks
Animation speed restoration requires the animation state machine to update after combat ends.

## Follow-up Items
None.
