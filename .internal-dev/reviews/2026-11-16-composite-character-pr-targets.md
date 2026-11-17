# Composite character PR targets review

## Scope

Code-only inventory of the complete PR diff against `origin/master` at `024d3d5`, through baseline `77e43d2` and the staged and unstaged loadout ownership follow-up. Current PR Targets policy includes substantive code, directly related tests and build configuration; it excludes game assets, non-code files and pure moves. Earlier asset inventory is retained in Git history, not repeated here. Equipment source migration and supersampled assets are summarized in the PR description.

## Findings

The patch implements shared atlas generation, composite rendering, loadouts, independent attack slots, logical canvas geometry and distinct physical and damage fixtures. The current unfiltered validation retains the pre-existing issue #6 failure; the run excluding that task passes. Bow primary is animation-only. Accessory sources are deliberately skipped and remain unequippable.

## Risk Assessment

- Real-GL checks cover both current weapon pages and native world-frame comparisons, but other drivers and live visual acceptance remain unverified.
- Both asset trees are ignored and require external provisioning; historical Git objects remain. Switching to an older branch that tracks these paths can overwrite local files.
- Repeat generation and ownership checks reduce source/output drift; later edits still require regeneration.
- Player and weapon schema/API consumers must migrate. Projectile execution remains deferred.

## Recommendations

- Review the full patch and the current validation evidence before any merge.
- Retain the known input failure visibly rather than presenting the filtered run as an all-green suite.

## Follow-ups

- Publish the authorized PR update after root review; do not merge as part of publication.
- Resolve issue #6 separately and rerun the unfiltered check. Review visual bounds without implying user approval has already occurred.

## Targets

- `.gitignore`
  - Ignores the complete local source and runtime asset trees.
- `generate-runtime-assets.sh` (new; replaces `raw_assets/generate-runtime-assets.sh`, deleted from the index while its local ignored copy remains)
  - Resolves the repository from the script location and forwards arguments to Gradle.
  - Fails with an actionable message when the raw tree is missing.
  - Creates the runtime directory before Gradle asset-list generation.

- `core/src/main/java/io/mindspice/lute/engine/GameCore.java`
  - Binds primary and secondary attack keys.
  - Queues shared character atlases and per-attack weapon sounds.
- `core/src/main/java/io/mindspice/lute/engine/assets/CharacterAssets.java` (new)
  - Defines the shared character, equipment and weapon atlas assets.
  - Builds the atlas load list using the configured weapon atlas.
- `core/src/main/java/io/mindspice/lute/engine/assets/WeaponAssets.java`
  - Builds distinct primary and secondary attack and hit sound assets for each weapon.
  - Collects sound assets for loading.
- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`
  - Tracks active attack action and its animation settings.
  - Exposes direction-specific atlas region keys, animation time and frame index.
  - Stores required frame counts for attack validation.
- `core/src/main/java/io/mindspice/lute/engine/components/Combat.java`
  - Tracks pending primary or secondary requests, the active attack snapshot, sensor and hits.
  - Removes weapon selection and the weapon-taking constructor from combat state.
- `core/src/main/java/io/mindspice/lute/engine/components/CombatWeapon.java`
  - Represents weapons with separate primary and secondary attacks.
  - Carries per-attack damage, animation, sound, collision and geometry.
- `core/src/main/java/io/mindspice/lute/engine/components/CompositeCharacter.java` (new)
  - Marks entities for composite-character rendering.
  - Stores native canvas dimensions separately from the logical transform.
- `core/src/main/java/io/mindspice/lute/engine/components/Controller.java`
  - Tracks input state for primary and secondary attacks.
- `core/src/main/java/io/mindspice/lute/engine/components/Hurtbox.java` (new)
  - Tags the persistent fixture used for explicit damage contacts.
- `core/src/main/java/io/mindspice/lute/engine/components/Loadout.java` (new)
  - Owns equipment and selected weapon without storing Combat, retaining an immutable catalogue and equipment validator.
  - Validates component selections; null selects unarmed and unknown IDs throw without mutation.
  - Exposes weapon and ID accessors; setters do not guard attack state.
- `core/src/main/java/io/mindspice/lute/engine/data/json/AnimationData.java`
  - Validates animation settings and supports the flat atlas key.
- `core/src/main/java/io/mindspice/lute/engine/data/json/CharacterBounds.java` (new)
  - Represents bottom-left logical bounds and validates their dimensions and placement.
- `core/src/main/java/io/mindspice/lute/engine/data/json/PlayerData.java`
  - Loads and validates player equipment and optional weapon.
  - Requires a flat character atlas prefix.
  - Validates logical size, feet collision bounds and damage hitbox bounds.
- `core/src/main/java/io/mindspice/lute/engine/data/json/WeaponsData.java`
  - Loads common atlas and separate primary and secondary attack definitions.
  - Validates weapon IDs, unarmed and melee-jab constraints, action and collision compatibility.
  - Validates attack fields and relative sound paths.
- `core/src/main/java/io/mindspice/lute/engine/input/InputAction.java`
  - Splits attack input into primary and secondary actions.
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
  - Provides the asset service to the render system for composition.
  - Installs LoadoutSystem for guarded gameplay switches.
- `core/src/main/java/io/mindspice/lute/engine/systems/AnimationSystem.java`
  - Selects and caches animations by active action and direction.
  - Validates required attack frame counts and character frame dimensions.
- `core/src/main/java/io/mindspice/lute/engine/systems/CharacterCompositor.java` (new)
  - Composes visible character and equipment layers into offscreen framebuffer cells, ordered by facing.
  - Reads equipment and weapon from Loadout without requiring Combat.
  - Places hands behind head in all directions while preserving weapon placement.
  - Batches layer draws by rank and texture page and manages multiple targets.
  - Validates indexed untrimmed frames, refreshes atlas caches and restores graphics state.
  - Culls transformed characters against the camera and disposes resources.
  - Preserves variable native canvas size, transparent gutters and transformed overhang during culling.
- `core/src/main/java/io/mindspice/lute/engine/systems/CharacterFixtures.java` (new)
  - Converts logical bounds to world units for Box2D fixtures.
  - Creates the non-sensor feet ellipse and separately tagged damage sensor.
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`
  - Requires Loadout, selects primary or secondary attacks from its weapon and configures attack animation.
  - Cleans active attack state and any sensor when Loadout is removed, before or after sensor activation.
  - Uses active attack damage, geometry and hit timing for melee.
  - Does not create melee collision for projectile attacks.
  - Routes explicit damage contacts to tagged hurtboxes, retaining generic target fallback.
- `core/src/main/java/io/mindspice/lute/engine/systems/ControllerSystem.java`
  - Routes attack key presses to the requested primary or secondary combat slot.
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
  - Builds separate player Combat and Loadout components, the composite marker and equipment selection from data.
  - Passes the existing weapon catalogue and loaded-equipment validator to Loadout.
  - Builds per-weapon attack definitions and sounds.
  - Validates weapon geometry, required character frames and equipment atlas layers.
  - Uses logical dimensions for the transform and creates separate feet and hurtbox fixtures.
- `core/src/main/java/io/mindspice/lute/engine/systems/LoadoutSystem.java` (new)
  - Coordinates equipment and weapon switches without an update loop.
  - Rejects pending or active combat before selection validation or mutation; allows entities without Combat.
- `core/src/main/java/io/mindspice/lute/engine/systems/RenderState.java` (new)
  - Captures and restores graphics bindings, viewport, blending, masks and related GL state.
  - Handles state associated with released framebuffer and shader resources.
- `core/src/main/java/io/mindspice/lute/engine/systems/RenderSystem.java`
  - Composes characters before rendering and draws their resulting regions in the world.
  - Sorts world rendering by depth while preserving sprite appearance.
  - Manages compositor lifecycle.
  - Centers the full native canvas on the logical transform without shrinking it.
- `core/src/test/java/io/mindspice/lute/engine/assets/AssetServiceTest.java`
  - Exercises asset loading and weapon sound identities for the shared atlas and attack slots.
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`
  - Exercises both attack slots for sword, bow and unarmed, pending and active switch locks, and unchanged rejected state.
  - Covers passive changes without Combat, invalid initial and idle IDs, loaded-equipment validation and Loadout-removal cleanup.
  - Exercises combat collision behavior.
  - Covers attack timing, geometry and relevant rejection cases.
  - Exercises explicit hurtboxes, solid-fixture exclusion and generic fallback.
- `core/src/test/java/io/mindspice/lute/engine/data/json/DataLoaderTest.java`
  - Exercises player loadout and weapon attack data loading.
  - Covers invalid weapon, animation, collision and sound inputs.
  - Checks logical sizes and collision and damage bounds at the data boundary.
- `core/src/test/java/io/mindspice/lute/engine/systems/MapSensorSystemTest.java`
  - Updates combat fixtures with separate Combat and Loadout components for the weapon and attack data model.
  - Covers map-trigger behavior with separate feet and hurtbox fixtures.
- `lwjgl3/build.gradle`
  - Adds desktop composite graphics JavaExec test execution configuration.
  - Permits zero discovered JUnit tests for the JavaExec-only desktop test sources.
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeGameTest.java` (new)
  - Exercises composite characters in a desktop game context using generated assets and verifies GameScreen installs LoadoutSystem.
  - Switches through the system and verifies pending and active locks; death and world-frame render fixtures omit Combat.
  - Compares actual composition and native-scale world frames across weapon pages.
  - Exercises native canvas positioning.
  - Independently checks opaque helmet pixels at actual glove overlaps.
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeRenderTest.java` (new)
  - Exercises offscreen composite pixel output, layer order and animation with Combat-free render fixtures and loadout switches.
  - Covers rendering state, visibility, batching and resource behavior.
  - Covers variable canvas sizes, gutters, transformed overhang and culling.
  - Checks independent pairwise layer priorities and exact rank-dependent batching.
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
  - Stages pack inputs in sorted path order for reproducible atlas generation.
  - Generates shared character, equipment and weapon atlases with multipage output.
  - Validates required sprite inputs, transparent frames, region names and dimensions.
  - Integrates sprite output with staged ownership and existing map asset generation.
  - Combines set-first equipment exports by slot and logs skipped accessories while rejecting other unknown slots.
  - Defaults sprites to 2048 maximum dimensions, retaining explicit and partial configuration overrides.
  - Rejects sprite folder mixing while retaining combined map-object packing and shared preservation guards.
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`
  - Tests sprite atlas regions, transparent pixels, prefixes and multipage publication.
  - Checks collision, missing input, ownership failure and stale-page cleanup without corrupting outputs.
  - Checks multiple equipment sets, invalid slots, skipped accessories and source preservation.
  - Checks folder-isolated pages, overflow, stale-page shrink and sprite/map mode rejection.
  - Checks 2048 defaults and partial settings inheritance.
