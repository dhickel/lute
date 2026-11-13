# Extract player component creation helpers

## Date
2026-11-12

## Git Commit

13273d8ca40b016ca7cb57b8ba610c4b8b0cde39

## Change Summary
Refactored EntityFactory directly, extracting animation initialization, pixel-to-world transform construction, and physics component construction into private helpers. Player assembly remains explicit; trivial component constructors remain inline.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`

## Behavioral Impact
None intended. Preserved component order, coordinate conversion, collision geometry, input registration, and caller-owned entity insertion. Core compilation and diff whitespace validation passed. No smoke runs or tests performed, as requested.

## Specification Impact
Specification Impact: none. This is an internal readability refactor without a contract change.

## Risks
Helpers remain private and do not expand the public API.

## Follow-up Items
None.
