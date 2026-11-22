# Widen wander timing ranges

## Date
2026-11-22

## Git Commit

52a3aa243647787cd576f1aa256716d9f46e26ca

## Change Summary
Adjusted wander timing and its focused tests.

## Files
- `ActorAi.java`
- `AwarenessSystemTest.java`
- `MapTravelTest.java`
- `.internal-dev/specifications/composite-characters.md`
- `.internal-dev/specifications/decisions.md`

## Behavioral Impact
Wander pauses last 0.5–5 seconds and walks last 1–5 seconds. Turning behavior is unchanged.

## Specification Impact
Updated both living specifications to reflect the new timing ranges.

## Risks
Randomized runs do not cover every timing sequence; subjective visual quality remains unverified.

## Follow-up Items
The focused awareness and desktop map travel test reruns passed. Manual playtesting remains open.
