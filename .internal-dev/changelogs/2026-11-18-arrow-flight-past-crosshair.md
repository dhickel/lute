## Date
2026-11-18

## Git Commit

2994c86da4210ad2120d0b60dfd5e14823ce1613

## Change Summary
Arrows retain their shallow arc through the crosshair, then continue straight until impact or lifetime expiry.

## Files
- `ArrowFlight.java`: preserve the original aim direction beyond the arc and sweep both segments when a frame crosses the aim point.
- `TriggerDebugRenderSystemTest.java`: verify continuation, expiry, centered aim, and impact beyond the crosshair.
- `.internal-dev/specifications/composite-characters.md`: document the aiming and lifetime behavior.

## Behavioral Impact
The crosshair no longer ends flight. Centered aim fires in the owner's facing direction. Lifetime and collision continue to apply on both flight segments.

## Specification Impact
The combat specification now describes the crosshair as an aim point rather than a despawn point.

## Risks
At the aim point the arrow transitions from the existing curved path to straight travel. Long-frame collision coverage is tested across that transition.

## Follow-up Items
None. Core checks passed with matching source/runtime copies isolated for data-loader validation; focused native physics and desktop lifecycle and map travel checks also passed. Local projectile tuning was preserved.
