# Hoard reinforcement and night tuning

## Date
2026-11-24

## Git Commit

b914dd91400ce5db4cbb65c95fa75b9b82729427

## Change Summary
Make each reinforcement batch roll 25–50% of the five-enemy baseline before round scaling and the existing night bonus. Set arena night to 20:00 inclusive through 06:00 exclusive.

## Files
`HoardRun.java`, core and desktop hoard tests, `README.md`, `docs/asset-service.md`, `docs/input-system.md`, and `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
Opening waves and the 20–40 second reinforcement interval are unchanged. New sessions still start at 06:00, now in daylight. The world clock is not reset on map travel.

## Specification Impact
Updated the reinforcement budget and overnight lighting and spawning rules.

## Risks
Individual whole-enemy counts vary with fractional carry and available space. Focused core and desktop arena tests passed.

## Follow-up Items
None.
