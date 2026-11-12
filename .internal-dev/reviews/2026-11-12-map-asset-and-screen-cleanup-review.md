# Map asset and screen cleanup review

## Scope
Reviewed current loading, map transitions, screen lifecycle, entity/physics cleanup, asset ownership, and rendering references.

## Findings
- Initial loading queues manifest assets and TiledSystem retrieves the map; shutdown disposes the screen/world before AssetService.
- TiledSystem map replacement removes only environment bodies and unloads the map, leaving old map entities and their input listeners and physics bodies.
- Same-map reactivation gets a map and then unloads that same asset before reusing it.
- Other manifest assets remain loaded; renderer references and animation cache require cleanup before atlas/map unload.
- GameScreen disposal leaves its systems on the shared engine; hide is called twice on active-screen disposal.
- PhysicSystem's entity removal listener already destroys entity-owned bodies.

## Risk Assessment
Map transitions and screen re-entry risk stale resources and disposed-map use; application shutdown has a sound overall disposal order. Transitions are not exercised by the current test evidence.

## Recommendations
Coordinate map teardown, preserving shared ownership and matching asset acquisitions with releases. Unregister input, remove map entities, clear renderer and animation references, destroy remaining environment bodies, then release map assets. Ensure target assets are loaded and same-asset reload is safe. Remove screen-owned systems during terminal disposal and make hide/dispose responsibilities explicit.

## Follow-ups
Test repeated screen activation, same/different map transitions, listener and body counts, asset ownership, and shutdown. One read-only reviewer reported :core:test passing; no graphical runtime test was performed.
