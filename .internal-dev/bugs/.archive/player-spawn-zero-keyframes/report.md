# Player spawn inherits zero keyFrames

## Summary
The PlayerCharacter project class supplied keyFrames=0 to the spawn point, causing animation initialization to fail although tile animations were positive.

## Scope
Point-based player configuration and animation diagnostics.

## Reproduction
Set PlayerCharacter.keyFrames to zero in assets/game.tiled-project and load test_tiled using Asset.Map parameters, which include projectFilePath. Inspect PlayerSpawn object 18: its loaded and parsed frame values are zero. The five trees retain ten.

## Expected
The player inherits its valid class default of eight. Invalid explicit overrides fail with identifiable animation context.

## Actual
Inherited zero bypassed the parser's absent-value fallback. The shared animation helper rejected zero without identifying the asset.

## Evidence
Non-graphics loader reproduction confirmed player zero and five trees ten before repair. Focused property test now passes with inherited eight and rejects explicit zero/negative overrides. Core/desktop compilation and manifest test passed.

## Impact
Game startup failed while constructing the player, not while constructing a tree.

## Status
Resolved locally after recurrence. The prior class-default-only fix did not persist: PlayerCharacter again supplied zero in the working project file. Added explicit integer keyFrames=8 to PlayerSpawn object 18 so its configuration wins over the class default. The source of the external default change is unproven. Preserved other dirty asset changes. Mirror: https://github.com/dhickel/lute/issues/2 . Archived as a resolved report.

## Next Action
Preserve the explicit spawn value and strict validation. Focused tests cover the saved point against zero/eight class defaults plus eight property precedence cases. Close the reopened mirrored issue after recording the fix commit.
