# Permanent daylight in Hoard

## Date
2026-11-24

## Git Commit

f5029c4f9454c9b3c1c5586ae89cb3c681e0ed7d

## Change Summary
Remove the Hoard day/night cycle, world-clock display, and night-only wave bonus.

## Files
Hoard rules and systems, world clock, gameplay HUD and rendering, Hoard tests, and `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
Hoard stays in daylight and pauses the session world clock. The clock resumes outside Hoard. Round countdowns and active survival time are unchanged.

## Specification Impact
Updates the Hoard and session world clock contracts.

## Risks
Hoard rules, the world clock, and the desktop arena integration tests passed.

## Follow-up Items
None.
