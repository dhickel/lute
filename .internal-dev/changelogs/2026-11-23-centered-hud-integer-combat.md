# Centered HUD and integer combat

## Date
2026-11-23

## Git Commit

438f4b4db8b04df8e9279039bf9011dca19d3433

## Change Summary
Center live HP and time in a responsive, full-width charcoal bar without trim. Use whole-number health and damage.

## Files
- `GameplayHud.java` and `RenderSystem.java`: style and center the readouts and render the bar without introducing a new texture owner.
- `Health.java`, `CombatWeapon.java`, `DataLoader.java`, `CombatSystem.java`, `ArrowFlight.java`, and `DamageFeedback.java`: use integer health and damage, enforce authored integers, and round landed damage once.
- Core and desktop tests cover integer damage, HUD layout and colors, render state, and health across death and travel.

## Behavioral Impact
HP and damage numbers share the green-to-yellow-to-red progression. The clock stays blue. Bow scaling and the independent ±10% hit roll are applied before rounding. Overkill displays actual health lost. Against the local 50 HP tuning, sword primary takes 5–6 hits and full-draw bow takes five.

## Specification Impact
The composite-characters specification and asset-service guide describe the revised HUD, integer combat, and local asset tuning. No new asset fields are introduced.

## Risks
Ignored asset edits do not travel with Git. Desktop validation used Java 25 and Mesa; no manual playtest was run.

## Follow-up Items
Use the asset-service guide to apply tuning in other checkouts. Provision the ignored raw test fixture if it is missing.

## Validation
All four desktop GL tasks passed. The offline full check passed in isolation after restoring the missing ignored `raw_assets/maps/test_tiled/objects/player/idle_up_02.png` fixture there. Without restoration, the same failure reproduced before this follow-up.
