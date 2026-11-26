# Ease Hoard sprint acceleration and release

## Date
2026-11-25

## Git Commit

733fc7b870c441d825a5b491e98cacab9d74d78e

## Change Summary
Replace the instantaneous sprint boost with a short fixed-step acceleration and deceleration.

## Files
`HoardSystem.java`, `GameScreen.java`, `HoardArenaTest.java`, `README.md`, and the Hoard sprint decision.

## Behavioral Impact
Holding B builds to the existing 1.6× top speed over 250 ms; release or exhaustion eases back to walk speed in up to 100 ms. Walk playback scales with the boost. The release tail still costs stamina while actually moving and stamina remains. Non-Hoard movement is unchanged.

## Specification Impact
Updates the Hoard sprint decision with the ramp and release contract.

## Risks
The shorter dodge boost is mechanically verified but still needs controller playtesting.

## Follow-up Items
Focused arena, input, travel, screen lifecycle and combat checks passed with the previously validated enemy asset fixture. Current local Dredge health authoring was not changed.
