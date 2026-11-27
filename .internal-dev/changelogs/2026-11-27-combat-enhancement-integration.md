## Date
2026-11-27

## Git Commit

8b8127a7c5b1d1cf2c2185708081bb93117433ce

## Change Summary
Integrate the combat-enhancement branch with master’s audit fixes, enable authored melee secondaries for enemy AI, and document where to demonstrate weapon changes.

## Files
`AGENTS.md`, the combat and AI systems, arena and screen composition, asset loader tests, desktop regressions, and living combat specifications.

## Behavioral Impact
The main menu offers scored Hoard and a separate Test Area. Enemy melee AI can choose a ready authored lunge or twirl. Dredge keeps his authored hammer and lunges against retreating targets or to close distance when in range.

## Specification Impact
Documents Test Area’s role and enemy secondary selection. The required attack animation schema remains `animation.anim_key`.

## Risks
Ignored raw and generated asset packs must match the authored attack and enemy schema.

## Follow-up Items
Focused data loading, awareness, combat, sensors, Hoard, Test Area, menu, travel, lifecycle, composite rendering, and weapon editor checks passed.
