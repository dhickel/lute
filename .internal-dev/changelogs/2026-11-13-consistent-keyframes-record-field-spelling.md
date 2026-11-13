# Consistent keyFrames record field spelling

## Date
2026-11-13

## Git Commit

b936d94f4cd05588d054b5e169e6c53f9c693ed3

## Change Summary
Renamed keyframes record components and explicit constructor parameters to keyFrames in PlayerCharProps and AnimatedPhysicObjectProps. Updated factory and regression test accessors to keyFrames().

## Files
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/PlayerCharProps.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/AnimatedPhysicObjectProps.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/test/java/io/mindspice/lute/engine/tiled/TiledAnimationPropertiesTest.java`

## Behavioral Impact
Parsing behavior is unchanged; constructor argument position already mapped the Tiled property correctly. Java callers now use the consistently spelled keyFrames() accessor. The prior crash came from a zero inherited class default, not field spelling.

## Specification Impact
Specification Impact: none. No living specification mandates the old Java accessor spelling.

## Risks
Any external callers must update from keyframes() to keyFrames(); all repository callers have been updated.

## Follow-up Items
Focused non-graphics property regression and desktop compilation passed. No game smoke run. Unrelated user edits preserved.
