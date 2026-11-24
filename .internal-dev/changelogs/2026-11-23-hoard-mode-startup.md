# Launch into Meridian Court

## Date
2026-11-23

## Git Commit

a7833f93f446609d1544d0a4276115e24db12273

## Change Summary
Set the local startup map to `hoard_mode` and regenerate runtime assets.

## Files
- `raw_assets/game.settings.json`: source startup setting.
- `assets/game.settings.json`: generated startup setting.

## Behavioral Impact
Desktop launch opens Meridian Court. Its `Triggers` layer remains unchanged; there is no controllable player until a `PLAYER` spawn point is authored.

## Specification Impact
Specification Impact: none. The change selects a different map using the existing `startupMap` setting.

## Risks
Both settings files are Git-ignored. The change is local to the provisioned asset tree. Runtime asset generation and a desktop startup smoke check passed.

## Follow-up Items
Add a `PLAYER` marker when ready to playtest, then regenerate runtime assets.
