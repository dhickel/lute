## Date
2026-11-27

## Git Commit

a12b77e5e13f53225203dfc2ced1735f91041bc3

## Change Summary
Register staged players before setup and destroy their Box2D bodies if setup fails, during map travel or respawn.

## Files
`TiledSystem.java` and `MapTravelTest.java`.

## Behavioral Impact
Failed travel leaves the source map and its player intact. Failed respawn preserves the existing corpse. Successful setup is unchanged.

## Specification Impact
None. Restores the existing map preparation and ownership contract.

## Risks
The original setup error still propagates on respawn; this change only cleans up the incomplete player.

## Follow-up Items
Map travel, screen lifecycle, and Hoard arena tests passed. Startup transaction handling remains in separate PR #55.
