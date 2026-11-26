# IntelliJ run profiles

## Date
2026-11-24

## Git Commit

9bac0140e86baadcdabb26a6a86950c4fb8a2454

## Change Summary
Add shared IntelliJ configurations for normal play and game diagnostics.

## Files
Shared `.run/` configurations, desktop Gradle run task, game settings loader and tests, debug decision, and asset guide.

## Behavioral Impact
Lute Run disables overlays. Lute Debug Run enables physics and trigger overlays. Neither changes authored assets; an unqualified launch still uses authored settings.

## Specification Impact
Document shared IntelliJ run configurations, the authored-settings fallback, and the distinction between game diagnostics and IDE breakpoints.

## Risks
Offline settings tests and desktop startup smoke tests passed. Configuration XML was not manually exercised in IntelliJ.

## Follow-up Items
None.
