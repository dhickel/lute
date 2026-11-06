# Refresh animation frames on state changes

## Date
2026-11-05

## Git Commit

5739fcf6d74a436dfe85e9cd6481e4b5594e721b

## Change Summary
Added `this.dirty = true` in `Animation2D.setType`. This is the only application-code change in this repair.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`
- `.internal-dev/knowledge/animation-initialization.md`

## Behavioral Impact
IDLE/WALK state transitions now request fresh animation frames without needing a facing-direction change. The state machine already entered IDLE on stopping; the render animation was not being refreshed.

## Specification Impact
Specification Impact: none. Restores the existing intended animation-state behavior without changing APIs or state transitions.

## Validation
- A temporary Java harness using the actual FSM and animation-system processing reproduced failure to reload idle frames before the change.
- `./gradlew :core:compileJava --console=plain` passed.
- The transition harness passed after the change: stopping, restarting in the same direction, rooted idle, and unchanged idle-state reuse. Atlas loading was replaced at its boundary with distinct test frames; no graphics context was required.
- The earlier first-update regression harness still passed for all four facing directions.
- `git diff --check` passed. No GUI gameplay run was performed.

## Risks
Minimal: state changes use the existing dirty/update path.

## Follow-up Items
The affected component remains a pre-existing untracked file; no code commit was made to avoid including the user's full new file or unrelated staged changes. The patch remains in the working tree.
