# Animation frame intervals and playback isolation

## Date
2026-11-15

## Git Commit

bd7e9ef95a38d913057bd0a3824606ad04b6126b

## Change Summary
Use player and weapon JSON frame intervals and isolate mutable playback per entity while sharing atlas frames.

## Files
Player and weapon data, animation components and systems, entity composition, tests, and animation decision.

## Behavioral Impact
Player idle and walk use the player interval; attacks use the equipped weapon interval and speed. Environmental animations retain their Tiled frame rates.

## Specification Impact
Updated the animation-driven attack lifetime decision to describe interval ownership and validation.

## Risks
Weapon attack timing changes with the authored interval. The pre-existing input system regression is tracked as GitHub issue #6.

## Follow-up Items
None.
