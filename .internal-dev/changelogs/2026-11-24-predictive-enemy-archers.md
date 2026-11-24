# Predictive enemy archers

## Date
2026-11-24

## Git Commit

dd9c7d5e08367ddb9a67d7190426c58ba382afba

## Change Summary
Add the opt-in `wander_archer` decision profile.

## Files
Enemy decision graphs, bow combat and projectile systems, factory and rendering, focused combat tests, and the composite-character specification.

## Behavioral Impact
Bow-equipped archers fire directly at stationary targets, lead moving targets using curved-flight time, and cancel a draw on sight loss. Non-projectile weapons retain ordinary pursuit behavior; AI aim does not display a crosshair.

## Specification Impact
Updated the composite-character specification.

## Risks
Focused combat, awareness, and input tests passed with a branch-compatible asset fixture. No manual map playtest was performed.

## Follow-up Items
Validate the authored Hoard arena integration on its own branch.
