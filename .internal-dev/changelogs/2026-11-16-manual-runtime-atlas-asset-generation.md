# Manual runtime atlas asset generation

## Date
2026-11-15

## Git Commit

43aeafb808c43e7d67e37f4bc6080634dc14b2e1

## Change Summary
Separate editable map assets from generated runtime assets and resolve object tiles through each map's atlas. Runtime generation is manually invoked.

## Files
`raw_assets/`, `assets/`, `util/`, core asset and Tiled loading code and tests, and `README.md`.

## Behavioral Impact
Maps borrow atlas regions while terrain textures remain map-owned. The generator tracks owned outputs and stages replacements.

## Specification Impact
The asset layout and loading workflow now use manual generation and atlas-backed object tiles; no existing detailed living specification covers this workflow.

## Risks
Interrupted publication requires transaction recovery. Full core validation has a pre-existing controller input test failure.

## Follow-up Items
Validate interactively in Tiled and in the graphical game; shared player atlases remain outside this change.
