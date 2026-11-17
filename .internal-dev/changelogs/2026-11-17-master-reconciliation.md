## Date
2026-11-17

## Git Commit

fbe0b18232b015b21dd9fe12072cb222d71e9c9e

## Change Summary
Merge `origin/master` into local `master` without rewriting either history. Resolve the input documentation conflict in favor of the startup configuration description from PR #24, while retaining the local gameplay flow and screen lifecycle corrections.

## Files
- `docs/input-system.md`: preserve the input settings contract and the local documentation corrections.

## Behavioral Impact
None from the conflict resolution. Both branches' existing code changes are retained. `./gradlew check --console=plain -Dorg.gradle.logging.level=lifecycle` passed in an isolated worktree, including 1,312 input checks and the texture packer tests.

## Specification Impact
Specification Impact: none. The resolution documents the input settings contract already merged in PR #24.

## Risks
No unresolved merge conflicts. Validation did not include interactive gameplay.

## Follow-up Items
None.
