# Player attack movement cancel

## Date
2026-11-24

## Git Commit

dd9c7d5e08367ddb9a67d7190426c58ba382afba

## Change Summary
Allow held movement to cancel lingering player attack recovery after cooldown and give a ready follow-up a one-step movement handoff.

## Files
Player combat state, input queue and controller, combat and physics systems, combat regression tests, and the composite-character specification.

## Behavioral Impact
Melee keeps its windup and hit. With no queued follow-up, movement continues normally after cancellation. With a ready follow-up, the player gets one fixed physics tick of movement before the next attack, even when physics runs multiple ticks in a frame. Bow draw still steers aim and committed arrows persist. NPC combat is unchanged.

## Specification Impact
Documented movement cancellation, its cooldown and hit conditions, and the one-request buffer.

## Risks
Focused automated checks passed; player feel remains subject to the requested manual playtest.

## Follow-up Items
Leave the pull request unmerged pending user playtest.
