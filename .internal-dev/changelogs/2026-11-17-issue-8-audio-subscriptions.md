# Issue 8: bind audio subscriptions to their owning lifecycle

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
Retain the actual owning event bus and exact audio consumers; subscribe on attachment and queue identity-based removal on detach or disposal. Process listener changes at safe boundaries without snapshots or per-event membership scans.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/{AudioSystem,EventSystem}.java`
- `core/src/main/java/io/mindspice/lute/engine/events/EventListener.java`
- `core/src/test/java/io/mindspice/lute/engine/systems/AudioSystemTest.java`
- `core/build.gradle`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/knowledge/screen-lifecycle.md`
- `.internal-dev/bugs/audio-system-event-subscription-lifecycle/report.md`

## Behavioral Impact
Detached audio callbacks guard asset access while queued removal awaits a boundary. Ordinary unsubscription does not skip listeners mid-batch; same-instance reattachment can reuse active callbacks once in that batch and leaves one registration at the boundary. Retired buses stop remaining dispatch and flush cleanup even on exceptions without another update, while retaining unrelated owners' listeners. EventListener helpers remain; current audio has three subscriptions, not the historical four.

## Specification Impact
The new user-approved queued event listener decision in `specifications/decisions.md` specifies boundary-only mutation, identity-based queued changes, direct iteration without per-event copies or scans, and protection of disposed resources.

## Risks
GameScreen system ownership in issue #16 and other listener-owning systems remain outside this fix. The recorded Git Commit identifies the existing audit baseline, not necessarily this revision.

## Follow-up Items
Current branch focused validation passed audio 252, map sensor 1,732, asset service and desktop compilation. Unfiltered `./gradlew check --continue --console=plain -Dorg.gradle.logging.level=lifecycle` passed 22 tasks: audio 252, input 1,286 with zero failures, map sensor 1,732 and combat 1,912. `DISPLAY=:0 ./gradlew :lwjgl3:compositeRenderTest --console=plain -Dorg.gradle.logging.level=lifecycle` passed gameplay 15,815, including 480 actual-asset/native-scale comparisons across two weapon pages, and synthetic GL 11,854. Evidence is in `/tmp/lute-pr20-queued-{focused,check,gl}.log`. Earlier 196-check and combined-branch results are historical. The branch incorporates merged PR #19 through `origin/master`. Issue #8 and PR #20 remain open; no merge performed.
