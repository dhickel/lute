## Date
2026-11-27

## Git Commit

bdc4c96464651261a7fe2b1029ddc6c33eff8627

## Change Summary
Remove empty frame hooks and unused transform offset storage. Derive active attack status from the existing attack snapshot.

## Files
`GameCore.java`, `Transform.java`, and `Combat.java`.

## Behavioral Impact
None. Render ordering, transform construction, and attack lifecycle are unchanged.

## Specification Impact
None. This removes redundant implementation details without changing the runtime contract.

## Risks
Attack status must continue to follow the snapshot throughout start, cancellation, and teardown.

## Follow-up Items
Focused combat, input, screen lifecycle, menu flow, and Hoard arena tests passed.
