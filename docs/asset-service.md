# Asset service

`AssetService` owns loaded sounds, music, texture atlases, and tiled maps. Construct it with a libGDX `FileHandleResolver`. Each entry is identified globally within the service by its asset class and logical ID, not by its file path. The same ID can identify different types; two IDs pointing to the same path are separate loads with independent ownership.

## Definitions and manifests

`Asset.Sound`, `Asset.Music`, `Asset.Atlas`, and `Asset.Map` are load definitions, not runtime handles. Each provides an ID, path, and resource type; IDs and paths must be nonblank. Their `of(id, file)` helpers use the sound, music, atlas, and map directory prefixes in `GameConsts`. Constructors accept complete paths. `GameCore.create()` first loads `assets/game.settings.json`, which supplies those prefixes and the existing game constants. Directory values must end with `/`; invalid settings stop startup before values are applied.

`ManifestLoader.parseMapManifest(mapName)` uses `mapName` as the logical ID for both the map and its single atlas. Each manifest supplies an `atlasAsset` filename; music and sound manifest keys remain their logical IDs. `MapAssets` checks keyed definitions and `all()` collects the map, atlas, music, and sound definitions for loading. `startMusic` selects a music ID or `RANDOM`.

## Animation authoring and migration

The shared `AnimationData` schema has four fields: speed, atlas key, sound frame, and frame interval in seconds. Player and weapon JSON use `speed`, `atlas_key`, `sound_frame`, and `frame_interval` inside `animation`. Tiled's `Animation` class uses camelCase `speed`, `atlasKey`, `soundFrame`, and `frameInterval`; `AnimatedPhysicObject.animation` is an instance of that class. Players start in `IDLE`; animated environmental objects start in `DEFAULT`. Neither initial type is set in Tiled. Keep the Tiled class and Java/JSON schema synchronized in fields, types, and meaning; values are independently authored.

The manifest supplies the one environmental atlas for the map; Tiled objects no longer select an atlas asset. A tile's animation class supplies its values. A placement can override individual fields; `FixedTmxMapLoader` merges explicitly authored members with the tile values before LibGDX's class defaults can replace them. Migrate old properties and remove `keyFrames`. The tree atlas key identifies its region; the tree uses a 0.1-second interval and speed 0.35. Only weapon attacks consume the sound frame. Player move and damage sounds list `sword_hit.wav` as data-only placeholders; they are not loaded or played.

Characters use six shared, potentially multipage atlases generated from `raw_assets/sprites/`: character, head, body, hands, feet, and weapons. Generated files under `assets/atlases/` are runtime outputs. Character action regions are flat, such as `idle_down`; equipment regions use item prefixes, such as `basic/shot_up`. The common weapons atlas retains category and item prefixes, such as `melee/basic_sword` and `ranged/basic_bow`. A shared animation clock aligns all visible layers; `cast` is not required.

The default loadout has basic head, body, hands, and feet equipment and `basic_sword`. Runtime equipment can be switched, including to no weapon. The renderer composes each visible character in its own offscreen cell, batching layers by actual atlas page while preserving their order, then world-depth-sorts each completed character with ordinary objects. See the [composite character specification](../.internal-dev/specifications/composite-characters.md) for the rendering and loadout contract.

## Sensor, teleport, and spawn authoring

On an orthogonal map, author trigger objects in an object layer named `Trigger` or `Triggers`. Trigger layers and their ancestor groups must have zero offsets and unit parallax. `Sensor` and `Teleport` objects support filled rectangles, circles/ellipses, and simple polygons, including rotation. Rectangles and ellipses must have positive dimensions; invalid, self-intersecting, or degenerate polygons are rejected. Non-circular ellipses use a fixed eight-sided approximation. Concave polygons and simple polygons with more than eight vertices use multiple convex fixtures but remain one logical detector, without duplicate events and with the same teleport reconciliation as a single-fixture detector. Points and polylines are not detector shapes; text and tile objects are excluded. The Tiled object name identifies the sensor and must be unique among sensors. A `Sensor` has `sensorType` set to `GENERAL`; a `Teleport` also emits `GENERAL` sensor enter and exit events. These events carry the sensor name, type, and player; only the player's non-sensor fixtures participate.

For a `Teleport`, set `targetMap` to `THIS` (case-insensitive) and `targetSpawn` to the name of a unique teleport-category `SpawnPoint`. Other map targets log as unsupported and leave the player in place. A same-map teleport centers the player on the destination point, stops velocity, preserves facing, and resets interpolation. The player must exit the destination teleport area before reentry can trigger it, preventing a teleport loop.

Author a `SpawnPoint` as a zero-size point and a `SpawnArea` as an unrotated rectangle with positive dimensions, using a Tiled object name unique among spawn markers and a `spawnType` of `PLAYER`, `TELEPORT`, `NPC`, or `ITEM`. At most one `PLAYER` point is allowed; it creates the initial player. Areas and `NPC`/`ITEM` markers are data-only. The map owner cleans up sensor objects.

## Weapon assets and animation timing

Map manifests describe map-owned assets. Weapon definitions live in `assets/data/weapons.json`, which contains a shared `atlas` path and a `weapons` map. Each weapon has an `atlas_key` and `attack_stats` with separate `primary` and `secondary` records. Each attack owns its `animation`, `bounds`, `stats`, `collision_type`, and `sounds.attack` and `sounds.hit` paths. The unarmed definition uses `NONE` for its weapon layer.

`WeaponAssets.of(weaponId, definitions)` resolves both attacks' sound filenames relative to the configured sound directory. Initial loading queues attack and hit sounds for both slots of every configured weapon, including unarmed. Each attack can select its own paths; reuse of existing WAV files is approved. Runtime IDs are `weapon/<id>/<primary|secondary>/<attack|hit>`; animation receives the resolved attack ID rather than weapon filenames or selection rules. Hit sounds are loaded but not played. Loading these assets does not implement inventory, UI, or automatic unloading.

Space triggers the primary attack: sword slash or bow shot. Shift triggers the secondary jab. Without a weapon, both trigger a hand jab. A bow shot plays its animation but creates no projectile and deals no damage; its projectile collision data remains authored for future execution.

Attack frames are zero-based. For melee, `stats.hit_frame` starts the hitbox, and animation completion removes it. `animation.sound_frame` emits one attack sound when playback reaches or crosses that frame; frame 0 is immediate at animation start. `animation.speed` scales both timings and the total attack lifetime. Combat owns fixtures and damage; animation owns playback and sound emission.

Player and weapon JSON values are validated on entry. At entity composition, `EntityFactory` validates weapon geometry and both cue frame limits for every facing direction against the loaded atlas. Runtime code trusts these values; animation checks the stored minimum frame count again only when a previously seen atlas is replaced.

The animation FSM and playback run before combat, which runs before physics. Combat accepts an attack and configures its playback data; the animation starts on the following update. Sound events retain the existing queued dispatch timing. Large updates cannot repeat or skip sound cues, but an update that crosses the entire hitbox interval does not force a physics step or fabricate a hit.

## Loading and lookup

Call resource-loading operations on the render thread. `queue(definition)` registers work without loading. Each `update()` synchronously loads at most one queued entry and returns true when no queued entries remain. There is no background loading. `LoadingScreen` queues its definitions on show and calls `update()` from render. If an update fails, the screen propagates the failure and stops updating or completing until `show()` resets it and queues the definitions again. `load(definition)` synchronously loads only that requested entry, even if others are queued.

Repeating a definition with the same type, ID, and path is idempotent. A different path for an existing type and ID fails until that entry is unloaded or a failed load removes it. Load failures are reported and remove the failed entry; explicitly queue or load it again to retry. `get(type, id)` fails if the entry is absent or still queued. `isLoaded(type, id)` reports whether it has a loaded value; `debugDiagnostics()` logs queued and loaded entries and their paths.

Internally, four typed maps store entries by ID, with direct class selection and a separate ordered loading `queue`. Diagnostics and disposal iterate the four maps directly; their cross-type insertion order is not preserved. The public logical identity remains `(Class, ID)`; this storage change does not alter the service interface or lifetime rules. Generic `get` uses an unchecked cast backed by validation against the resource class at the load boundary. See [the storage benchmark](asset-storage-benchmark.md) for the comparison and its limits.

For example, after loading a sound, look it up by type and ID, or pass the ID to the audio event consumer:

```java
Asset.Sound hit = Asset.Sound.of("SWORD_HIT", "sword_hit.wav");
assets.load(hit);
Sound sound = assets.get(Sound.class, "SWORD_HIT");
sound.play();
Event.Sound.Play event = new Event.Sound.Play("SWORD_HIT");
```

The event must be dispatched through the application's event system. `AudioSystem` resolves sound IDs when handling sound events and resolves music IDs for playback. Animation components likewise store an atlas ID, which `AnimationSystem` resolves as a `TextureAtlas`.

## Unloading and ownership

`unload(type, id)` cancels a queued entry or removes and disposes a loaded resource; an unknown key is a no-op. There are no reference counts: repeated loads or lookups do not extend a resource's lifetime. Do not use a resource, or objects dependent on it, after its entry is unloaded or the service is disposed. Atlases and directly loaded maps own their textures, which are disposed with them. During construction, the direct atlas and map loaders retain ownership of textures until the completed resource takes ownership; on a runtime failure they dispose textures already created. `DirectTexture` also disposes its GL handle if texture loading fails during construction. Dispose the service when finished; disposal clears queued entries and disposes loaded resources and is idempotent.

For example, a standalone map load can be paired with an unload once all consumers have stopped using it:

```java
Asset.Map map = Asset.Map.of("level1", "level1.tmx");
TiledMap loaded = assets.load(map);
// Use loaded while its entry remains loaded.
assets.unload(TiledMap.class, map.id());
```

In gameplay, `TiledSystem` resolves a loaded map by the `MapAssets` map ID and unloads the previous map when switching to a different map. Coordinate the lifetimes of other map assets and their consumers separately.

## Migration from AssetManager-backed usage

Create `Asset` definitions at load boundaries, then retain or pass logical IDs at runtime. Replace manager or path-based lookups with `get(ResourceType.class, id)`, and use the same pair for status checks and unloading. Sound and music events carry IDs rather than definitions; animation components carry an atlas ID. Loading uses direct libGDX sound and music APIs, `DirectAtlasLoader`, and `FixedTmxMapLoader` with the project's `projectFilePath` parameter. The service validates each value against its resource class at the load boundary; typed lookup relies on that invariant.
