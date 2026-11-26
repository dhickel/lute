# Match the vision overlay and provoke enemies on rear hits

## Date
2026-11-25

## Git Commit

599bfd442f94ea8ef160fc7ecf2de1b4757c2bdb

## Change Summary
Draw the forward detection semicircle in the physics overlay. Confirmed nonlethal player melee damage now provokes ordinary enemies as well as arrows, regardless of facing.

## Files
`AwarenessSystem.java`, `CombatSystem.java`, `PhysicDebugRenderSystem.java`, awareness and map travel tests, README, and the related specifications and knowledge.

## Behavioral Impact
Rear approach alone does not trigger detection. A confirmed hit can immediately engage the enemy without bypassing attack cooldowns or committed windups. Dredge retains its full-circle detection; NPC melee behavior is unchanged.

## Specification Impact
Documents the distinction between passive vision, debug geometry, and retaliation.

## Risks
The overlay shows the detection shape, not line-of-sight occlusion. Retaliation still respects range and cover.

## Follow-up Items
Check the overlay and rear-hit response during ordinary playtesting.
