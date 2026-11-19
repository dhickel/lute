# Session World Time

## Date
2026-11-19

## Git Commit

31bc58c1350f1ba1965e7693e40641c1c5c9a688

## Change Summary
Added a screen-owned, queryable simulation clock with a configurable day length and focused clock and lifecycle coverage.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/WorldTimeSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `core/src/main/java/io/mindspice/lute/engine/singletons/GameConsts.java`
- `core/src/test/java/io/mindspice/lute/engine/systems/WorldTimeSystemTest.java`
- `core/build.gradle`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/GameScreenLifecycleTest.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/MapTravelTest.java`
- `.internal-dev/specifications/decisions.md` and `.internal-dev/specifications/horizon-ideas.md`

## Behavioral Impact
The enabled clock starts at day zero, 06:00 and advances before default-priority systems. Optional numeric `dayLengthMinutes` defaults to 24 simulation minutes per game day, or 60 in-game seconds per simulation second; fractional values are accepted. Settings reject explicit null, non-numbers, non-finite or non-positive values, and an unusable derived rate. `GameConsts` passes the setting to the clock. The clock survives map travel and hide/show, stops during hidden or paused rendering, and resets with a new screen. Rendering clamps simulation delta; the clock is not wall time or persisted. No scheduled events, AI, day/night, or lighting are included.

## Specification Impact
Recorded the session clock contract in decisions and uncommitted scheduled-event and day/night directions in horizon ideas.

## Risks
Elapsed game time follows clamped simulation time rather than real elapsed time. The final combined focused run, `./gradlew --offline :core:worldTimeSystemTest :lwjgl3:gameScreenLifecycleTest :lwjgl3:mapTravelTest`, passed after the overflow fix: 22 clock checks including overflow, 181 lifecycle checks, and 91 map-travel checks. `git diff --cached --check` passed. A controlled-fixture broad core check passed before the overflow fix; the normal local settings caused `DataLoaderTest` to fail because `viewWidth` is 32.0 rather than the expected 21.3. No final broad core check was run after the fix.

## Follow-up Items
Consider scheduling and day/night separately; neither is committed scope. Account for the local settings mismatch before claiming a normal-environment core check passes.
