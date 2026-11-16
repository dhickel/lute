# Lute

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Runtime assets

Edit source assets in `raw_assets/`, not the generated files in `assets/`. Shared settings and the Tiled project live at the source root; `data/` and `audio/` contain shared game data and sounds. Each map directory, such as `raw_assets/test_tiled/`, contains a matching TMX and manifest, its tilesets, and an `objects/` directory with source images and `pack.json`. The manifest must name `<map>.atlas` and `<map>/<map>.tmx`, and include `music_assets` and `sound_assets` objects, even when empty.

After changing source assets, manually run from the repository root:

```sh
./raw_assets/generate-runtime-assets.sh
```

The script invokes `:util:generateRuntimeAssets`; generation is not automatic when running or building the game. It copies shared files, generates a per-map object atlas under `assets/atlases/`, and publishes maps and required terrain images under `assets/maps/`. Object image collections resolve through the atlas at runtime; terrain sheets remain separate. Object packing must preserve paths, indexes, dimensions, and orientation: do not enable rotation, whitespace stripping, path flattening, or nested pack settings.

`assets/.runtime-assets-owned` records generated file paths and hashes. Generation preserves unowned files, refuses to overwrite modified owned files or unowned collisions, and removes previously owned files no longer generated. Make intended changes in `raw_assets/`; if an owned output was edited, preserve any needed changes in the source and restore the owned output before regenerating. For an initial migration with identical existing files, `./raw_assets/generate-runtime-assets.sh -PruntimeAssetsAdoptIdentical=true` explicitly adopts matching collisions; differing files still require manual resolution. `assets/game.tiled-session` is not owned by the generator.

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
