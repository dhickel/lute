# Event listener consumer handles

## Date
2026-11-10

## Git Commit

4fa145f665ddcaa6bcc81405139069538e6616f8

## Change Summary
Replaced EventListener's system field and invalid subscribe wrapper with subscribeConsumer and unsubscribeConsumer method references. Generic SubscriptionConsumer preserves event class/consumer type matching.

## Files
- core/src/main/java/io/mindspice/lute/engine/events/EventListener.java

## Behavioral Impact
Use subscribeConsumer.accept(EventType.class, consumer) and unsubscribeConsumer.accept(EventType.class, consumer).

## Specification Impact
none. This small convenience API correction does not change an existing specification.

## Risks
Retains the existing eager global engine lookup pattern.

## Follow-up Items
None. Validation: ./gradlew core:compileJava --console=plain passed.
