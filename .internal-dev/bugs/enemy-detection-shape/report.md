# Author enemy detection shape in `enemies.json`

## Summary
Enemy detection radius is authored, but the detection shape is hardcoded.

## Scope
Support full circle and forward semicircle alongside `detection_bounds.radius` in `enemies.json`. Migrate assets and fixtures without changing existing enemy behavior.

## Reproduction
Inspect the schema and compare ordinary enemy acquisition with Dredge acquisition and the physics debug overlay.

## Expected
Each enemy's shape and radius are authored and validated. Awareness and the debug overlay agree on the selected shape.

## Actual
Ordinary enemies use a forward half-plane filter; `EntityFactory` gives the literal `dredge` spawn key full-circle sight.

## Evidence
`MapActorData.DetectionBounds` accepts only radius. `EntityFactory` adds `FullCircleVision`; `AwarenessSystem` and `PhysicDebugRenderSystem` use that distinction.

## Impact
Content authors cannot change an enemy's vision shape without Java changes.

## Status
Open. [GitHub issue #48](https://github.com/dhickel/lute/issues/48).

## Next Action
Use the authored shape for visibility and debug display, preserve generic circular range queries, line of sight, retaliation and Hoard guard behavior, and test both shapes and boundary cases.
