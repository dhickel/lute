# Hoard archer standoff

## Date
2026-11-24

## Git Commit

6e549a23fadb8769e296d0405a116717ef901bd9

## Change Summary
Double the undead archer's detection radius to 320 pixels. Archers hold and fire within half their detection radius; beyond that, they approach between shots and stop to draw and release.

## Files
Actor AI and movement systems, combat and awareness tests, composite-character spec, asset authoring guide, and local enemy data.

## Behavioral Impact
Archers use the normal bow cooldown and still require live sight to release. The undead archer holds at 160 pixels or closer; melee enemy detection is unchanged.

## Specification Impact
Updated the archer standoff contract and local fixture instructions.

## Risks
Runtime assets must be regenerated after changing authored data. Core checks and desktop arena tests passed.

## Follow-up Items
None.
