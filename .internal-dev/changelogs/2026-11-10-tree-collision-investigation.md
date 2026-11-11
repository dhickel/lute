## Date

2026-11-10

## Git Commit

ab6135bf3f71e2e6d9b7f3d69a0eb2cae8beb086

## Change Summary

Recorded the tree collision root cause, reusable property-scope guidance, and an unrelated map-reload type mismatch. Investigation only; no game repair applied.

## Files

- `.internal-dev/reviews/2026-11-10-tree-collision-review.md`
- `.internal-dev/knowledge/tiled-collision-properties.md`
- `.internal-dev/bugs/map-reload-asset-reference/report.md`
- `.internal-dev/changelogs/2026-11-10-tree-collision-investigation.md`

## Behavioral Impact

None. Existing and concurrent application edits remain untouched.

## Specification Impact

none. These records explain current loader behavior and asset configuration; they introduce no intended behavior or contract changes.

## Risks

Diagnosis validated by source tracing and XML parsing, not an in-game run. The separate map-reload defect remains unfixed.

## Follow-up Items

When authorized, place the tree's physics-enabling properties on the parent tile and validate player collisions in game. Track the separate reload defect on GitHub.
