# Common animation schema

## Date
2026-11-15

## Git Commit

2f8f61d29d1559e879102a7eaf0061c449a505f3

## Change Summary
Share animation configuration between player, weapon, and Tiled animated objects; add data-only player sound entries.

## Files
Animation data records, JSON assets, Tiled project, map and tileset, entity factory, tests, and animation authoring documentation.

## Behavioral Impact
Player timing and atlas key come from JSON. Environmental animations use the shared Tiled fields with preserved tree timing. Player move and damage sounds are not loaded or played.

## Specification Impact
Updated the common animation authoring and attack ownership decisions, including keeping the Tiled and JSON schemas synchronized.

## Risks
Existing external Tiled assets and player definitions require schema migration. Player sounds remain placeholders.

## Follow-up Items
Player sound playback is intentionally not wired.
