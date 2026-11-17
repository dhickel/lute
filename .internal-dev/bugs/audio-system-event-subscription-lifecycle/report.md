# AudioSystem event subscriptions outlive system removal and screen disposal

## Summary
Historically, AudioSystem registered instance-capturing event consumers without unregistering them. The lifecycle repair is prepared for review; the issue remains open until PR merge. GitHub: https://github.com/dhickel/lute/issues/8

## Scope
AudioSystem lifecycle and its registrations with the owning EventSystem. Other listener-owning systems are outside this report.

## Reproduction
Code-inferred, not runtime-tested: install an EventSystem, construct/add AudioSystem, and update to apply subscriptions. Remove or replace AudioSystem while retaining the EventSystem, add a replacement, then emit Sound.Play and update. Both instances' registered consumers attempt playback.

## Expected
A removed or permanently disposed audio system no longer handles events; its replacement handles each event once. Temporary screen hide/show remains supported.

## Actual
At the reported baseline, no audio cleanup or removal hook unregistered the consumers. GameScreen disposal did not remove systems and only disposed Disposable systems; AudioSystem was not one. The repair retains the owning EventSystem and exact consumers for Map.Load, Sound.Play, and Sound.PlayAtVolume. Detach queues their removal through EventListener helpers and clears the owner, so audio callbacks avoid asset access while awaiting a safe boundary. Retired buses flush cleanup without a future update, including when dispatch throws. Ordinary queued removal does not skip other listeners mid-batch.

## Historical Evidence
- At the baseline below, `core/src/main/java/io/mindspice/lute/engine/systems/AudioSystem.java:24-36` registered Map.Load, Sound.Play, Sound.PlayAtVolume and Player.Moved without retaining consumer references. Player.Moved is not a current subscription.
- `core/src/main/java/io/mindspice/lute/engine/systems/EventSystem.java:27-48,68-82`: changes are queued, removal uses consumer equality, and dispatch invokes retained listeners.
- `core/src/main/java/io/mindspice/lute/engine/events/EventListener.java:14-18`: unsubscription resolves the current global EventSystem, not necessarily the original one.
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java:34-44,67-80,97-105`: system setup, hide and disposal. `GameCore` owns the shared engine and currently creates one game screen.
- Baseline commit: `3fef4db57b8e1e41c7ca5979e6601ff6433f5aa9`.

## Impact
If the EventSystem remains live after audio removal or replacement, old listeners retain the audio system and asset service and may perform stale playback or asset lookups. Normal startup duplication has not been demonstrated.

## Validation
Current branch validation passed: focused audio 252 checks, map sensor 1,732 checks, asset service and desktop compilation; unfiltered `./gradlew check --continue --console=plain -Dorg.gradle.logging.level=lifecycle` passed 22 tasks, including audio 252, input 1,286 with zero failures, map sensor 1,732 and combat 1,912. `DISPLAY=:0 ./gradlew :lwjgl3:compositeRenderTest --console=plain -Dorg.gradle.logging.level=lifecycle` passed gameplay 15,815 checks with 480 actual-asset/native-scale comparisons across two weapon pages, and synthetic 11,854 checks. Logs: `/tmp/lute-pr20-queued-{focused,check,gl}.log`. The branch incorporates merged PR #19 through `origin/master`. Earlier 196-check and combined-branch results are historical, not evidence for this revision.

## Status
The fix is prepared for review; the issue remains open until PR merge. Shared screen-system ownership in issue #16 is separate and is not fixed by this repair.

## Next Action
Retain this record until the PR is merged and the issue resolved.
