# AudioSystem event subscriptions outlive system removal and screen disposal

## Summary
Historically, AudioSystem registered instance-capturing event consumers without unregistering them. The lifecycle repair is implemented, pending PR. GitHub: https://github.com/dhickel/lute/issues/8

## Scope
AudioSystem lifecycle and its registrations with the owning EventSystem. Other listener-owning systems are outside this report.

## Reproduction
Code-inferred, not runtime-tested: install an EventSystem, construct/add AudioSystem, and update to apply subscriptions. Remove or replace AudioSystem while retaining the EventSystem, add a replacement, then emit Sound.Play and update. Both instances' registered consumers attempt playback.

## Expected
A removed or permanently disposed audio system no longer handles events; its replacement handles each event once. Temporary screen hide/show remains supported.

## Actual
At the reported baseline, no audio cleanup or removal hook unregistered the consumers. GameScreen disposal did not remove systems and only disposed Disposable systems; AudioSystem was not one. The implemented repair retains the owning EventSystem and exact consumer instances, subscribes on attachment, and synchronously removes active and pending registrations on detach or disposal through EventListener helpers. The current subscriptions are Map.Load, Sound.Play, and Sound.PlayAtVolume.

## Historical Evidence
- At the baseline below, `core/src/main/java/io/mindspice/lute/engine/systems/AudioSystem.java:24-36` registered Map.Load, Sound.Play, Sound.PlayAtVolume and Player.Moved without retaining consumer references. Player.Moved is not a current subscription.
- `core/src/main/java/io/mindspice/lute/engine/systems/EventSystem.java:27-48,68-82`: changes are queued, removal uses consumer equality, and dispatch invokes retained listeners.
- `core/src/main/java/io/mindspice/lute/engine/events/EventListener.java:14-18`: unsubscription resolves the current global EventSystem, not necessarily the original one.
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java:34-44,67-80,97-105`: system setup, hide and disposal. `GameCore` owns the shared engine and currently creates one game screen.
- Baseline commit: `3fef4db57b8e1e41c7ca5979e6601ff6433f5aa9`.

## Impact
If the EventSystem remains live after audio removal or replacement, old listeners retain the audio system and asset service and may perform stale playback or asset lookups. Normal startup duplication has not been demonstrated.

## Validation
Focused `:core:audioSystemTest` passed 195 checks; map sensor tests passed 1,732 checks, and asset tests and compile passed. The independent branch retains the unrelated baseline input failure tracked in issue #6. Root independent gates and full integration have not yet run.

## Status
Implemented, pending PR; not closed or archived. Shared screen-system ownership in issue #16 is separate and is not fixed by this repair.

## Next Action
Complete root independent validation and integration, then proceed with the PR.
