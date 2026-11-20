# Restore short-shot arrow lift

## Date

2026-11-19

## Git Commit

058b7fa87d0eceacd311f2b59165573b3b8c6346

## Change Summary

Preserved visible lift for nearby arrow shots while continuing past reachable crosshairs on a tangent-matched descending curve.

## Files

- `ArrowFlight.java`: scales lift around the distance to the crosshair and continues curving past it.
- `ArrowFlightTest.java` and `TriggerDebugRenderSystemTest.java`: cover short shots and collisions on the continued descent.
- Composite character specification and combat sensor knowledge record the updated trajectory contract and regression lesson.

## Behavioral Impact

Increasing `arc_height` visibly changes short shots again. The arrow still crosses an unobstructed, reachable crosshair and does not switch to a straight path. Projectile data and schema are unchanged.

## Specification Impact

Supersedes the prior lifetime-normalized parabola and full-flight offset cap described in the previous entry.

## Risks

The new descent changes which fixtures an arrow can hit after crossing the aim line.

## Follow-up Items

Manual visual verification was not run.

## Validation

`./gradlew --offline --continue :core:combatSystemTest :core:triggerDebugRenderSystemTest :lwjgl3:compositeRenderTest` passed, including 11,854 real GL checks.
