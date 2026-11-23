# Share Tiled spawn metadata

## Date

2026-11-22

## Git Commit

cdcb431ef412605b04c06a26906b542a49ca625a

## Change Summary

Read the shared Tiled `SpawnInfo` class for spawn markers and share name and type between Java point and area records.

## Files

- `MapTriggers.java`: Parse nested spawn metadata and retain legacy flat overrides.
- `MapTriggersTest.java` and `MapSensorSystemTest.java`: Verify nested authoring and shared runtime data with unchanged marker behavior.
- `docs/asset-service.md`, `.internal-dev/specifications/decisions.md`, and `.internal-dev/knowledge/point-player-spawning.md`: Document authoring and migration.

## Behavioral Impact

Points retain their spawning and travel behavior; areas remain data-only. Migrated the local ignored project and sample map and restored the `TELEPORT` enum entry before regenerating runtime assets.

## Specification Impact

The Tiled spawn decision now defines the shared nested class and legacy property precedence.

## Risks

Other asset trees must preserve each marker's role when migrating, especially teleport destinations. Existing flat properties still work and take precedence if retained.

## Follow-up Items

None. Asset generation and the map trigger, map sensor, map travel, and data loader tests passed.
