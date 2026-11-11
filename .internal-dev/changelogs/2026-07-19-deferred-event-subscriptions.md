# Deferred event subscriptions

## Date
2026-07-19

## Git Commit

4fa145f665ddcaa6bcc81405139069538e6616f8

## Change Summary
Queue and drain subscriptions using the existing unsubscription pattern, after dispatch and before removals.

## Files
- core/src/main/java/io/mindspice/lute/engine/systems/EventSystem.java

## Behavioral Impact
New listeners activate after dispatch finishes. Callback registrations cannot modify the current listener list. Duplicate registrations remain supported; same-update removals apply after registrations.

## Specification Impact
Specification Impact: none. No living event-system specification exists; current specifications cover input listeners.

## Risks
Initial registration now requires an update before delivery. Changes remain uncommitted because this file contains staged and unstaged user implementation work that cannot be included without incorporating unrelated edits.

## Follow-up Items
None for implementation. Validation: Gradle compilation command exited zero. Independently compiled EventSystem, Event and Pair and ran a temporary assertion harness covering initial deferral, callback registration with multiple queued events, queue clearing, duplicates, deferred removal and same-update cancellation. git diff --check passed.
