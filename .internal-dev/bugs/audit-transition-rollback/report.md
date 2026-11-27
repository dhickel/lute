## Summary
Gameplay startup and map staging lack complete rollback on a failed transition.

## Scope
`GameCore.startMap`, `LoadingScreen`, `ScreenManager`, `GameScreen`, and `TiledSystem` on `7d2cc90`.

## Reproduction
Not run during the static audit. Fault-inject gameplay asset loading or first `show()`. Separately, force arena player configuration to throw after allocation with a live map mounted.

## Expected
Retain or return to a usable screen; release systems, input, bodies, and destination assets owned only by the failed attempt.

## Actual
Static inspection: systems mount before loading; `currScreen` is assigned before `show`; a new player's body can be allocated before `prepare` adds the player to the rollback list.

## Evidence
[Architecture review](../../reviews/2026-11-27-combat-architecture-and-records-review.md).

## Impact
Failed load or setup can leave a half-mounted session or an extra body in a live world.

## Status
Open; fault-injection validation pending.

## Next Action
Give startup one rollback owner and track the staged body before mode configuration. Test both paths independently.
