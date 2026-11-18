## Date
2026-11-18

## Git Commit

88ae659aef7478c706c7e9582e846389ad42cde6

## Change Summary
Moved the iron arrow definition to `projectiles.json`, added an authored lifetime cap, and made the swept triangle's current bounds visible in the debug overlay. Recorded the decision to retain swept-triangle collision and corrected the stale statement that bow projectile execution is deferred.

## Files
- `core/src/main/resources/projectiles.json` and `ProjectilesData.java`: projectile definitions and validation, replacing the arrow-only resource and loader.
- `ArrowFlight.java`, `ArrowTipSweep.java`, `CombatSystem.java`, `TriggerDebugRenderSystem.java`, and `RenderSystem.java`: flight, collision, debug rendering, and atlas lookup.
- Core, LWJGL3, and texture packer test sources: schema, lifetime, debug geometry, and atlas checks.
- `.internal-dev/specifications/decisions.md` and `composite-characters.md`: collision decision and authoring contract.

## Behavioral Impact
The bow uses `projectiles/IronArrow` from the shared weapons atlas. In-flight arrows show their current triangular hitbox in yellow and expire after five seconds by default, or sooner on impact or arrival. The last collision sweep stops at the expiry point.

## Specification Impact
Records the swept-triangle tradeoff and specifies projectile configuration, units, lifetime, and arrow asset paths. Corrects the obsolete bow deferral.

## Risks
Existing generated atlases must be rebuilt against the relocated projectile sprite. Swept collision costs more than a ray or per-frame overlap, but the overlay performs no additional collision test.

## Follow-up Items
Revisit collision cost only if profiling many projectiles indicates a problem. Runtime asset generation, `:util:texturePackerToolTest`, `:core:check`, and `:lwjgl3:gameScreenLifecycleTest` passed; no manual visibility or stress check was performed.
