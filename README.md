# Lute

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Runtime assets

Both `raw_assets/` and `assets/` are local and ignored by Git; their contents are not included in the current tracked tree or new checkouts. Existing Git history retains old asset objects; it has not been purged. In a new checkout, provide the complete current `raw_assets/` tree externally, including settings, data, audio, maps, tilesets, sprites, and the Tiled project. Edit source assets in `raw_assets/`, not the generated files in `assets/`. Shared settings and the Tiled project live at the source root; `data/` and `audio/` contain shared game data and sounds. Each map directory, such as `raw_assets/maps/test_tiled/`, contains a matching TMX and manifest and an `objects/` directory with source images and `pack.json`. Shared terrain tilesets live under `raw_assets/tilesets/` and can be referenced by multiple maps. The manifest must name `<map>.atlas` and `<map>/<map>.tmx`, and include `music_assets` and `sound_assets` objects, even when empty.

After provisioning or changing source assets, run:

```sh
./generate-runtime-assets.sh
```

The root script works from any current directory, reports a missing `raw_assets/` directory, and creates `assets/` before invoking `:util:generateRuntimeAssets`; generation is not automatic when running or building the game. It copies shared files, generates a per-map object atlas under `assets/atlases/`, and publishes map directories under `assets/maps/` and referenced shared tilesets under `assets/tilesets/`. Object image collections resolve through the atlas at runtime; terrain sheets remain separate. Object packing must preserve paths, indexes, dimensions, and orientation: do not enable rotation, whitespace stripping, path flattening, or nested pack settings.

Character sprites come from `raw_assets/sprites/`: base frames are flat in `character/`, equipment frames live in `equipment/<set>/<slot>/` (with `head`, `body`, `hands`, and `feet` slots), and weapon frames remain under `weapons/melee/basic_sword/` and `weapons/ranged/basic_bow/`. The basic set requires all four slots. Generation gathers equipment sets by slot into four atlases; an optional `equipment/pack.json` supplies shared settings for them, while nested packing overrides and unknown slots are rejected, except that `accessories` export folders are reported and skipped for now. Their source files are preserved; accessories are not packed or equipable. Six shared atlases are generated under `assets/atlases/`: character, head, body, hands, feet, and weapons. Sprite packing defaults to a maximum page size of 2048×2048; optional `pack.json` settings retain these defaults for omitted dimensions and may explicitly override them. Sprite pages never mix source asset folders; `combineSubdirectories=true` overrides are rejected. A folder that outgrows a page can still span pages, so animation sequences are not guaranteed to remain together in that case. The current character and equipment atlases each use one 2048×2048 page; the weapons atlas uses two 2048×2048 pages, one with 160 sword frames and one with 160 bow frames. All current eight-frame animation sequences fit on one page. Character action regions are flat, such as `slash_up`; equipment regions include the item prefix, such as `basic/shot_up`. The common weapons atlas retains melee and ranged prefixes, such as `melee/basic_sword` and `ranged/basic_bow`. These generated atlases are runtime outputs, not source assets. The corrected source frames are 128×128 canvases around a centered 64×64 logical character. The accepted rendering contract retains the full canvas at pixel scale, including padding and overhang; it does not shrink or crop it. Logical size and separate feet and hurtbox bounds are authored in player data. Focused automated validation passed; live review of the initial bounds is pending. See the [composite character specification](.internal-dev/specifications/composite-characters.md) for composition and loadout behavior.

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
