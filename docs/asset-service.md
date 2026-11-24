# Asset service

`AssetService` owns loaded sounds, music, texture atlases, and tiled maps. Construct it with a libGDX `FileHandleResolver`. Each entry is identified globally within the service by its asset class and logical ID, not by its file path. The same ID can identify different types; two IDs pointing to the same path are separate loads with independent ownership.

## Definitions and manifests

`Asset.Sound`, `Asset.Music`, `Asset.Atlas`, and `Asset.Map` are load definitions, not runtime handles. Each provides an ID, path, and resource type; IDs and paths must be nonblank. Their `of(id, file)` helpers use the sound, music, atlas, and map directory prefixes in `GameConsts`. Constructors accept complete paths. `GameCore.create()` first loads `assets/game.settings.json`, which supplies those prefixes and the existing game constants. Directory values must end with `/`; invalid settings stop startup before values are applied.

`ManifestLoader.parseMapManifest(mapName)` uses `mapName` as the logical ID for both the map and its single atlas. Each manifest supplies an `atlasAsset` filename; music and sound manifest keys remain their logical IDs. `MapAssets` checks keyed definitions and `all()` collects the map, atlas, music, and sound definitions for loading. `startMusic` selects a music ID or `RANDOM`.

## Local asset storage

`raw_assets/` and `assets/` are ignored and absent from the current tracked tree and new checkouts. Provision the complete current source tree externally, including settings, data, audio, maps, tilesets, sprites, and the Tiled project, then run `./generate-runtime-assets.sh` from the repository root. The root entrypoint also works when invoked from another directory, fails with an actionable message if the source directory is missing, and creates the runtime directory before generation. Running the game and asset-dependent tests requires the provisioned data. Git ignore is separate from runtime resource ownership; existing Git history has not been purged and may contain asset objects.

Map actor data comes from `raw_assets/data/characters.json` for NPCs and `raw_assets/data/enemies.json` for enemies, generated into the corresponding `assets/data/` paths. Each required file is a top-level map keyed by spawn key and may be empty; spawn role selects the file. Each definition requires a nonblank `name` and positive integer `max_health`, used as initial and maximum spawn health. NPCs retain their loadout and enemies require a nullable top-level `weapon` instead. The player schema is unchanged. See the README for complete examples.

The ignored raw and runtime copies in this checkout have been migrated locally. Other checkouts must migrate their own ignored data: move entries from the former `actors.json` role maps into the corresponding files, preserve their graph keys and detection radii, add names, and retain map actor `max_health`. Regenerate runtime assets. There is no `actors.json` fallback.

### Reproducing combat tuning

Both asset trees are ignored, so these JSON values are not included in the PR. After provisioning the raw tree, set the following existing fields, preserving all other data:

- `raw_assets/data/player.json`: set `max_health` to `50`.
- `raw_assets/data/characters.json`: set `basic_npc.max_health` to `50`.
- `raw_assets/data/enemies.json`: set `brainless_undead.max_health` to `50`.
- `raw_assets/data/weapons.json`: set `weapons.basic_sword.attack_stats.primary.stats.damage` to `{ "min": 15, "max": 20 }` and its secondary damage to `{ "min": 10, "max": 10 }`; set both `weapons.basic_bow` attack damage values to `{ "min": 20, "max": 30 }`. Unarmed primary and secondary retain scalar damage `3`.

Run `./generate-runtime-assets.sh` from the repository root to regenerate the corresponding `assets/data/` files; do not edit generated files directly. Damage accepts either a positive whole-number scalar or an object with positive whole-number `min` and `max`, with `min <= max`. Scalar hits roll within ±10% after draw scaling; ranges roll an inclusive integer first, then apply draw scaling. Both round once before subtraction. Floating damage displays effective health loss capped by health before the hit. Bow draw multipliers are 25%, 50%, 75%, and 100%.

The stylized top bar spans the gameplay viewport with open sides and visually distinguished text. Player HP and 24-hour game time are horizontally centered together. HP text uses the same green-yellow-red current-to-maximum health ratio as floating damage, without a colorful border.

## Terrain tilesheet generation

Author maps and terrain sheets normally in `raw_assets/`; do not manually pad generated files or edit `assets/`. `./generate-runtime-assets.sh` extrudes a one-pixel repeated RGBA border around each tile of grid-based, single-image terrain tilesets, whether defined in an external TSX or inline in a TMX. The generated PNG and tileset metadata use margin 1 and spacing 2, with the image reference and dimensions updated to match the generated sheet.

Source margin and spacing default to zero when absent; valid nondefault values are read as authored. The actual source image dimensions determine the grid. Generation fails for invalid grid geometry or explicit columns that conflict with that grid, and normalizes a stale tilecount to the actual grid. Tile order and IDs, properties, animation, and collision are preserved. Image-collection tilesets and image-layer images are not extruded, and source files are never rewritten.

For each map, generation also gathers used grid tiles across all its terrain sheets, including placed tile objects and animation frames, and appends them to the existing manifest atlas alongside object regions. The map's TMX reference records its selected local IDs; shared TSX files do not carry map-specific selections. Terrain regions use the reserved name `__lute_tile/<firstgid>` and the tileset-local tile ID as the index, with repeated edge padding. Qualifying the key by tileset preserves distinct animation frames and placements even when their GIDs overlap. Regenerate runtime assets produced by the earlier GID-only atlas format. Terrain pages are at most 2048×2048 and can overflow to additional pages; a single page or a particular reduction in texture switches is not guaranteed. Chunked tile layers are unsupported. Terrain-only and empty maps still receive the manifest atlas.

With a map atlas, `FixedTmxMapLoader` resolves marked terrain from its regions and rejects missing or invalid regions; it does not silently fall back to sheets for those tiles. Without an atlas, standalone map loading uses the generated padded sheets. The map borrows atlas textures: the atlas owner must keep them alive until map consumers stop using them. Generated sheets, atlas pages, TMX files, and shared definitions follow the existing ownership and transaction rules described in the [README](../README.md#per-map-generation). Padding reduces texture bleed but does not address every cause of tile seams.

## Animation authoring and migration

The shared `AnimationData` schema has four fields: speed, atlas key, sound frame, and frame interval in seconds. Player and weapon JSON use `speed`, `atlas_key`, `sound_frame`, and `frame_interval` inside `animation`. Tiled's `Animation` class uses camelCase `speed`, `atlasKey`, `soundFrame`, and `frameInterval`; `AnimatedPhysicObject.animation` is an instance of that class. Players start in `IDLE`; animated environmental objects start in `DEFAULT`. Neither initial type is set in Tiled. Keep the Tiled class and Java/JSON schema synchronized in fields, types, and meaning; values are independently authored.

The manifest supplies the one environmental atlas for the map; Tiled objects no longer select an atlas asset. A tile's animation class supplies its values. A placement can override individual fields; `FixedTmxMapLoader` merges explicitly authored members with the tile values before LibGDX's class defaults can replace them. Migrate old properties and remove `keyFrames`. The tree atlas key identifies its region; the tree uses a 0.1-second interval and speed 0.35. Only weapon attacks consume the sound frame. Player move and damage sounds list `sword_hit.wav` as data-only placeholders; they are not loaded or played.

Characters use six shared, potentially multipage atlases generated from `raw_assets/sprites/`: character, head, body, hands, feet, and weapons. Base frames are flat in `character/`; equipment frames live in `equipment/<set>/<slot>/`, with `head`, `body`, `hands`, and `feet` slots. The basic set requires all four slots. Generation gathers all sets by slot; optional `equipment/pack.json` settings apply to all four slot atlases, and nested overrides and unknown slots are rejected. The one exception is `accessories` export folders: generation reports and skips them for now, preserving their source files without packing an accessories atlas or making accessories equipable. Weapons remain under `weapons/melee/basic_sword/` and `weapons/ranged/basic_bow/`. Generated files under `assets/atlases/` are runtime outputs. Sprite packing defaults to maximum page dimensions of 2048×2048; optional `pack.json` settings inherit omitted dimensions and can explicitly override them. Sprite packing rejects `combineSubdirectories=true`: pages never mix source asset folders. A single folder can still overflow onto multiple pages, without a guarantee that an animation stays together. Current character and equipment atlases each use one 2048×2048 page; weapons use two 2048×2048 pages, one for the 160 sword frames and one for the 160 bow frames. All current eight-frame sequences stay on one page. Character action regions are flat, such as `idle_down`; equipment regions use item prefixes, such as `basic/shot_up`. The common weapons atlas retains category and item prefixes, such as `melee/basic_sword` and `ranged/basic_bow`. A shared animation clock aligns all visible layers; `cast` is not required.

The corrected source inventory has six actions (death, idle, jab, shot, slash, walk), four directions, and eight frames per direction for the base and each basic equipment set. Sword lacks shot, bow lacks slash, and neither has cast. Each source canvas is 128×128; the nominal 64×64 logical area is centered at [32, 96] on each axis. Render the full canvas at pixel scale with a -32 pixel offset relative to the logical transform, retaining transparent padding and overhang. Do not crop or downsample it. Spawn and weapon anchors use the logical area; configure `logical_size` explicitly rather than inferring it from the canvas.

Player JSON authors `logical_size` as `{ "width": 64, "height": 64 }`, `collision` as `{ "shape": "ELLIPSE", "bounds": { "x1": 20, "y1": 4, "x2": 44, "y2": 14 }, "rotation": 0 }`, and `hitbox_bounds` as `{ "x1": 16, "y1": 6, "x2": 48, "y2": 56 }`. Bounds use bottom-left logical-local pixels, converted to world units once. These define a 24×10 solid feet fixture and a separate 32×50 elliptical persistent damage sensor. Non-circular ellipses use the existing eight-vertex Box2D approximation. The hurtbox neither blocks movement nor contributes to map triggers; attack sensors use it instead of solids for explicit hurtbox targets, while generic full-sprite targets retain their fallback. These initial fitted bounds are accepted pending live user review; focused automated validation passed.

The default loadout has basic head, body, hands, and feet equipment and `basic_sword`. `Loadout` owns the equipment and selected weapon, its immutable weapon catalogue, and the equipment validator; it does not hold `Combat`. For guarded gameplay changes, call `engine.getSystem(LoadoutSystem.class).equip(entity, equipment)` or `equipWeapon(entity, weaponId)`. The system returns false before validation or mutation while combat is pending or active; combat is optional. An idle null weapon ID selects unarmed; an unknown ID throws without changing the selection. The component setters validate selections but do not enforce the combat guard. The renderer reads both equipment and weapon from `Loadout` and composes each visible character in its own offscreen cell, batching layers by actual atlas page while preserving their order, then world-depth-sorts each completed character with ordinary objects. See the [composite character specification](../.internal-dev/specifications/composite-characters.md) for the rendering and loadout contract.

## Sensor, teleport, and spawn authoring

On an orthogonal map, author trigger objects in an object layer named `Trigger` or `Triggers`. Trigger layers and their ancestor groups must have zero offsets and unit parallax. `Sensor` and `Teleport` objects support filled rectangles, circles/ellipses, and simple polygons, including rotation. Rectangles and ellipses must have positive dimensions; invalid, self-intersecting, or degenerate polygons are rejected. Non-circular ellipses use a fixed eight-sided approximation. Concave polygons and simple polygons with more than eight vertices use multiple convex fixtures but remain one logical detector, without duplicate events and with the same teleport reconciliation as a single-fixture detector. Points and polylines are not detector shapes; text and tile objects are excluded. The Tiled object name identifies the sensor and must be unique among sensors. A `Sensor` has `sensorType` set to `GENERAL`; a `Teleport` also emits `GENERAL` sensor enter and exit events. These events carry the sensor name, type, and player; only the player's non-sensor fixtures participate.

For an in-place `Teleport`, set `targetMap` to `THIS` (case-insensitive) and `targetSpawn` to a unique `TELEPORT`-category `SpawnPoint` on the current map. It centers the player on the point, stops velocity, preserves facing, and resets interpolation.

For map travel, set `targetMap` to the destination map's logical manifest ID and `targetSpawn` to a named `PLAYER` or `TELEPORT` point there. An explicit ID matching the current map still rebuilds its runtime; use `THIS` for the in-place behavior. The destination is synchronously loaded and prepared before the old runtime is replaced after the ECS and physics update. Invalid or missing destinations are logged and leave the current runtime intact. Arrival portals require exit before reentry, preventing a teleport loop. Named travel retains live health, loadout, and facing, but resets transient combat and input state. Hide/show remounts the current entry with the same session state; a new session uses player JSON defaults. No saves or disk hot reload are provided.

Author a `SpawnPoint` as a zero-size point. A data-only `SpawnArea` must be an unrotated rectangle with positive dimensions; an area with behavior supports an unrotated positive-size rectangle or valid polygon. Use a Tiled object name unique among spawn markers and a `spawnType` of `PLAYER`, `TELEPORT`, `NPC`, `ENEMY`, `OBJECT`, or `ITEM`. At most one `PLAYER` point is allowed; it creates the initial player. NPC and ENEMY points create map actors, preferring `spawnKey` and falling back to legacy `actorId` when no explicit key is authored. OBJECT and ITEM points create objects and require `spawnKey`. Both marker classes use a nested Tiled `spawnInfo` class property of type `SpawnInfo`, containing `spawnType`, `spawnKey`, and `facing`; `NULL` counts as an unset key. The spawn type enum includes `TELEPORT` and `OBJECT`. Legacy flat `spawnType`, `spawnKey`, and `facing` properties override nested values when present. Migrate markers by their intended roles, update the ignored raw Tiled project and maps, and regenerate runtime assets. PLAYER, NPC, ENEMY, OBJECT, and ITEM points require the `facing` enum; the Tiled project supplies `DOWN`. A new player uses the marker facing, while travel preserves the live player's facing. Data-only areas do not spawn actors. Behavior-enabled enemy areas support spawning; the `hoard_mode` arena requires four enemy polygons as described below. The map owner cleans up sensor objects.

### Hoard arena provisioning

Both asset trees are ignored. Provision the `hoard_mode` map and manifest in `raw_assets/maps/hoard_mode/`, with their referenced tilesets and art. In the raw Tiled project, add the string member `behaviorKey` to `SpawnInfo` with the literal default `NULL` (not JSON null). On the map's `Triggers` layer, author four simple polygon `SpawnArea` objects named `SpawnArea1` through `SpawnArea4`; each nested `spawnInfo` must set `behaviorKey` to `agroWanderingHoard`, `spawnType` to `ENEMY`, and `spawnKey` to `brainless_undead`. Provide that enemy definition in `raw_assets/data/enemies.json` and a player spawn point. Set `startupMap` to `hoard_mode` in `raw_assets/game.settings.json` to start there. Preserve other assets and regenerate with `./generate-runtime-assets.sh`; this produces the ignored runtime map, project, and data under `assets/`.

Each opening or reinforcement batch has one total budget distributed across the four areas. For each enemy, the baseline melee `spawnKey` or `undead_archer` is chosen independently with equal probability. The rolls do not guarantee an exact half of each type in a batch. Friendly fire and retaliation are unchanged.

The old fixed `CourtyardArcher` placement is absent; archers spawn through batches. Retain this entry in the existing top-level map in `raw_assets/data/enemies.json`, preserving the other entries:

```json
"undead_archer": {
  "name": "Undead Archer",
  "max_health": 50,
  "behavior_graph": "wander_archer",
  "move_speed": 4.0,
  "logical_size": { "width": 64, "height": 64 },
  "collision": { "shape": "ELLIPSE", "bounds": { "x1": 20, "y1": 4, "x2": 44, "y2": 14 }, "rotation": 0 },
  "hitbox_bounds": { "x1": 16, "y1": 6, "x2": 48, "y2": 56 },
  "sounds": { "move": "sword_hit.wav", "damage": "sword_hit.wav" },
  "animation": { "speed": 1, "atlas_key": "brainless_undead", "sound_frame": 0, "frame_interval": 0.12 },
  "detection_bounds": { "radius": 320 },
  "weapon": "basic_bow"
}
```

The existing raw and generated assets remain ignored; regenerate them with `./generate-runtime-assets.sh` after updating the map and data.

The arena starts with 1,000 player HP and 120-simulation-second rounds. Each round restores a living player, spawns an opening batch using the full five-enemy baseline, and schedules reinforcements every 20–40 simulation seconds. Each reinforcement batch independently samples 25–50% inclusive of that baseline. Both batch types use the multiplier `1 + 0.05 × round number`: +5% in round 1 and +50% in round 10. Apply the reinforcement percentage and the unchanged ×1.15 night multiplier before rounding each final batch count to the nearest whole number, with ties rounded up and no fractional carry. Each credited player kill of a map-owned enemy awards the base five points multiplied by the current round multiplier, rounded per kill to the nearest whole number with ties up. Night does not affect score, and earlier kills are not repriced when the round changes. The HUD adds round, time remaining, kills, and score to HP and world time. Arena night runs overnight from 20:00 inclusive to 06:00 exclusive by world clock; N is ignored there. A new session starts at 06:00, and map travel does not reset the clock. Player death is terminal in the arena; ordinary maps retain their existing respawn behavior.

OBJECT and ITEM points use the required shared `objects.atlas`, generated from `raw_assets/sprites/objects/`, and required `raw_assets/data/objects.json`, keyed by `spawnKey`. Each definition requires the exact keys `animation`, `collision`, and `emissive`, each explicitly nullable; omission is invalid. `animation` is nullable: null selects the static atlas region named exactly by `spawnKey`; otherwise it uses the player `AnimationData` fields and `atlas_key` selects indexed frames. `sound_frame` is required but unused for objects. `collision` is explicitly nullable for objects; null creates no physics body or fixture. Otherwise its bounds use bottom-left sprite-local pixels. The sprite is centered on the point, while the fixture retains its offset from the sprite center; object facing rotates both around the point: DOWN 0°, RIGHT 90°, UP 180°, LEFT -90°. Collision rotation adds to facing without rotating the artwork.

To reproduce the local ignored asset update, add a `FireSpawn` OBJECT point to `test_tiled` at Tiled coordinates x=134, y=164, with `spawnKey` `campfire1` and facing `DOWN`. Split the old 128×64 campfire sheet into six 32×32 frames in row-major order, ignoring the last two blank cells, and save them as `raw_assets/sprites/objects/campfire1/campfire1_00.png` through `campfire1_05.png`. Remove the old sheet after splitting it so it is not packed alongside the frames. Add this entry to `raw_assets/data/objects.json`:

```json
{
  "campfire1": {
    "animation": {
      "atlas_key": "campfire1/campfire1",
      "speed": 1,
      "frame_interval": 0.1,
      "sound_frame": 0
    },
    "collision": { "shape": "ELLIPSE", "bounds": { "x1": 4, "y1": 2, "x2": 28, "y2": 14 }, "rotation": 0 },
    "emissive": {
      "color": "#FFAA55", "radius_pixels": 128, "intensity": 0.9,
      "pulse": { "period_seconds": 1.2, "radius_strength": 0.12, "intensity_strength": 0.18 }
    }
  }
}
```

For the lighting migration, preserve the existing FireSpawn placement and art; the preceding placement and frame instructions apply only when provisioning an older tree without them. Add `emissive: null` to every nonemitter in `raw_assets/data/objects.json`, and use the shown block for `campfire1`. A nonnull emissive requires `color` as `#RRGGBB`, positive `radius_pixels`, intensity greater than zero and at most one, and a required nullable `pulse`. A nonnull pulse requires positive `period_seconds` and independent `radius_strength` and `intensity_strength` in [0, 1). Numbers and their derived values must remain finite and usable. The two channels use `base × (1 + strength × sin(phase))`; null pulse or zero strength is steady. Missing, duplicate, unknown, mistyped, or invalid members fail loading; `emssive` is not an alias.

Add `"nightAmbient": { "tint": "#8299CC", "brightness": 0.35 }` to `raw_assets/game.settings.json`. Both fields are required: tint is `#RRGGBB` and brightness is finite in [0, 1]. Regenerate with `./generate-runtime-assets.sh` and restart to apply changes; no live reload or missing-data fallback is provided. These are the current local values, not defaults. Outside the hoard arena, each new gameplay screen starts in day and N toggles night independently of world time. The arena instead uses world time for lighting. Solid active Box2D fixtures shadow each light independently, excluding its own body and sensors. A different filled solid containing or touching the light origin blocks that light; edge and chain segments do not have filled interiors. Shadows follow physics geometry, not artwork. See [README](../README.md#night-lighting-migration-and-validation) for testing commands.

Migrate the ignored raw data files: `raw_assets/data/player.json`, `characters.json`, `enemies.json`, `objects.json`, and `projectiles.json`. Replace each `collision_bounds` with required `collision: { shape, bounds, rotation }`; only objects may explicitly use null. Preserve existing actor and object bounds as `ELLIPSE` with rotation 0. For `iron_arrow`, formerly a triangle, use `ELLIPSE`, rotation 0, and bounds `{ "x1": 0, "y1": 0, "x2": 10, "y2": 8.5 }`. Regenerate the ignored `assets/data/` copies with `./generate-runtime-assets.sh` after updating the raw Tiled project, map, sprites, and data.

Collision shapes are `ELLIPSE`, `RECT`, `CIRCLE`, and `TRIANGLE`. Bounds `x1`, `y1`, `x2`, `y2` are local pixels with positive width and height; circles require equal dimensions. Rotation is finite counterclockwise degrees about the bounds center. Actor and object circles use native circle fixtures; projectile circles use a 32-vertex sweep polygon. Ellipses use eight vertices; triangles have a bottom base and a top-midpoint apex. For projectiles the unrotated bounds' top midpoint anchors at the art tip: X is full width and Y is length behind the tip. Rotation combines with the flight tangent and does not rotate the art independently. Hitboxes, Tiled collision, arrow flight, and artwork are unchanged.

## Weapon assets and animation timing

Map manifests describe map-owned assets. Weapon definitions live in `assets/data/weapons.json`, which contains a shared `atlas` path and a `weapons` map. Each weapon has an `atlas_key` and `attack_stats` with separate `primary` and `secondary` records. Each attack owns its `animation`, `bounds`, `stats`, `collision_type`, and `sounds.attack` and `sounds.hit` paths. The unarmed definition uses `NONE` for its weapon layer.

`WeaponAssets.of(weaponId, definitions)` resolves both attacks' sound filenames relative to the configured sound directory. Initial loading queues attack and hit sounds for both slots of every configured weapon, including unarmed. Each attack can select its own paths; reuse of existing WAV files is approved. Runtime IDs are `weapon/<id>/<primary|secondary>/<attack|hit>`; animation receives the resolved attack ID rather than weapon filenames or selection rules. Hit sounds are loaded but not played. Loading these assets does not implement inventory, UI, or automatic unloading.

Space triggers the primary attack: sword slash or bow shot. Shift triggers the secondary jab. Without a weapon, both trigger a hand jab. A bow shot launches the authored projectile; its flight and collision contract is described in the composite character specification.

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

`unload(type, id)` cancels a queued entry or removes and disposes a loaded resource; an unknown key is a no-op. There are no reference counts: repeated loads or lookups do not extend a resource's lifetime. Do not use a resource, or objects dependent on it, after its entry is unloaded or the service is disposed. Atlases own their textures; directly loaded maps own textures they load themselves. Maps loaded with a borrowed atlas do not own its textures, so unload the map and stop its consumers before unloading that atlas. During construction, the direct atlas and map loaders retain ownership of textures until the completed resource takes ownership; on a runtime failure they dispose textures already created. `DirectTexture` also disposes its GL handle if texture loading fails during construction. Dispose the service when finished; disposal clears queued entries and disposes loaded resources and is idempotent.

For example, a standalone map load can be paired with an unload once all consumers have stopped using it:

```java
Asset.Map map = Asset.Map.of("level1", "level1.tmx");
TiledMap loaded = assets.load(map);
// Use loaded while its entry remains loaded.
assets.unload(TiledMap.class, map.id());
```

In gameplay, `TiledSystem` synchronously loads and owns map assets it acquires while preparing a runtime. It retains shared definitions needed by the destination and releases outgoing owned assets after rebinding their consumers; assets already registered by another owner remain that owner's responsibility. A named reload of the current map reuses registered asset data, not changed files on disk. The session retains player health, loadout, and facing separately from the rebuilt map entities.

## Migration from AssetManager-backed usage

Create `Asset` definitions at load boundaries, then retain or pass logical IDs at runtime. Replace manager or path-based lookups with `get(ResourceType.class, id)`, and use the same pair for status checks and unloading. Sound and music events carry IDs rather than definitions; animation components carry an atlas ID. Loading uses direct libGDX sound and music APIs, `DirectAtlasLoader`, and `FixedTmxMapLoader` with the project's `projectFilePath` parameter. The service validates each value against its resource class at the load boundary; typed lookup relies on that invariant.
