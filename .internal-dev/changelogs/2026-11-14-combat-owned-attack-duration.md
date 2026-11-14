# Combat owned attack duration

## Date
2026-11-13

## Git Commit

85cd0cf02637aa48c359624b24e22512d461f204

## Change Summary
Define explicit, weapon-configured combat attack lifetime.

## Files
`assets/data/weapons.json`; `WeaponsData.java`, `Combat.java`, `CombatWeapon.java`, `CombatSystem.java`, `EntityFactory.java`; combat and data-loader tests; `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
Combat owns attack state and duration. Animation speed does not scale duration; `hitDelay` remains unused.

## Specification Impact
Records the combat lifetime contract and the accepted physics-tick caveat.

## Risks
Attacks shorter than a physics tick can expire without registering a hit.

## Follow-up Items
Revisit if timing, animation ownership, or physics contact guarantees change.
