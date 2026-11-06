# Initialize animation on its first update

## Date
2026-11-05

## Git Commit

5739fcf6d74a436dfe85e9cd6481e4b5594e721b

## Change Summary
Added `this.dirty = true` to the `Animation2D` constructor. No other application code was changed.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`

## Behavioral Impact
The first animation-system update loads the animation even when its initial facing direction matches the component default. Previously both dirty and direction-change checks were false, leaving animation null at `AnimationSystem.java:44`.

## Specification Impact
Specification Impact: none. This repairs initialization rather than changing the intended animation behavior.

## Risks
Minimal: initialization now follows the existing dirty/update path.

## Validation
- Reproduced the original null dereference using a temporary, graphics-free Java harness that invokes the actual `AnimationSystem.processEntity` with an overridden atlas-loading boundary.
- `./gradlew :core:compileJava --console=plain` passed.
- The same harness passed after the fix for all four facing directions, including second-update animation reuse and dirty-flag clearing.
- No GUI gameplay run was performed.

## Follow-up Items
The modified component was already untracked, alongside unrelated staged and unstaged work. The one-line repair remains in the working tree rather than committing the user's entire new component or changing their index.
