# Lute

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Runtime assets

Both `raw_assets/` and `assets/` are local and ignored by Git; their contents are not included in the current tracked tree or new checkouts. Existing Git history retains old asset objects; it has not been purged. In a new checkout, provide the complete current `raw_assets/` tree externally, including settings, data, audio, maps, tilesets, sprites, and the Tiled project. Edit source assets in `raw_assets/`, not the generated files in `assets/`. Shared settings and the Tiled project live at the source root; `data/` and `audio/` contain shared game data and sounds. Each map directory, such as `raw_assets/maps/test_tiled/`, contains a matching TMX and manifest and an `objects/` directory with source images and `pack.json`. Shared terrain tilesets live under `raw_assets/tilesets/` and can be referenced by multiple maps. The manifest must name `<map>.atlas` and `<map>/<map>.tmx`, and include `music_assets` and `sound_assets` objects, even when empty.

`raw_assets/game.settings.json` requires `startupMap`, a map name rather than a path, and `projectPath`, the filename of the root Tiled `.tiled-project` file. The local values are `test_tiled` and `game.tiled-project`, respectively. The asset generator also allows its Tiled project filename to be configured; keep it aligned with `projectPath`.

Authored gameplay JSON follows the same source-to-runtime path: provision `raw_assets/data/player.json`, `characters.json`, `enemies.json`, `weapons.json`, and `projectiles.json`, then generate `assets/data/`. The mandatory `characters.json` and `enemies.json` files replace `actors.json`; each is a top-level map keyed by actor ID. NPC definitions mirror player fields except `name`, with `detection_bounds: { "radius": ... }` in pixels. Enemy definitions use the same actor fields but require a nullable top-level `weapon` instead of a loadout. Animation `atlas_key` selects the actor's sprite prefix. Move speed and sounds are authored, but map actors remain static and do not attack.

These examples reproduce the local raw files, which are ignored and not shipped. If needed, copy them into the corresponding files before generating runtime assets:

`raw_assets/data/characters.json`:

```json
{
  "basic_npc": {
    "max_health": 100,
    "move_speed": 4.0,
    "logical_size": {
      "width": 64,
      "height": 64
    },
    "collision_bounds": {
      "x1": 20,
      "y1": 4,
      "x2": 44,
      "y2": 14
    },
    "hitbox_bounds": {
      "x1": 16,
      "y1": 6,
      "x2": 48,
      "y2": 56
    },
    "sounds": {
      "move": "sword_hit.wav",
      "damage": "sword_hit.wav"
    },
    "animation": {
      "speed": 1,
      "atlas_key": "basic_npc",
      "sound_frame": 0,
      "frame_interval": 0.12
    },
    "detection_bounds": {
      "radius": 96
    },
    "loadout": {
      "body": "basic",
      "hands": "basic",
      "feet": "basic"
    }
  }
}
```

`raw_assets/data/enemies.json`:

```json
{
  "brainless_undead": {
    "max_health": 100,
    "move_speed": 4.0,
    "logical_size": {
      "width": 64,
      "height": 64
    },
    "collision_bounds": {
      "x1": 20,
      "y1": 4,
      "x2": 44,
      "y2": 14
    },
    "hitbox_bounds": {
      "x1": 16,
      "y1": 6,
      "x2": 48,
      "y2": 56
    },
    "sounds": {
      "move": "sword_hit.wav",
      "damage": "sword_hit.wav"
    },
    "animation": {
      "speed": 1,
      "atlas_key": "brainless_undead",
      "sound_frame": 0,
      "frame_interval": 0.12
    },
    "detection_bounds": {
      "radius": 160
    },
    "weapon": "basic_sword"
  }
}
```

At startup, `DataLoader` loads them through `GameConsts.DATA_PATH`, and `GameData` supplies the resolved data to gameplay. There is no bundled-resource or classpath fallback. If the externally provisioned tree lacks `raw_assets/data/projectiles.json`, create it with this starter data:

```json
{
  "projectiles": {
    "iron_arrow": {
      "atlas_key": "projectiles/IronArrow",
      "speed": 8.0,
      "lifetime": 5.0,
      "collision_bounds": { "length": 8.5, "width": 10.0 },
      "arc_height": 0.30,
      "tip_x": 16.0,
      "tip_y": 32.0,
      "art_degrees": 90.0
    }
  }
}
```

Existing externally provisioned `raw_assets/data/weapons.json` files must be migrated: add a finite, positive `stats.cooldown_seconds` to every primary and secondary attack, including unarmed. The locally tuned values are sword primary `0.30` and secondary `0.24`, bow primary `0.25` and secondary `0.32`, and unarmed `0.32` for both slots. These local asset edits are not tracked.

Cooldowns are independent per fighter, stable weapon definition ID, and attack slot; there is no global timer. They age during gameplay before input independently of playback, including while another weapon is equipped. Switching away and back does not reset them; fresh map combat state clears them. Melee acceptance or arrow release starts the corresponding cooldown, not bow draw or aim cancellation. A ready attack can replace current playback even while the old attack is still cooling down. This can truncate not-yet-reached hit or sound cues, even when the old cooldown is long. Queued physics hits are resolved first, the old melee sensor is removed, and existing arrows continue. Inputs are not buffered, natural animation completion does not clear cooldown, and normal movement is unchanged. Cooldowns add no weapon-swapping restriction beyond existing attack-state guards. Bow draw uses the primary slot's readiness, and aim reserves input while held.

### Night lighting migration and validation

In `raw_assets/data/objects.json`, every OBJECT/ITEM definition must contain `animation`, `collision`, and `emissive`, even when their value is null. Set `emissive: null` for nonemitters. The existing `campfire1` keeps its art, collision, and FireSpawn placement; its locally authored emissive value is:

```json
{ "color": "#FFAA55", "radius_pixels": 128, "intensity": 0.9,
  "pulse": { "period_seconds": 1.2, "radius_strength": 0.12, "intensity_strength": 0.18 } }
```

Add `"nightAmbient": { "tint": "#8299CC", "brightness": 0.35 }` and the required `debugLayers` object to `raw_assets/game.settings.json`:

```json
"debugLayers": {
  "physics": true,
  "triggers": true,
  "rawLightMap": false,
  "lightingOutlines": false
}
```

All four fields must be JSON booleans; missing or invalid fields fail startup. These are local authored sample values, not code defaults. Edit the raw settings, run `./generate-runtime-assets.sh`, and restart to apply changes. See [asset authoring](docs/asset-service.md) for validation and the complete object example.

Press N in gameplay to toggle day and night. Each new screen starts in day; mode and pulse phase survive travel and hide/show, and pause freezes the pulse. The toggle does not change world time. At night solid Box2D fixtures cast shadows, except the emitter's own fixtures; sensors and arrows without solids do not. Lighting follows collision geometry rather than art silhouettes. Crosshair and debug overlays remain unlit.

F3 no longer cycles diagnostics. The four debug layers are independent and can appear together: `physics` shows physics geometry, `triggers` shows sensor diagnostics, and `lightingOutlines` shows solid fixture outlines and pulsed emitter rings by day or night. At night `rawLightMap` replaces world art with the combined ambient and emitter lightmap; by day it leaves the ordinary world view unchanged. Other enabled overlays can still appear over the raw lightmap.

After provisioning or changing source assets, run:

```sh
./generate-runtime-assets.sh
```

For focused regressions and the integrated checks, run:

```sh
./gradlew :core:lightingDataTest :core:lightingBehaviorTest :core:dataLoaderTest :core:inputSystemTest :core:worldTimeSystemTest
./gradlew check
./gradlew :lwjgl3:lightingRenderTest :lwjgl3:compositeRenderTest :lwjgl3:gameScreenLifecycleTest :lwjgl3:mapTravelTest
./gradlew :lwjgl3:run
```

The synthetic lighting data, behavior, and render tests do not require the ignored game assets; the render test requires a GL-capable display. Other integration checks and the game require provisioned assets. On `test_tiled`, compare day, N-enabled night, and return to day; inspect the campfire pulse and moving-player shadow without relocating FireSpawn.

The root script works from any current directory, reports a missing `raw_assets/` directory, and creates `assets/` before invoking `:util:generateRuntimeAssets`; generation is not automatic when running or building the game. It copies shared files, generates a per-map object atlas under `assets/atlases/`, and publishes map directories under `assets/maps/` and referenced shared tilesets under `assets/tilesets/`. Object image collections resolve through the atlas at runtime; terrain sheets remain separate. Object packing must preserve paths, indexes, dimensions, and orientation: do not enable rotation, whitespace stripping, path flattening, or nested pack settings.

Character sprites come from `raw_assets/sprites/`: base frames live in `character/player/` and `character/basic_npc/` and are packed into a shared `character.atlas` with actor-prefixed regions. Enemy body sprites with baked equipment live under `enemies/` (currently `brainless_undead/`) and are packed into a separate `enemies.atlas`, also with actor-prefixed regions. Equipment frames live in `equipment/<set>/<slot>/` (with `head`, `body`, `hands`, and `feet` slots), and selectable weapons use the shared weapons atlas, including `weapons/melee/basic_sword/` and `weapons/ranged/basic_bow/`. The basic set requires all four slots. Generation gathers equipment sets by slot into four atlases; an optional `equipment/pack.json` supplies shared settings for them, while nested packing overrides and unknown slots are rejected, except that `accessories` export folders are reported and skipped for now. Their source files are preserved; accessories are not packed or equipable. Sprite packing defaults to a maximum page size of 2048×2048; optional `pack.json` settings retain these defaults for omitted dimensions and may explicitly override them. Sprite pages never mix source asset folders; `combineSubdirectories=true` overrides are rejected. A folder that outgrows a page can still span pages, so animation sequences are not guaranteed to remain together in that case. Equipment regions include the item prefix, such as `basic/shot_up`; weapon regions retain category and item prefixes, such as `melee/basic_sword` and `ranged/basic_bow`. These generated atlases are runtime outputs, not source assets. Composite source frames are 128×128 canvases around a centered 64×64 logical character. The rendering contract retains the full canvas at pixel scale, including padding and overhang; it does not shrink or crop it. The player's and map actors' logical sizes and separate feet and hurtbox bounds are authored in their respective data. Earlier focused validation passed for the canvas correction; live review of the initial bounds remains pending. See the [composite character specification](.internal-dev/specifications/composite-characters.md) for composition and loadout behavior.

### Map actor demo

Tiled `PLAYER`, `NPC`, and `ENEMY` spawn points require a `facing` enum value: `UP`, `DOWN`, `LEFT`, or `RIGHT`. The Tiled class supplies the `DOWN` default; Java does not supply a fallback. The sample NPC faces `LEFT`. A new player uses the player marker's facing, while named travel and in-place travel retain the live player's existing facing. `TELEPORT` and `ITEM` points do not use facing.

The existing `NPC` and `ENEMY` points on `test_tiled` spawn map-owned actors. The NPC starts with basic body, hands, and feet equipment, without a head item or weapon; it can equip compatible head armor. All four armor slots are independently optional for both the player and basic NPC. The player's authored defaults remain the basic set and sword: its loadout object is required, but each slot, including the weapon, may be omitted or null; non-null IDs must not be blank. Equipped frames must still match the actor's base animation. The demo enemy uses its baked body art and starts with a sword and no separate armor; its weapon can be changed through the existing `LoadoutSystem`. Both actors have a stationary solid feet ellipse, a separate sensor hurtbox, and 100 health. Their bounds and health are authored independently of player JSON. They can take damage; NPCs have no reactive behavior, and enemies can face a visible player but do not move or attack. Both have static query-only detection circles centered at their feet, with radii authored in pixels. They have no dialogue, player control, or camera ownership. Named travel rebuilds them with the map. In Tiled, set the required string property `actorId` on `NpcSpawn` to `basic_npc` and on `EnemySpawn` to `brainless_undead`. Provision the raw assets and run the root generation script before launching `./gradlew lwjgl3:run`. Visit the marked NPC and ENEMY locations on `test_tiled` to inspect their appearance and placement, then travel away and back to check that they are rebuilt.

### Per-map generation

`TexturePackerTool.generateMap` processes each source directory containing `objects/`. It validates that the matching manifest references `<map>.atlas` and `<map>/<map>.tmx` and supplies music and sound asset objects (snake_case or camelCase names are accepted). It reads `objects/pack.json` and requires indexes and combined subdirectories, a single unscaled output with no scale suffix, and no rotation, whitespace stripping, path flattening, or nested `pack.json` overrides.

The method packs `objects/` into a temporary atlas, reads it back, and rejects duplicate region name/index pairs. It stages the atlas and pages under `atlases/`, and the TMX and manifest together under `maps/<map>/`. Referenced tileset definitions, terrain sheets, and image-layer images retain their source-relative paths, including shared files under `tilesets/`; object collection images are packed rather than copied. Map dependencies must stay within the map's own directory or the shared `tilesets/` directory.

For each image-collection tile, generation requires an existing image under `objects/` and a collection definition directly in that directory. It resolves the atlas key from the image's relative path without its extension, interpreting a trailing `_<number>` as the region index. The corresponding region must exist, be unrotated and untrimmed, and match the image dimensions declared in the tileset.

This work writes to the transaction's `generated/` staging directory, not directly to `assets/`. Only after all maps are generated does the outer generator check ownership and collisions, prepare the complete output, and publish it using atomic directory moves.

`assets/.runtime-assets-owned` records generated file paths and hashes. Generation preserves unowned files, refuses to overwrite modified owned files or unowned collisions, and removes previously owned files no longer generated. Make intended changes in `raw_assets/`; if an owned output was edited, preserve any needed changes in the source and restore the owned output before regenerating. For an initial migration with identical existing files, `./generate-runtime-assets.sh -PruntimeAssetsAdoptIdentical=true` explicitly adopts matching collisions; differing files still require manual resolution. `assets/game.tiled-session` is not owned by the generator.

Publication uses `.runtime-assets-transaction/` beside `assets/` for staging and backup. If generation reports a retained transaction, first ensure no generator is running and inspect its `previous/`, `prepared/`, and `generated/` contents. Preserve the only surviving copy of runtime assets: if `assets/` is missing and `previous/` exists, restore `previous/` to `assets/`; if both exist, inspect and retain whichever data is needed before removing the transaction directory and retrying. The generator rejects symlinks in source and output trees and relies on atomic directory moves.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
