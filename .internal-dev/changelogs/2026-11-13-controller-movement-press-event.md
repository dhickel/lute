# Controller movement press event

## Date
2026-11-13

## Git Commit

21fde1a7cc99e4e9309e5c7e8c34a062e208ac3a

## Change Summary
Emit the player movement event on movement key presses, not releases.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/ControllerSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/input/InputSystemTest.java`

## Behavioral Impact
Movement releases still clear direction without triggering the movement sound; presses for controllers with a Move component emit one event.

## Specification Impact
None. The existing input transition contract is unchanged.

## Risks
The player event applies to all controller entities with Move, consistent with the existing controller scope.

## Follow-up Items
None.
