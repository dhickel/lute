# Optional Character Equipment

## Date
2026-11-18

## Git Commit

e50a8d938e44931fedfc6f19c954055df502bf30

## Change Summary
Made player and NPC armor slots independently optional, retained equipped-item validation, and verified base-only and single-slot rendering.

## Files
- `PlayerData.java` and `EntityFactory.java`: optional loadout slots and shared player/NPC equipment validation.
- `DataLoaderTest.java`, `CombatSystemTest.java`, and `CompositeGameTest.java`: JSON, runtime validation, and rendering coverage.
- `README.md`, `.internal-dev/specifications/composite-characters.md`, and `.internal-dev/knowledge/composite-sprite-assets.md`: optional-equipment contract.

## Behavioral Impact
Players and NPCs can wear any combination of head, body, hand, and foot armor, including none. The demo NPC still starts without a helmet. Player loadout entries may be omitted or null. Enemy baked armor is unchanged.

## Specification Impact
The composite-character specification now distinguishes optional slots from default equipment and records the validation rules for equipped items.

## Risks
Equipped item IDs still require matching atlas regions. No changes to atlas generation were needed.

## Follow-up Items
`./gradlew --offline check :lwjgl3:compositeRenderTest :lwjgl3:mapTravelTest` passed, including fully unequipped and single-slot rendering on both the player and NPC.
