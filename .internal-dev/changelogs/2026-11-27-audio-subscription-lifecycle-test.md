# Audio subscription lifecycle regression

## Date
2026-11-27

## Git Commit
a12b77e5e13f53225203dfc2ced1735f91041bc3 (baseline; changes are uncommitted)

## Change Summary
Add `:core:audioSubscriptionLifecycleTest` to `:core:check` for [issue #8](https://github.com/dhickel/lute/issues/8). Drive real AudioSystem instances and both sound event types through Ashley and the retained global EventSystem. Only the LibGDX logging and audio backends are stubbed; the test measures playback by instance, sound ID, volume, and count.

## Files
- `core/build.gradle`
- `core/src/test/java/io/mindspice/lute/engine/systems/AudioSubscriptionLifecycleTest.java`

## Behavioral Impact
Test only. All six lifecycle checkpoints passed, including queued teardown before the first drain, repeat removal, re-add of the same instance, replacement, and silence without a live listener. `:core:assetServiceTest` and `:lwjgl3:gameScreenLifecycleTest` also passed (825 real-GL checks for the latter). The isolated asset-service run requires both generated `assets/` and `raw_assets/` for the TMX fixture.

Individually removing either unsubscription in a temporary compiled copy reproduced stale playback from the removed instance before the first drain; each mutant failed the test. No production files were changed.

## Specification Impact
Specification Impact: none. The test verifies existing lifecycle behavior without changing the event contract or the marker interfaces.

## Risks
Replacing the EventSystem underneath a live listener and switching global engines are outside the test. GameScreen installs the bus before audio and removes owned systems in reverse order, so the current screen teardown does not require either operation.

## Follow-up Items
Issue #8 remains open. Its historical report describes an older baseline with no removal hook; the retained-bus leak did not reproduce on this baseline. No new defect was reproduced in the scoped lifecycle.
