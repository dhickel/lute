# Full-flight arrow arc

## Date

2026-11-19

## Git Commit

da22f16c2eca536cd9db0a63c30d3998bd176665

## Change Summary

Keep the arrow on a continuous parabola through the exact reachable crosshair and until impact or lifetime expiry.

## Files

- `ArrowFlight.java`: continuous trajectory and lifetime-clipped collision sweeps.
- `ArrowFlightTest.java` and `CombatSystemTest.java`: native Box2D trajectory and collision regressions.
- `composite-characters.md`: updated flight contract.

## Behavioral Impact

The arrow keeps curving past the crosshair instead of straightening. The crosshair remains an exact waypoint when reachable, and `arc_height` caps the vertical offset from the aim line.

## Specification Impact

Replaces the former straight-extension contract with a full-flight curve and clipped sweeps.

## Risks

Post-crosshair hit locations change by design; the tests exercise a hurtbox below the old straight extension.

## Follow-up Items

None.

## Validation

Combat checks and LWJGL3 test compilation passed. `:lwjgl3:compositeRenderTest` passed 11,854 real GL checks.
