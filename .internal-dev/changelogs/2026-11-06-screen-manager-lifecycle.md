# Connect custom screen manager to the game lifecycle

## Date
2026-11-06

## Git Commit

67036c3a544a113bcb3ee6cad88438e4793d71c9

## Change Summary
This repair builds on the user's existing, uncommitted screen-manager refactor.
- Start on `InitialLoading` and execute the existing completion thunk only after assets load.
- Forward rendering, resizing, pause, resume, and shutdown through the custom manager.
- Start with no active screen, guard the first switch, and resize each newly shown screen.
- Switch to the game before disposing the loading screen; clear the active reference when unloading an active screen.

## Files
- `core/src/main/java/io/mindspice/lute/engine/GameCore.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/ScreenManager.java`
- The existing user changes in `LoadingScreen.java` are retained without further edits.
- `.internal-dev/knowledge/screen-lifecycle.md`
- `.internal-dev/bugs/missing-old-tree-atlas-region/report.md`

## Behavioral Impact
Loading queues and updates atlases before map-object atlas lookups. The current custom screen receives the application lifecycle callbacks. Screen shutdown precedes shared-resource disposal.

## Specification Impact
Specification Impact: none. This restores lifecycle wiring in the existing screen-manager architecture without introducing a new intended feature or changing an existing living specification contract.

## Risks
Compilation still reports unchecked-operation warnings. The desktop run confirms startup and rendering, not interactive pause/resume or shutdown behavior. An unrelated missing `old_tree1` atlas region falls back to the tile texture and is tracked separately.

## Follow-up Items
- `./gradlew :core:compileJava :lwjgl3:compileJava --console=plain` passed.
- `git diff --check` passed.
- A timed `:lwjgl3:run` smoke run logged loading completion, entity/map initialization, and FPS output. It was intentionally stopped after 15 seconds; no startup exception occurred.
- No test suite was added or run, as requested.
