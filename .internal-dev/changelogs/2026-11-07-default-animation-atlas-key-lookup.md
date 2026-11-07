# Default animation atlas key lookup

## Date
2026-11-07

## Git Commit

e3e46d1c988c8125238dfd0b68a26cc160d478db

## Change Summary
AnimationSystem uses the atlas key directly for AnimationType.DEFAULT; IDLE and WALK retain their state/direction suffixes.

## Files
- core/src/main/java/io/mindspice/lute/engine/systems/AnimationSystem.java

## Behavioral Impact
Nondirectional tree frame sequences resolve without an erroneous /_down suffix.

## Specification Impact
none. This corrects key resolution for the existing DEFAULT enum value without adding an API or changing player naming.

## Risks
The user's other in-progress animation changes remain outside this focused change.

## Follow-up Items
Runtime gameplay was not exercised. Validation: core:compileJava returned exit code 0; focused key checks confirmed tree DEFAULT, player IDLE/down, and player WALK/up names exist in the packed atlas; git diff --check passed.
