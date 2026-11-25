# Weapon timing and enemy movement review

## Scope
Astra audited all 12 authored weapon definitions against the directional sprite frames. Root traced enemy movement from authored JSON to the physics body and checked all nine Hoard enemy types.

## Findings
Player primary hit onset / same-slot cooldown after tuning: basic sword 125/200 ms; cutlus 150/250 ms; longswords 200/360 ms; axes 500/800 ms; hammers 600/960 ms. Melee secondary cues were aligned to their sprites; bow primaries were unchanged. Hit onset is not guaranteed damage. Native probes stepped all 21 melee slots at 30, 60 and 120 Hz before accepting a stationary same-slot repeat.

`MapActorData` reads `move_speed`, `EntityFactory` passes it to `Move`, and `PhysicMoveSystem` normalizes direction and multiplies by that speed for Box2D velocity. The live arena checked all nine definitions, including Dredge at 2 and Viper at 4.5 world units per second. Attack locks and stopping can still set velocity to zero.

## Risk Assessment
Stationary cross-slot replacement can still cut off a hit under the existing independent cooldown contract. The asset pass does not change this or guarantee a hit at arbitrary update rates. The catalog-count test failed on the same assertion before and after the asset change.

## Recommendations
Playtest commitment lengths in mixed combat. If stationary cross-slot cancels also need to preserve hits, agree on a cancellation rule before changing the runtime.

## Follow-ups
Raw and local runtime weapon data were verified identical. Combat, input, awareness, live arena and map travel checks passed with the published data.
