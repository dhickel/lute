# Tune weapon cadence and verify enemy movement speeds

## Date
2026-11-25

## Git Commit

ae87a2926800b3ad9f337b10f1b3ff3662f37787

## Change Summary
Align melee hit and sound frames with sprite cues and tune same-slot repeat timing. Verify authored enemy movement speeds through live Box2D velocity checks.

## Files
Local `raw_assets/data/weapons.json` and generated `assets/data/weapons.json`; `HoardArenaTest.java`; attack cooldown knowledge and the weapon timing review.

## Behavioral Impact
Short swords repeat sooner than longswords; axes and hammers retain heavier windups and longer commitments. Bow primaries and enemy movement values are unchanged.

## Specification Impact
None. Authored timings change within the existing cooldown and cancellation contract.

## Risks
A stationary attack can still be replaced by the other slot before its hit frame under the existing contract. The baseline catalog-count assertion expects three weapons and fails with twelve.

## Follow-up Items
Playtest the updated weapon timings. Confirm the intended cross-slot behavior before changing cancellation rules.
