# Authored sprite and map checkpoint

## Date
2026-11-16

## Git Commit

2395e7f90a626c9c3e2d6552ac75dd25a1658483

## Change Summary
Checkpoint pre-existing character, equipment, and weapon sprites alongside existing test-map and Tiled editor changes.

## Files
- `raw_assets/sprites/` — new sprite PNGs.
- `raw_assets/maps/test_tiled/objects/objects.tsx` and `assets/maps/test_tiled/objects/objects.tsx` — removed the legacy player tile and changed the tree collision ellipse.
- `assets/.runtime-assets-owned` — refreshed checksum.
- `assets/game.tiled-session` and `raw_assets/game.tiled-session` — editor state.
- Build caches and test reports are excluded.

## Behavioral Impact
No composite player runtime implementation is included. Runtime effects of the map edits have not been validated.

## Specification Impact
None. This is a checkpoint of authored assets and existing map edits.

## Validation
Limited to `git diff --check`, JSON and XML parsing, and PNG signature and header checks. No runtime validation was performed.

## Risks
The map changes and sprite assets have not been exercised in the game.

## Follow-up Items
Commit the checkpoint, then implement and validate the composite player feature separately.
