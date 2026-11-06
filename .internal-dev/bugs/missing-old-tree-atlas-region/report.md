# Map references old_tree1 without a matching atlas region

## Summary
A desktop startup smoke run reports missing atlas region `old_tree1` after loading completes. This is separate from the screen lifecycle repair.

## Scope
Map/atlas content and Tiled texture-region lookup; not changed by the lifecycle fix.

## Reproduction
Run `./gradlew :lwjgl3:run` and observe startup logs after `[LoadingScreen] Finished loading assets`.

## Expected
Map texture lookups find their atlas regions.

## Actual
`[TiledAshleyConfigurator.Error] Failed to find AtlasRegion for key: old_tree1` appears three times. The application continues rendering.

## Evidence
- `assets/maps/test_tiled.tmx` references `../tilesets/ancient_forest/old_tree1.tsx`.
- `assets/objects/packed/objects.atlas` contains no `old_tree1` entry.
- Startup log: `/tmp/lute-screen-startup-GCtXBM.log` (temporary validation evidence).
- Map/atlas assets and the lookup implementation were not modified by the lifecycle repair.

## Impact
The tree texture uses the existing fallback rather than an atlas region; startup is not blocked.

## Status
Open; outside the requested minimal screen lifecycle changes. No Git remote is configured, so no GitHub issue mirror is applicable.

## Next Action
Check the intended atlas assignment and packed tree assets before changing map or atlas content.
