## Date
2026-11-26

## Git Commit

a68c3b093c2c542e201167e79d622c5a9aebc4df

## Change Summary
Fixed launch profiles silently overriding debug layers enabled in Options.

## Files
- `MenuSettings.java` and `MenuFlowTest.java`
- `.internal-dev/specifications/decisions.md` and `docs/asset-service.md`

## Behavioral Impact
Explicit Options selections take precedence when starting gameplay through the menu. Launch profiles still supply defaults and control direct-map startup. The raw light map remains night-only.

## Specification Impact
Updated the developer launch decision to document precedence.

## Risks
Previously saved overlay choices now apply even when starting through the normal IDE launch profile.

## Follow-up Items
Menu Flow and Hoard Hammer Demo passed. The separate data-loader assertion failure is tracked in GitHub #50.
