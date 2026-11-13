# Clarify animation key selection

## Date
2026-11-12

## Git Commit

f23e0250318639b555f21cf8b884f8d7651b0e42

## Change Summary
Replaced the animation-key ternary expression with explicit if/else branches in EntityFactory.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`

## Behavioral Impact
None; the same animation key is selected.

## Specification Impact
Specification Impact: none. Only expression readability changed.

## Risks
None identified.

## Follow-up Items
None.
