# GameData combat sensors and debug rendering

## Date
2026-11-13

## Git Commit

61f3e055de0e154d664ca92cbab397412718460c

## Change Summary
Implemented the combat sensor and debug rendering behavior from commit `75ed470`, adapted to the existing GameCore GameData and weapon map.

## Files
Combat, health, weapon geometry, player factory, input, animation, physics movement, screen, Tiled and trigger debug systems; Gradle test tasks and focused tests under `core/`.

## Behavioral Impact
Player health, movement speed and equipped weapon use loaded game data. Attack input creates directional weapon sensors, coordinates movement and animation, and applies contact damage. Debug rendering shows sensors and Tiled triggers.

## Specification Impact
None; no existing living specification defines player combat or trigger debug rendering.

## Risks
The existing input system test fails identically on the unchanged baseline. Desktop startup was exercised, but interactive attack visuals were not manually verified. The existing hitDelay field is not used by the referenced commit's combat behavior.

## Follow-up Items
Resolve the independently reproduced input test failure separately.
