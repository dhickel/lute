## Date
2026-11-27

## Git Commit

4955edb746d6ad832082dd2a1a0abd7eec4d0a43

## Change Summary
Show a graphical startup progress bar, numeric loaded/total counts, and latched red errors in PR #55.

## Files
Startup and asset lifecycle code, startup and asset-service regressions, and the startup sections of the asset guide and living specifications.

## Behavioral Impact
Startup counts unique shared and map assets. A runtime error stops all startup work and stays visible. Menu launches can return to the menu and retry; direct-map launches stay on the error screen until exit.

## Specification Impact
Updates the startup loading contract; cycling backgrounds remain deferred.

## Risks
Individual asset loads and map mounting remain synchronous.

## Follow-up Items
Six focused tasks passed, including 779 real-GL startup checks. Broader data-loader and composite-render failures also reproduce on the unchanged merged baseline.
