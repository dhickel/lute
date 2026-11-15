# Asset service and logical keys review

## Scope

Review of the current asset service, LibGDX `AssetManager`, manifest-based asset resolution, events, and the proposal to use compound logical keys. Based on direct code inspection and subsequent design discussion; tests were not run. Recommendations are alternatives for later consideration, not accepted implementation decisions.

## Findings

### Current flow

- `assets/maps/test_tiled.manifest.json` maps logical IDs such as `OBJECTS`, `TOWN`, and `SWORD_HIT` to filenames.
- `ManifestLoader` converts these entries into `MapAssets` maps containing `Asset` records. Each `Asset` holds a LibGDX `AssetDescriptor` and a filename; `Asset.java` applies directory prefixes and Tiled loader parameters.
- `GameCore` supplies `MapAssets.all()` to `LoadingScreen`, which queues assets through `AssetService` and calls `update()` until loading finishes.
- Consumers retrieve a descriptor-bearing `Asset` and pass it to `AssetService.get()`, which delegates to `AssetManager`. Sound events currently carry `Asset.Sound`; map load events carry `MapAssets`. `Animation2D` retains an `Asset.Atlas`.
- At the manifest level there are logical names. At the manager level asset identity uses descriptor filenames. Logical name resolution is currently spread among consumers rather than provided as a direct service lookup.

### Logical keys and descriptors

- A game-facing `(asset type, logical string key)` can decouple events and components from paths and descriptor-bearing records. An event specific to sounds could carry just a sound ID; the consumer already knows the type.
- `AssetManager` does not need to be removed to expose this API. Descriptors and loader parameters can remain internal to registration and loading. Loaded assets may also be retrieved from the manager by filename and type without passing a descriptor.
- Resolving logical ID to path and then looking up the loaded asset generally introduces two lookup stages. It avoids a second resource owner, but the cost should be measured before treating it as a performance problem. In the inspected code sound lookup occurs when played, and animation atlas lookup occurs when the animation cache requires an entry, not for every sprite frame.
- An alternative is a direct `(type, logical key) → loaded resource` index populated after loading. This retains references, not duplicate underlying resources, and gives a single application-level lookup. `AssetManager` can still own loading and disposal, but the index must be invalidated when resources unload and consumers must not use disposed references.
- A direct filename-and-type manager lookup avoids the logical registry but exposes paths as identities to callers.

### Loading and ownership

- `Gdx.files.internal` provides file access, not an automatic replacement for asset loading, dependency and lifetime management. Removing `AssetManager` would require explicit handling of loading, shared resources, disposal, map dependencies, partial failures, and the custom `FixedTmxMapLoader` behavior.
- `TiledSystem` unloads a previous map asset on replacement, whereas the loading screen queues a bundle that can include atlases and audio. A complete map-bundle ownership and release policy is not apparent in the inspected flow. This is especially relevant if map switching and asset sharing expand.
- `AssetService.load()` calls `finishLoading()`, which waits for the manager's pending queue; the current loading screen instead uses `queue()` and `update()`.
- `AudioSystem.onMapLoad()` calls `playMusic(music)` before checking whether the manifest lookup returned null, so the intended descriptive missing-key error may not be reached.

## Risk Assessment

- Replacing `AssetManager` solely to avoid descriptor exposure would expand the scope to resource lifetime and dependency management without inherently solving logical ID resolution.
- A direct loaded-resource index can retain stale references after unload if ownership and invalidation are not coordinated.
- The current manifests are map-scoped; a global `(type, key)` registry needs explicit rules for collisions and map transitions. A bare ID can otherwise resolve to the wrong map's resource.
- Path-based caller keys make file moves or renames affect game code and events.

## Recommendations

1. Separate logical game-facing IDs from loading details. Consider typed or type-associated string IDs in events and service calls, resolving them internally.
2. Retain `AssetManager` initially, including the custom map loader, unless there is a concrete reason to take over its responsibilities.
3. Choose between logical-key-to-path lookup and a direct loaded-resource index based on clarity, ownership needs, and measured performance. If using a direct index, keep manager ownership explicit and invalidate index entries as part of unload.
4. Define whether keys are global or map-local and how map transitions and shared assets interact before implementing the registry. Add explicit scope only if needed.
5. Make missing-key errors occur before using the missing value. Clarify the synchronous `load()` method's queue-wide blocking behavior.
6. Keep gameplay facts and explicit sound-play requests distinct where useful; avoid adding an event translation layer without a concrete need.

## Follow-ups

- Review and test loading, unload, shared assets, map transitions, missing keys, duplicate IDs, and access after disposal before implementing a new lookup model.
- Relevant sources: `core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`, `Asset.java`, `AssetManifest.java`, `MapAssets.java`, `tiled/ManifestLoader.java`, `tiled/FixedTmxMapLoader.java`, `screen/LoadingScreen.java`, `systems/AudioSystem.java`, `systems/TiledSystem.java`, and `events/Event.java`.
