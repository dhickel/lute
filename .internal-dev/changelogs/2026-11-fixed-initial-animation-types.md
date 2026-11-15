# Fixed initial animation types

## Date
2026-11-15

## Git Commit

1a43a1274c9da7facf07e7f0f9d595fbce56cb70

## Change Summary
Remove Tiled animation type configuration and initialize players and animated environmental objects in code.

## Files
Tiled project and tileset, entity and Tiled systems, animation property parsers, tests, and animation authoring documentation.

## Behavioral Impact
Players start in IDLE; animated environmental objects use DEFAULT. Legacy Tiled animationType properties no longer select playback.

## Specification Impact
Updated the common animation authoring decision to specify the fixed initial types.

## Risks
Previously authored non-default Tiled animation types no longer affect initial playback.

## Follow-up Items
None.
