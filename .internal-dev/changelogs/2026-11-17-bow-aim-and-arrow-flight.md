# Bow Aim and Arrow Flight

## Date

2026-11-17

## Git Commit

e54b10a279203fc9ca8809f9a66d5e1043a35ec3

## Change Summary

Implemented held bow aiming and release-triggered arrow flight. Updated the living specifications and asset knowledge to reflect the implemented behavior and locally provisioned arrow art.

## Files

The bow aim and arrow data components, input routing, controller/combat/render systems, default arrow JSON, core combat and desktop lifecycle/render tests, and the composite-character specifications and asset knowledge.

## Behavioral Impact

Holding bow primary displays a crosshair two map tiles ahead; movement actions steer it instead of moving the player. Releasing the last held primary binding fires once. Context invalidation and other invalidating transitions cancel aim without firing. Melee remains press-triggered.

The arrow follows a shallow arc and sweeps a triangular tip against terrain and hurtboxes, ignoring its owner. Flights are cleared when their owner or combat system is removed. The default arrow uses the locally provisioned 32×32 `IronArrow.png`; missing art is not replaced by a fallback.

## Specification Impact

Updated the composite-character contract for bow input, collision, validation and atlas expectations; removed the obsolete projectile deferral; and recorded selectable arrow types as a future idea. Updated asset knowledge with the external PNG source and missing-region behavior.

## Risks

Raw assets and generated outputs are ignored. Other checkouts must obtain the same external PNG at `raw_assets/sprites/weapons/ranged/arrows/IronArrow.png` and regenerate assets. Generation can succeed without it, but rendering a shot then throws for the missing atlas region.

Offline asset generation and `./gradlew --offline :core:inputSystemTest :core:combatSystemTest :lwjgl3:compileTestJava :lwjgl3:gameScreenLifecycleTest :lwjgl3:compositeRenderTest check` passed.

## Follow-up Items

Human hardware and manual gameplay testing have not been performed. Selectable arrow types remain a future idea.
