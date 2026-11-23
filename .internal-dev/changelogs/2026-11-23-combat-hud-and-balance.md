# Combat feedback and gameplay HUD

## Date
2026-11-23

## Git Commit

000135a55f8598ddf6b4892172c1a19d5b1b256d

## Change Summary
Add independently randomized landed-hit damage, floating damage feedback, and a gameplay health and time HUD. Capture committed attack facing for melee and arrow feedback. Document locally authored combat tuning, which cannot be shipped in the ignored asset trees.

## Files
- `Combat.java`, `ArrowFlight.java`, `CombatSystem.java`, and `DamageFeedback.java`: commit attack facing, roll and apply damage, and manage transient hit snapshots.
- `GameplayHud.java`, `RenderSystem.java`, and `TiledSystem.java`: draw and dispose the overlay, supply live or retained health, and clear map feedback.
- Core and desktop tests: cover damage and direction, HUD and clock lifecycle, render state, and asset-driven assertions.
- `.internal-dev/specifications/composite-characters.md` and `docs/asset-service.md`: specify HUD, hit feedback and data tuning; document reproduction.

## Behavioral Impact
Each landed melee or arrow hit rolls independently within ±10% after bow draw scaling. Floating text shows actual health lost and colors from post-hit health; it rises, drifts and fades. The unlit HUD shows live health and 24-hour game time, including after death and travel. Local ignored data sets player, basic NPC and undead to 50 HP, sword primary to 10 and full-draw bow primary to 11, yielding 5–6 hits.

## Specification Impact
The composite-characters specification records the HUD, landed-hit damage and local tuning values. There is no asset schema change.

## Risks
Other checkouts will not receive ignored asset edits. Automated framebuffer checks used Java 25 and Mesa; no alternate display or manual visual playtest was run.

## Follow-up Items
Apply the documented tuning in other checkouts and run `./generate-runtime-assets.sh`.

## Validation
Independent integration validation passed offline full `check` and desktop composite render, lighting, lifecycle, and map travel tasks on Java 25 and Mesa. Raw and generated tuning JSON pairs matched and supported the 5–6-hit range.
