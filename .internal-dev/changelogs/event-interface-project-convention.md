# Document explicit event participation interfaces

## Date

2026-11-14

## Git Commit

77f0a867164574e5c5177e2068dd16f49cb6e2ab

## Change Summary

Add a project-root `AGENTS.md` recording the user's event-system convention: classes declare event participation through `EventListener` and `EventEmitter` and use their helpers rather than replacing them with direct system access for convenience.

## Files

- `AGENTS.md`

## Behavioral Impact

No runtime changes. The guidance explains that the interfaces serve a marker-style architectural role while also supplying helpers.

## Specification Impact

Specification Impact: none. This records the existing event interface convention without changing runtime event contracts.

## Risks

No application code was changed. An unrelated local `AnimationSystem` edit is preserved and excluded from this change.

## Follow-up Items

Validate the documentation diff and merge the focused documentation branch into local master. No runtime tests are needed for this instruction-only change.
