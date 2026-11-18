# Bow Draw and Release Animation

## Date
2026-11-17

## Git Commit

968aae4ae7c6ff42f858cdf09c85de5e103a0093

## Change Summary
Revised bow draw and release animation while preserving immediate projectile firing.

## Files
Animation state and playback, bow aim, combat and controller systems, entity asset validation, combat and desktop gameplay tests, and the composite-character specification.

## Behavioral Impact
Holding draws through frames 00–04 and holds 04. Release fires once immediately and starts frames 05–07, facing the target’s dominant axis; vertical wins ties and a centered target retains facing. Quick release follows the same release path. Cancellation fires no shot; melee is unchanged.

## Specification Impact
Updated the composite-character attack and validation contract for draw, release, and facing.

## Risks
Live play feel for the animation revision and physical-device behavior remain unconfirmed.

## Follow-up Items
Focused automated input, combat, desktop gameplay, composite rendering, and lifecycle validation passed. Manual review of the revised animation remains pending.
