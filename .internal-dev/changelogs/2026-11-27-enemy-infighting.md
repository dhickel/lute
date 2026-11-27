## Date
2026-11-27

## Git Commit

f6c5058e8a704bb67eff67d87d92fd777243250d

## Change Summary
Added a required enemy-infighting percentage for ordinary gameplay.

## Files
- `GameConsts.java`, `GameScreen.java`, `CombatSystem.java`, and `AwarenessSystem.java`
- Settings, awareness, and arena regression fixtures
- Composite character and Hoard specifications and the asset service guide

## Behavioral Impact
At the authored 30% setting, an eligible nonlethal hit between enemies can make the victim retaliate. Zero disables retaliation and 100 always provokes an eligible target. Damage and existing player and NPC retaliation rules remain unchanged.

## Specification Impact
Documents the required setting and retaliation rules, including Dredge's normal line-of-sight checks for enemy attackers.

## Risks
Existing local asset settings must add the required field. The coordinated Dredge volley test isolates the guards' hurtboxes so the intended whiff cannot become an infighting test by accident.

## Follow-up Items
None. Focused settings, awareness, map sensor, combat, and Hoard arena tests passed.
