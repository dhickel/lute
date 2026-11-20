# Accepted arrow flight

## Date

2026-11-19

## Git Commit

5e7880c595812194b08cac11bd7de5d22695aac7

## Change Summary

Revised arrow flight to follow one continuous parabola through reachable aim and beyond, with arc-length speed and tangent-facing orientation. Near-north lift tapers smoothly, with conditional apex expiry for transition shots that have not cleared the launch strip.

## Files

- `ArrowFlight.java`, `ArrowFlightTest.java`, and `TriggerDebugRenderSystemTest.java`
- Composite character specification, decisions, horizon ideas, and combat sensor knowledge

## Behavioral Impact

Swept collisions follow the curved trajectory; centered shots remain straight. Shots within 10° of north are straight, lift smoothly reaches full strength at 25°, and short transition shots may expire at their apex after crossing aim. Projectile schema and art are unchanged.

## Specification Impact

Updated the composite-character flight contract and recorded the accepted near-north decision and unaccepted alternatives.

## Risks

The new trajectory changes collision lanes. Sweeps approximate the curve with chords, and the focused tests cannot cover every map or aim direction.

## Follow-up Items

None required. Full project validation has not been run. The user accepted the current UP behavior in live play. `./gradlew --offline :core:combatSystemTest :core:triggerDebugRenderSystemTest :lwjgl3:compositeRenderTest` passed, including 11,854 real-GL checks.
