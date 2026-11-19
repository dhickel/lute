## Date
2026-11-18

## Git Commit

170700744be7f84a3cb609c44c3a2c7da20b5124

## Change Summary
Moved projectile definitions from a bundled resource to the authored gameplay-data asset path.

## Files
- `raw_assets/data/projectiles.json` (local, ignored) replaces the bundled projectile file.
- `DataLoader`, `GameData`, `GameScreen`, and `CombatSystem` load and pass gameplay definitions at startup.
- `README.md` and the asset specifications and knowledge record document the asset path and provide setup instructions.

## Behavioral Impact
Generation copies `projectiles.json` to `assets/data/`. `DataLoader` reads it with the player and weapon files and fails if the runtime file is missing, with no bundled fallback.

## Specification Impact
Updated the composite-character and local asset-tracking contracts to prohibit bundled authored gameplay data.

## Risks
Older externally provisioned asset trees need `raw_assets/data/projectiles.json`. The README provides starter data.

## Follow-up Items
None. Asset generation, atlas tests, core checks, the missing-file regression test, and the real-GL game screen lifecycle test passed.
