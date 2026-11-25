# Tune Hoard archer frequency and AI attack delays

## Date
2026-11-25

## Git Commit

3b4373e4a8d108bc89f8aceb35be09ab1ec8692a

## Change Summary
Give each unlocked Hoard archer half the spawn weight of each melee type. Set base AI pre-commit waits to 25–100 ms for melee and 150–300 ms for archers.

## Files
`BehaviorGraphs.java`, `HoardSystem.java`, awareness and Hoard arena tests, and related specifications and knowledge.

## Behavioral Impact
Dredge remains separate from weighted waves. Wave size, timing, XP, animation windups, cooldowns, and recovery are unchanged.

## Specification Impact
Updated Hoard roster weights and base AI decision timings.

## Risks
Shorter decision waits give the player less time before a melee enemy commits.

## Follow-up Items
Hoard run, awareness, combat, live arena, and map travel tests passed. `composite-characters.md` still has a pre-existing stale Dredge HP example; see the current scaling rule in `decisions.md`.
