# Asset service

`AssetService` owns loaded sounds, music, texture atlases, and tiled maps. Construct it with a libGDX `FileHandleResolver`. Each entry is identified globally within the service by its asset class and logical ID, not by its file path. The same ID can identify different types; two IDs pointing to the same path are separate loads with independent ownership.

## Definitions and manifests

`Asset.Sound`, `Asset.Music`, `Asset.Atlas`, and `Asset.Map` are load definitions, not runtime handles. Each provides an ID, path, and resource type; IDs and paths must be nonblank. Their `of(id, file)` helpers prefix files with `audio/`, `audio/`, `objects/packed/`, and `maps/`, respectively. Constructors accept complete paths.

`ManifestLoader.parseMapManifest(mapName)` uses `mapName` as the map's logical ID and reads its manifest. The existing atlas, music, and sound manifest keys remain their logical IDs. `MapAssets` checks that each keyed definition has a matching ID; `all()` collects the map and its declared definitions for loading. `startMusic` selects a music ID or `RANDOM`.

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
