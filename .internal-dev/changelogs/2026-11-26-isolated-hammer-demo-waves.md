## Date
2026-11-26

## Git Commit

c3138a1a07252140db8d71973744567a0a0b17df

## Change Summary
Give the Hammer Demo its own unscored map instance and central wave timer while sharing Hoard survival mechanics.

## Files
- `HoardSystem.java`, `TiledSystem.java`, `Controller.java`, `GameplayHud.java`, `RenderSystem.java`, and `GameScreen.java`
- `HoardHammerDemoTest.java`
- `.internal-dev/specifications/decisions.md`

## Behavioral Impact
The demo uses Hoard stamina, sprinting, HP, and kill-healing chance. The player starts with the basic hammer equipped and all weapons available. Ten undead spawn at clear central positions every 15 simulation seconds, starting at 15 seconds. Fresh instances reset the timer and stamina. The demo has no Dredge, regular rounds, or scoring.

## Specification Impact
Updated the Hammer exception and demo decision. Scored Hoard keeps its existing wave, score, and inventory rules.

## Risks
Blocked central positions can reduce a wave rather than spawning overlapping enemies.

## Follow-up Items
None. Desktop Hammer Demo, scored Hoard, and menu regressions passed.
