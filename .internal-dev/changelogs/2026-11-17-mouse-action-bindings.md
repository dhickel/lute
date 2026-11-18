# Mouse action bindings

## Date
2026-11-17

## Git Commit

e2dab27f0a68f3b1966a49a2dbdcbb85fa7040c3

## Change Summary
Added optional mouse-button action bindings and delivery to both gameplay queues and positional mouse caches, including balancing releases. Scroll behavior remains unchanged.

## Files
`assets/input.setting.json`, core input settings and mouse routing classes, input regression tests, `docs/input-system.md`, and `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
`MButton1` through `MButton5` bind directly to actions. The shipped mouse map is empty, so defaults are unchanged.

## Specification Impact
The input settings decision now defines optional mouse bindings and their delivery behavior.

## Risks
Bindings apply at startup only. Physical mouse hardware was not exercised.

## Follow-up Items
None required for this change. The isolated input regression passed 1,387 checks, and desktop Java compilation passed.
