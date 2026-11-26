# Player melee facing while stationary

## Date
2026-11-24

## Git Commit

dd9c7d5e08367ddb9a67d7190426c58ba382afba

## Change Summary
Allow directional input to turn the player during melee attacks, including when attack presses are repeated.

## Files
Player facing, combat and animation components and systems in `core/`, synthetic combat regression tests, and the composite-character specification.

## Behavioral Impact
Melee animation and hit geometry follow the updated facing without moving the player, resetting playback, or allowing a second hit on the same target within a swing. Bow aiming, shot trajectory, and NPC attack commitment remain unchanged.

## Specification Impact
Updated the composite-character specification with the player melee steering and stationary movement contract.

## Risks
Covered stationary movement, input ordering, attack continuity, and affected combat behavior with synthetic tests. No manual playtest was run.

## Follow-up Items
None.
