# Record hobby project simplicity and readability priorities

## Date

2026-11-14

## Git Commit

2f646a25be86188b88b3ec482e14438fa58d9b22

## Change Summary

Add the user's hobbyist-project guidance to `AGENTS.md`: prioritize simple, grokkable code, readable flow, and formatting. Avoid verbose implementations, unnecessary abstractions, exhaustive speculative edge-case handling, and repeated defensive checks inside trusted code. Retain meaningful boundary validation and realistic failure handling.

## Files

- `AGENTS.md`

## Behavioral Impact

Instruction-only change; no runtime behavior changes.

## Specification Impact

Specification Impact: none. This records engineering style preferences without changing application contracts.

## Risks

The unrelated local `AnimationSystem` edit is preserved and excluded.

## Follow-up Items

Validate the documentation diff and merge the focused branch into local master. Runtime tests are not applicable.
