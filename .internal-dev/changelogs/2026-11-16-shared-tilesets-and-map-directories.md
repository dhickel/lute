# Shared tilesets and map directories

## Date
2026-11-15

## Git Commit

bf3286cb163aa88f1c680f0f8aed19b1ee34b7dc

## Change Summary
Place source and runtime map directories under `maps/` and share terrain tilesets under `tilesets/`.

## Files
`raw_assets/`, `assets/`, map manifest loading, runtime asset generation and tests, and `README.md`.

## Behavioral Impact
Manifests reside beside maps. Referenced shared tileset definitions and terrain images retain their paths; object images are packed rather than copied.

## Specification Impact
The layout follows the clarified source and runtime asset contract; the existing project specifications do not contain a detailed asset layout.

## Risks
Tiled and graphical game workflows require interactive verification.

## Follow-up Items
None.
