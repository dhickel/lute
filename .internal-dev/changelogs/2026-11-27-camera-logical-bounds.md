## Date
2026-11-27

## Git Commit

b92f56fffd86ca11008b083c6fd4d7df78dc9306

## Change Summary
Follow the center of the player's authored logical bounds and center map axes that fit inside the viewport. Preserve camera position on resize.

## Files
CameraSystem, GameCore, and camera, travel, and screen lifecycle tests.

## Behavioral Impact
Framing uses logical bounds rather than the padded animation canvas. Large maps remain clamped at their edges.

## Specification Impact
None. This corrects camera geometry without changing character dimensions.

## Risks
Unclamped framing shifts by half a world unit on each axis for the current player. Debug overlay viewport handling is separate.

## Follow-up Items
Camera, trigger debug, map travel, screen lifecycle, menu, and Hoard checks passed. The composite melee-contact failure reproduced on the baseline.
