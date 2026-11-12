# Immediate map event black screen review

## Scope
Reviewed working-tree immediate event changes, screen startup, event subscription and map rendering flow. Static analysis only; no runtime test.

## Findings
- `GameScreen.show()` immediately emits `Map.Load` before the first engine update. `TiledSystem` has requested a subscription, but `EventSystem.subscribe()` only queues it; `emitEventImmediate()` reads only the active listeners and silently drops the event. Map loading and subsequent renderer setup never happen.
- Previously the queued load was dispatched only after `EventSystem.update()` activated pending subscriptions. Events queued during dispatch, such as `Map.Changed`, are processed in that same update because the loop observes the changing event count.
- Pending unsubscriptions also remain active until the event update, so immediate dispatch can call a listener after unsubscribe was requested.

## Risk Assessment
Startup immediate delivery is lost; any immediate event emitted between subscription requests and their application has similar timing hazards. Immediate calls can run reentrantly in the caller's context, unlike queued delivery.

## Recommendations
Use queued delivery for the current startup path, or define and implement explicit subscription visibility semantics for immediate dispatch. Test subscribe and unsubscribe visibility, startup delivery, and nested event ordering before relying on immediate events.

## Follow-ups
A runtime smoke test would confirm the diagnosis. Preserve awareness that Ashley defers entity additions while the engine is updating; event delivery is not an entity-visibility boundary.
