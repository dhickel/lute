# Game data startup and delayed weapon hits

## Date
2026-11-13

## Git Commit

75ed470de5c72265aa07ca5b0351d80c6d9eb9aa

## Change Summary
Rename CombatData to GameData, separate player stats and loadout, load immutable player and weapon data at startup, and inject it into EntityFactory. Add contact-relative hitDelay and shared attack pivot constants.

## Files
`assets/data/player.json`, `assets/data/weapons/sword.json`, GameCore, GameData, GameConsts, Combat, CombatSystem, EntityFactory, GameScreen, and focused tests.

## Behavioral Impact
Sword damage is delayed 0.1 seconds after contact and persists past sensor expiry. Nonpositive finite damage is accepted but does not affect health. Attack pivot is fixed in sprite-local pixels.

## Specification Impact
None: existing living specifications do not define combat data layout or timing. These changes implement the user's follow-up request.

## Risks
The full suite has a previously reproduced unrelated input movement-event failure.

## Follow-up Items
None for this scope.
