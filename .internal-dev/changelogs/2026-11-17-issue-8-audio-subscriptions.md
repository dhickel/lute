# Issue 8: bind audio subscriptions to their owning lifecycle

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
Retain the actual owning event bus and exact audio consumers; subscribe on attachment and synchronously remove active and pending callbacks on removal/disposal. Independent phase review: PASS. Deferred unsubscription now also respects consumer identity.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/{AudioSystem,EventSystem}.java`
- `core/src/main/java/io/mindspice/lute/engine/events/EventListener.java`
- `core/src/test/java/io/mindspice/lute/engine/systems/AudioSystemTest.java`
- `core/build.gradle`
- `.internal-dev/knowledge/screen-lifecycle.md`
- `.internal-dev/bugs/audio-system-event-subscription-lifecycle/report.md`

## Behavioral Impact
Removed/replaced/disposed audio systems stop receiving events even when their old bus never updates again. Replacement during dispatch and pending registration teardown are supported. EventListener participation and helpers are retained. Current audio has three subscriptions, not the historical four.

## Specification Impact
Specification Impact: none. Restores resource/subscription ownership without new gameplay behavior or changing deferred input delivery.

## Risks
GameScreen system ownership is separately handled by issue #16. Other listener-owning systems are outside this fix. The coordinating Git hash identifies the audit baseline; fix branch starts at code-equivalent parent 33dbd077ff1c66bf211971086b77caf2c8800563.

## Follow-up Items
Independent tests passed: audioSystemTest 196 checks, mapSensorSystemTest 1,732 checks, assetServiceTest, core compilation and diff check. Validator repaired deferred consumer identity removal and reran affected tests. Full integration evidence is separate. Known issue #6 still fails on this independent branch. Issue #8 remains open until PR merge; no merge performed.
