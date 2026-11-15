# AudioSystem event subscriptions outlive system removal and screen disposal

## Summary
AudioSystem registers four instance-capturing event consumers but never unregisters them. GitHub: https://github.com/dhickel/lute/issues/8

## Scope
AudioSystem lifecycle and its registrations with the owning EventSystem. Other listener-owning systems are outside this report.

## Reproduction
Code-inferred, not runtime-tested: install an EventSystem, construct/add AudioSystem, and update to apply subscriptions. Remove or replace AudioSystem while retaining the EventSystem, add a replacement, then emit Sound.Play and update. Both instances' registered consumers attempt playback.

## Expected
A removed or permanently disposed audio system no longer handles events; its replacement handles each event once. Temporary screen hide/show remains supported.

## Actual
No audio cleanup or removal hook unregisters the consumers. GameScreen disposal does not remove systems and only disposes Disposable systems; AudioSystem is not one.

## Evidence
- `core/src/main/java/io/mindspice/lute/engine/systems/AudioSystem.java:24-36`: Map.Load, Sound.Play, Sound.PlayAtVolume and Player.Moved subscriptions; consumer references are not retained.
- `core/src/main/java/io/mindspice/lute/engine/systems/EventSystem.java:27-48,68-82`: changes are queued, removal uses consumer equality, and dispatch invokes retained listeners.
- `core/src/main/java/io/mindspice/lute/engine/events/EventListener.java:14-18`: unsubscription resolves the current global EventSystem, not necessarily the original one.
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java:34-44,67-80,97-105`: system setup, hide and disposal. `GameCore` owns the shared engine and currently creates one game screen.
- Baseline commit: `3fef4db57b8e1e41c7ca5979e6601ff6433f5aa9`.

## Impact
If the EventSystem remains live after audio removal or replacement, old listeners retain the audio system and asset service and may perform stale playback or asset lookups. Normal startup duplication has not been demonstrated.

## Status
Open; mirrored to GitHub issue #8. Closed GitHub issues were checked; no corresponding issue found.

## Next Action
Design teardown against the original EventSystem, including queued registration/unsubscription and disposal ordering; test removal, replacement, repeated lifecycle operations, and screen hide/show reuse.
