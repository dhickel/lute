package io.mindspice.lute.engine.assets;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Graphic;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.screen.LoadingScreen;
import io.mindspice.lute.engine.systems.AnimationSystem;
import io.mindspice.lute.engine.systems.AudioSystem;
import io.mindspice.lute.engine.systems.EventSystem;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public final class AssetServiceTest {
    private static boolean perType;

    private static AssetStorage newService(FileHandleResolver resolver) {
        return AssetStorage.create(perType, resolver);
    }

    private static void storageWiring(FileHandleResolver resolver) {
        var composite = AssetStorage.create(false, resolver);
        var perTypeStorage = AssetStorage.create(true, resolver);
        if (!(composite instanceof AssetStorage.Composite original)
            || original.service().getClass() != CompositeAssetService.class
            || !(perTypeStorage instanceof AssetStorage.PerType production)
            || production.service().getClass() != AssetService.class) {
            throw new AssertionError("Storage adapters must select the original composite and production per-type services");
        }
        composite.dispose();
        perTypeStorage.dispose();
    }

    public static void main(String[] args) {
        var application = Gdx.app;
        var audio = Gdx.audio;
        var files = Gdx.files;
        var graphics = Gdx.graphics;
        var gl = Gdx.gl;
        var gl20 = Gdx.gl20;
        try {
            GdxNativesLoader.load();
            var backend = new Backend();
            backend.install();
            assetValues();
            storageWiring(backend.resolver());
            for (boolean candidate : new boolean[]{false, true}) {
                perType = candidate;
                validation(backend);
                directAudioAndCompositeKeys(backend);
                queueAndConflicts(backend);
                queueOrdering(backend);
                failuresAndRetry(backend);
                nullLoaderAndMissingErrors(backend);
                atlasReloadAndMapOwnership(backend);
                partialLoadTextureFailures(backend);
                terminalDisposal(backend);
                disposalFailures(backend);
                backend.checkAllDisposed();
                System.out.println("Asset lifecycle contract passed: " + (candidate ? "production per-type" : "original composite"));
            }
            animationReload(backend);
            loadingScreen(backend);
            audioEvents(backend);
            backend.checkAllDisposed();
            System.out.println("AssetServiceTest: passed (direct loaders; no graphics context or game launch)");
        } finally {
            Gdx.app = application;
            Gdx.audio = audio;
            Gdx.files = files;
            Gdx.graphics = graphics;
            Gdx.gl = gl;
            Gdx.gl20 = gl20;
        }
    }

    private static void assetValues() {
        checkAsset(Asset.Map.of("world", "test_tiled/test_tiled.tmx"), "world", "maps/test_tiled/test_tiled.tmx", TiledMap.class);
        checkAsset(Asset.Atlas.of("sprites", "test_tiled.atlas"), "sprites", "atlases/test_tiled.atlas", TextureAtlas.class);
        checkAsset(Asset.Music.of("theme", "theme.ogg"), "theme", "audio/theme.ogg", Music.class);
        checkAsset(Asset.Sound.of("hit", "hit.wav"), "hit", "audio/hit.wav", Sound.class);
        checkAsset(new Asset.Map("world", "custom/world.tmx"), "world", "custom/world.tmx", TiledMap.class);
        checkAsset(new Asset.Atlas("sprites", "custom/sprites.atlas"), "sprites", "custom/sprites.atlas", TextureAtlas.class);
        checkAsset(new Asset.Music("theme", "custom/theme.ogg"), "theme", "custom/theme.ogg", Music.class);
        checkAsset(new Asset.Sound("hit", "custom/hit.wav"), "hit", "custom/hit.wav", Sound.class);
        check(Asset.class.isSealed(), "Asset implementations must remain closed over supported types");
    }

    private static <T> void checkAsset(Asset<T> asset, String id, String path, Class<T> type) {
        check(id.equals(asset.id()), "Asset must preserve its logical ID");
        check(path.equals(asset.path()), "Asset must expose its full resolver path");
        check(asset.type() == type, "Each asset variant must fix its resource type");
    }

    private static void validation(Backend backend) {
        var service = newService(backend.resolver());
        try {
            List<BiFunction<String, String, Asset<?>>> constructors = List.of(
                Asset.Map::new, Asset.Atlas::new, Asset.Music::new, Asset.Sound::new);
            for (var constructor : constructors) {
                for (String invalid : new String[]{null, "", " \t\n"}) {
                    rejectsArgument(() -> service.load(constructor.apply(invalid, "valid/path")), "Invalid asset ID");
                    rejectsArgument(() -> service.queue(constructor.apply("valid", invalid)), "Invalid asset path");
                }
            }
            List<BiFunction<String, String, Asset<?>>> factories = List.of(
                Asset.Map::of, Asset.Atlas::of, Asset.Music::of, Asset.Sound::of);
            for (var factory : factories) {
                for (String invalid : new String[]{null, "", " \t"}) {
                    rejectsArgument(() -> service.load(factory.apply(invalid, "valid.file")), "Invalid factory ID");
                    rejectsArgument(() -> service.queue(factory.apply("valid", invalid)), "Invalid factory file");
                }
            }
            rejectsArgument(() -> service.get(null, "valid"), "Null get type");
            rejectsArgument(() -> service.unload(null, "valid"), "Null unload type");
            rejectsArgument(() -> service.isLoaded(null, "valid"), "Null isLoaded type");
            rejectsArgument(() -> service.get(String.class, "valid"), "Unsupported get type");
            rejectsArgument(() -> service.unload(String.class, "valid"), "Unsupported unload type");
            rejectsArgument(() -> service.isLoaded(String.class, "valid"), "Unsupported isLoaded type");
            check(!service.isLoaded(Sound.class, "missing"), "Missing key is not loaded");
            rejectsRuntime(() -> service.get(Sound.class, "missing"), "Getting an unloaded key must fail");
            service.unload(Sound.class, "missing");
            check(service.update(), "Empty queue must be complete");
            service.debugDiagnostics();
        } finally {
            service.dispose();
        }
    }

    private static void directAudioAndCompositeKeys(Backend backend) {
        var service = newService(backend.resolver());
        int before = backend.audioResources.size();
        try {
            var soundAsset = Asset.Sound.of("shared", "same.audio");
            var musicAsset = Asset.Music.of("shared", "same.audio");
            Sound sound = service.load(soundAsset);
            Music music = service.load(musicAsset);
            check(backend.audioResources.size() == before + 2, "Direct loads must synchronously call both audio branches");
            check(backend.resource(sound).path.equals("assets/audio/same.audio"), "Sound must use the injected resolver");
            check(backend.resource(music).path.equals("assets/audio/same.audio"), "Music must use the injected resolver");
            check(service.get(Sound.class, "shared") == sound, "Sound lookup must preserve its runtime type and identity");
            check(service.get(Music.class, "shared") == music, "Class plus ID must distinguish music from sound");
            check(service.isLoaded(Sound.class, "shared") && service.isLoaded(Music.class, "shared"),
                "Different types with the same ID must coexist");
            check(!service.isLoaded(TextureAtlas.class, "shared"), "No cross-type lookup may report another resource");
            rejectsRuntime(() -> service.get(TextureAtlas.class, "shared"), "A different resource type is not a match");
            check(service.load(new Asset.Sound("shared", soundAsset.path())) == sound, "Repeated loads must return the same object");
            check(service.load(new Asset.Music("shared", musicAsset.path())) == music, "Repeated music loads must be idempotent");
            service.queue(soundAsset);
            service.queue(musicAsset);
            check(service.update(), "Queueing loaded resources must not schedule another load");
            check(backend.audioResources.size() == before + 2, "Idempotent operations must not allocate or retain another reference");
            rejectsArgument(() -> service.load(Asset.Sound.of("shared", "other.audio")), "Loaded path conflicts must fail");
            rejectsArgument(() -> service.queue(Asset.Music.of("shared", "other.audio")), "Loaded queued-path conflicts must fail");
            Sound alias = service.load(Asset.Sound.of("another-id", "same.audio"));
            check(alias != sound, "Different logical IDs must own independent resources even for the same path");
            service.unload(Sound.class, "shared");
            check(backend.resource(sound).disposals == 1, "One unload must dispose an idempotently loaded resource exactly once");
            check(service.get(Music.class, "shared") == music, "Unloading sound must not affect music with the same ID");
            check(service.get(Sound.class, "another-id") == alias, "Unloading one ID must not dispose another owner");
            check(!service.isLoaded(Sound.class, "shared"), "Unload must remove the key");
            rejectsRuntime(() -> service.get(Sound.class, "shared"), "Unloaded get must fail");
            service.unload(Sound.class, "shared");
            check(backend.resource(sound).disposals == 1, "Missing unload must not dispose again");
            Sound replacement = service.load(Asset.Sound.of("shared", "replacement.audio"));
            check(replacement != sound, "Unload must permit rebinding the ID to a new path");
        } finally {
            service.dispose();
        }
    }

    private static void queueAndConflicts(Backend backend) {
        var service = newService(backend.resolver());
        int before = backend.audioResources.size();
        try {
            var first = Asset.Sound.of("first", "first.wav");
            var second = Asset.Music.of("second", "second.ogg");
            service.queue(first);
            service.queue(new Asset.Sound(first.id(), first.path()));
            service.queue(second);
            check(backend.audioResources.size() == before, "Queueing must not perform loading");
            check(!service.isLoaded(Sound.class, "first"), "Queued entries are not loaded");
            service.debugDiagnostics();
            check(backend.diagnostics.contains("Sound[first] queued audio/first.wav"),
                "Diagnostics must identify queued type, logical ID, and path");
            rejectsRuntime(() -> service.get(Sound.class, "first"), "Queued get must fail");
            rejectsArgument(() -> service.queue(Asset.Sound.of("first", "conflict.wav")), "Queued path conflicts must fail");
            rejectsArgument(() -> service.load(Asset.Sound.of("first", "conflict.wav")), "Direct load must respect queued paths");
            check(!service.update(), "One update must leave the second distinct item queued");
            check(service.isLoaded(Sound.class, "first"), "Queue must process the first entry");
            service.debugDiagnostics();
            check(backend.diagnostics.contains("Sound[first] loaded audio/first.wav"),
                "Diagnostics must distinguish loaded entries from queued entries");
            check(!service.isLoaded(Music.class, "second"), "One update must not process two items");
            check(backend.audioResources.size() == before + 1, "Duplicate queued entries must collapse");
            check(service.update(), "Processing the final queued entry must return true immediately");
            check(service.isLoaded(Music.class, "second"), "Second queue entry must load on the second update");
            check(service.update(), "Completed queue must remain complete");

            var direct = Asset.Sound.of("direct", "direct.wav");
            service.queue(direct);
            Sound loaded = service.load(new Asset.Sound(direct.id(), direct.path()));
            int afterDirect = backend.audioResources.size();
            check(service.update(), "Direct load of a queued key must consume the queue entry");
            check(service.get(Sound.class, "direct") == loaded && backend.audioResources.size() == afterDirect,
                "A queued-then-direct load must not be reloaded by update");

            service.queue(Asset.Sound.of("cancel", "cancel.wav"));
            service.queue(Asset.Music.of("keep", "keep.ogg"));
            service.unload(Sound.class, "cancel");
            service.unload(Sound.class, "cancel");
            int afterCancel = backend.audioResources.size();
            check(service.update(), "Cancelling a queued entry must leave only the remaining item");
            check(backend.audioResources.size() == afterCancel + 1 && !service.isLoaded(Sound.class, "cancel"),
                "Cancelled queued resources must never reach their backend loader");
            Sound rebound = service.load(Asset.Sound.of("cancel", "new-path.wav"));
            check(rebound != null, "Cancellation must release the old path binding");
        } finally {
            service.dispose();
        }
    }

    private static void queueOrdering(Backend backend) {
        var service = newService(backend.resolver());
        try {
            service.load(Asset.Sound.of("prefix", "prefix.wav"));
            var sound = Asset.Sound.of("ordered", "ordered.wav");
            var atlas = Asset.Atlas.of("ordered", "test_tiled.atlas");
            var music = Asset.Music.of("ordered", "ordered.ogg");
            var map = tutorialMap("ordered");
            service.queue(sound);
            service.queue(atlas);
            service.queue(music);
            service.queue(map);
            service.queue(sound);
            service.unload(TextureAtlas.class, "ordered");
            service.queue(atlas); // Cancellation then requeue must move it to the tail.
            service.load(music); // Direct loading a later entry must not reorder the remainder.
            check(!service.update() && service.isLoaded(Sound.class, "ordered"), "First queued type must load first");
            check(!service.isLoaded(TiledMap.class, "ordered") && !service.isLoaded(TextureAtlas.class, "ordered"),
                "An update must not load later types");
            if (perType) {
                rejectsRuntime(service::update, "Map must reject a queued-but-unloaded atlas without loading it implicitly");
                check(!service.isLoaded(TiledMap.class, "ordered"), "Map failure must clear the failed key");
                check(service.update(), "Atlas must remain queued after map failure");
                service.load(map);
            } else {
                check(!service.update() && service.isLoaded(TiledMap.class, "ordered"), "Global queue order must cross type maps");
                check(!service.isLoaded(TextureAtlas.class, "ordered"), "Requeued atlas must remain at the tail");
                check(service.update() && service.isLoaded(TextureAtlas.class, "ordered"), "Final mixed-type item completes immediately");
            }
            service.debugDiagnostics();
            check(backend.diagnostics.lines().count() == 5,
                "Diagnostics must contain each registered entry once after cancellation/requeue");
            for (var asset : List.of(sound, atlas, music, map)) {
                check(backend.diagnostics.contains(asset.type().getSimpleName() + "[ordered] loaded " + asset.path()),
                    "Diagnostics must include loaded entries from every type map");
            }
        } finally {
            service.dispose();
        }
    }

    private static void failuresAndRetry(Backend backend) {
        var service = newService(backend.resolver());
        var failed = Asset.Sound.of("retry", "retry.wav");
        String failedPath = "assets/" + failed.path();
        backend.failAudio.add(failedPath);
        try {
            service.queue(failed);
            service.queue(Asset.Music.of("after-failure", "good.ogg"));
            rejectsRuntime(service::update, "Backend failure must propagate from update");
            check(!service.isLoaded(Sound.class, failed.id()), "Failed entry must not become loaded");
            rejectsRuntime(() -> service.get(Sound.class, failed.id()), "Failed entry must not be retrievable");
            check(service.update(), "Failed entry must be removed so the next entry can finish");
            check(service.isLoaded(Music.class, "after-failure"), "A failed entry must not block the queue");
            backend.failAudio.remove(failedPath);
            service.queue(failed);
            check(service.update() && service.isLoaded(Sound.class, failed.id()), "A failed queued key must be retryable");

            var direct = Asset.Music.of("direct-failure", "broken.ogg");
            String directPath = "assets/" + direct.path();
            backend.failAudio.add(directPath);
            try {
                rejectsRuntime(() -> service.load(direct), "Synchronous backend failure must propagate");
                check(!service.isLoaded(Music.class, direct.id()), "Failed direct load must not become loaded");
            } finally {
                backend.failAudio.remove(directPath);
            }
            Music recovered = service.load(Asset.Music.of(direct.id(), "recovered.ogg"));
            check(service.get(Music.class, direct.id()) == recovered, "Failure must not retain a conflicting path binding");

            var queuedDirect = Asset.Sound.of("queued-direct-failure", "queued-broken.wav");
            String queuedPath = "assets/" + queuedDirect.path();
            service.queue(queuedDirect);
            backend.failAudio.add(queuedPath);
            try {
                rejectsRuntime(() -> service.load(queuedDirect), "Failed direct loading of a queued key must propagate");
            } finally {
                backend.failAudio.remove(queuedPath);
            }
            check(service.update(), "Failed queued-then-direct entry must be removed, not implicitly retried");
            check(!service.isLoaded(Sound.class, queuedDirect.id()), "Only an explicit retry may reload a failed key");
            service.load(queuedDirect);
            check(service.isLoaded(Sound.class, queuedDirect.id()), "Explicit retry of queued-then-direct failure must succeed");

            var missingAtlas = new Asset.Atlas("missing-atlas", "atlases/does-not-exist.atlas");
            service.queue(missingAtlas);
            rejectsRuntime(service::update, "Actual atlas loader failures must propagate");
            check(!service.isLoaded(TextureAtlas.class, missingAtlas.id()), "Failed atlas must not be registered");
            check(service.update(), "Actual atlas failure must consume its queue entry");
        } finally {
            backend.failAudio.remove(failedPath);
            service.dispose();
        }
    }

    private static void nullLoaderAndMissingErrors(Backend backend) {
        var service = newService(backend.resolver());
        var sound = Asset.Sound.of("null-loader", "null.wav");
        String path = "assets/" + sound.path();
        backend.nullAudio.add(path);
        try {
            service.queue(sound);
            service.queue(Asset.Music.of("after-null", "next.ogg"));
            RuntimeException failure = captureRuntime(service::update, "Null loader result must fail");
            check(failure.getCause() instanceof NullPointerException, "Null must be rejected at the load boundary");
            check(failure.getMessage().contains("Sound[null-loader]") && failure.getMessage().contains(sound.path()),
                "Load errors must identify type, logical ID, and source path");
            RuntimeException missing = captureRuntime(() -> service.get(Sound.class, sound.id()), "Missing get must fail");
            check(missing instanceof IllegalStateException && missing.getMessage().contains("Asset is not loaded: Sound[null-loader]"),
                "Missing get must clearly identify the unloaded logical key");
            check(service.update() && service.isLoaded(Music.class, "after-null"), "Null result must not block subsequent work");
            backend.nullAudio.remove(path);
            service.load(sound);
            check(service.update() && service.isLoaded(Sound.class, sound.id()), "Null-loader failure must permit an explicit retry");
        } finally {
            backend.nullAudio.remove(path);
            service.dispose();
        }
    }

    private static void atlasReloadAndMapOwnership(Backend backend) {
        var service = newService(backend.resolver());
        try {
            var atlasAsset = Asset.Atlas.of("shared", "test_tiled.atlas");
            TextureAtlas first = service.load(atlasAsset);
            var region = first.findRegion("player/idle_right");
            check(region != null, "Direct atlas branch must parse the actual packed regions");
            Set<Integer> firstTextures = textureHandles(first);
            check(!firstTextures.isEmpty(), "Actual atlas load must create owned textures");
            check(service.load(atlasAsset) == first, "Repeated atlas loads must preserve identity");
            service.queue(atlasAsset);
            check(service.update(), "Queueing an already loaded atlas must be a no-op");
            rejectsArgument(() -> service.load(new Asset.Atlas(atlasAsset.id(), "different.atlas")), "Atlas paths must not rebind while loaded");
            service.unload(TextureAtlas.class, atlasAsset.id());
            backend.checkDeletedOnce(firstTextures, "Atlas unload");
            check(!service.isLoaded(TextureAtlas.class, atlasAsset.id()), "Atlas unload must clear lookup");
            rejectsRuntime(() -> service.get(TextureAtlas.class, atlasAsset.id()), "Unloaded atlas get must fail");
            TextureAtlas reloaded = service.load(atlasAsset);
            check(reloaded != first, "Reload must return a new actual atlas");
            check(reloaded.findRegion("player/idle_right") != region, "Reload must not reuse stale atlas regions");
            Set<Integer> reloadedTextures = textureHandles(reloaded);
            check(java.util.Collections.disjoint(firstTextures, reloadedTextures), "Reload must create new texture ownership");

            if (perType) {
                var houseRegion = reloaded.findRegion("house");
                reloaded.getRegions().removeValue(houseRegion, true);
                int beforeFailure = backend.nextTexture;
                RuntimeException failure = captureRuntime(() -> service.load(tutorialMap("shared")),
                    "Missing packed objects must fail without falling back to standalone source PNGs");
                check(failure.getCause().getMessage().contains("Missing object atlas region for 'house.png'"),
                    "Missing region error must identify the authored source image");
                Set<Integer> failedTextures = backend.handlesSince(beforeFailure);
                check(failedTextures.size() == 2, "Map must allocate only the two terrain textures before processing objects");
                backend.checkDeletedOnce(failedTextures, "Failed map must clean up its terrain textures");
                for (int handle : reloadedTextures) {
                    check(backend.textureDisposals.getOrDefault(handle, 0) == 0, "Failed map must not dispose borrowed atlas");
                }
                check(service.update() && !service.isLoaded(TiledMap.class, "shared"), "Failed map requires explicit retry");
                reloaded.getRegions().add(houseRegion);
                var indexedDecoy = new TextureAtlas.AtlasRegion(houseRegion);
                indexedDecoy.name = "old_tree1/old_tree1_00";
                indexedDecoy.index = 0;
                reloaded.getRegions().insert(0, indexedDecoy);
                var staticDecoy = new TextureAtlas.AtlasRegion(houseRegion);
                staticDecoy.index = 0;
                reloaded.getRegions().insert(0, staticDecoy);
            }
            int beforeMap = backend.nextTexture;
            TiledMap map = service.load(tutorialMap("shared"));
            check(service.get(TiledMap.class, "shared") == map, "Map and atlas must coexist under the same logical ID");
            check(service.get(TextureAtlas.class, "shared") == reloaded, "Map loading must not replace the atlas");
            check(map.getLayers().get("Objects") != null && map.getLayers().get("Triggers") != null,
                "Direct map branch must parse the actual map and external tilesets");
            var spawnProperties = map.getLayers().get("Triggers").getObjects().get("PlayerSpawn").getProperties();
            check(spawnProperties.get("animationType") == null,
                "Player animation type is not configured by the map");
            check(spawnProperties.get("keyFrames") == null,
                "Player spawn must not retain legacy keyFrames");
            Set<Integer> mapTextures = new HashSet<>();
            for (var tileSet : map.getTileSets()) {
                for (var tile : tileSet) {
                    Texture texture = tile.getTextureRegion().getTexture();
                    mapTextures.add(texture.getTextureObjectHandle());
                }
            }
            check(!mapTextures.isEmpty(), "Actual map loading must create tile textures");
            Set<Integer> ownedMapTextures = new HashSet<>(mapTextures);
            ownedMapTextures.removeAll(reloadedTextures);
            check(ownedMapTextures.size() == backend.nextTexture - beforeMap,
                "Every texture allocated by the actual map loader must be represented in its tilesets");
            if (perType) {
                check(mapTextures.containsAll(reloadedTextures), "Object tiles must borrow atlas textures");
                rejectsRuntime(() -> service.unload(TextureAtlas.class, "shared"), "A live map prevents atlas unload");
                check(service.get(TextureAtlas.class, "shared") == reloaded, "Rejected unload must leave owner intact");
                var tree = map.getTileSets().getTileSet("objects").getTile(2666);
                check(tree.getTextureRegion() == reloaded.findRegion("old_tree1/old_tree1", 0),
                    "Indexed TSX image must resolve to the exact packed region");
                var house = map.getTileSets().getTileSet("objects").getTile(2665);
                check(house.getTextureRegion() == reloaded.findRegion("house", -1),
                    "Static image must resolve the exact unindexed region, not the first region with that name");
                check(tree.getObjects().getCount() == 1, "Object collision shapes must survive atlas loading");
                check(map.getTileSets().getTileSet("old_tree1").getTile(2656)
                        instanceof com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile,
                    "Terrain native Tiled animations must remain animated");
            } else {
                check(java.util.Collections.disjoint(mapTextures, reloadedTextures), "Legacy baseline owns standalone textures");
            }
            for (int handle : mapTextures) {
                check(backend.textureDisposals.getOrDefault(handle, 0) == 0, "Map textures must remain alive while loaded");
            }
            service.unload(TiledMap.class, "shared");
            backend.checkDeletedOnce(ownedMapTextures, "Map unload must dispose owned textures");
            check(!service.isLoaded(TiledMap.class, "shared"), "Map unload must clear lookup");
            service.unload(TiledMap.class, "shared");
            backend.checkDeletedOnce(ownedMapTextures, "Repeated map unload");
            if (perType) {
                var house = reloaded.findRegion("house", -1);
                for (String invalid : List.of("rotation", "trimming", "dimensions")) {
                    int beforeFailure = backend.nextTexture;
                    int width = house.originalWidth;
                    int regionWidth = house.getRegionWidth();
                    if (invalid.equals("rotation")) { house.rotate = true; }
                    if (invalid.equals("trimming")) { house.originalWidth++; }
                    if (invalid.equals("dimensions")) { house.setRegionWidth(regionWidth + 1); }
                    try {
                        rejectsRuntime(() -> service.load(tutorialMap("shared")), "Unsafe packed " + invalid + " must fail");
                        backend.checkDeletedOnce(backend.handlesSince(beforeFailure), "Invalid region " + invalid + " cleanup");
                    } finally {
                        house.rotate = false;
                        house.originalWidth = width;
                        house.setRegionWidth(regionWidth);
                    }
                }
                TextureAtlas otherAtlas = service.load(Asset.Atlas.of("other-map", "test_tiled.atlas"));
                TiledMap otherMap = service.load(tutorialMap("other-map"));
                TiledMap restoredMap = service.load(tutorialMap("shared"));
                check(otherMap.getTileSets().getTile(2665).getTextureRegion() == otherAtlas.findRegion("house", -1),
                    "Each map must resolve repeated region names from its own atlas");
                check(restoredMap.getTileSets().getTile(2665).getTextureRegion() == house,
                    "A second map must not replace the first map's regions");
                service.unload(TiledMap.class, "other-map");
                service.unload(TextureAtlas.class, "other-map");
                for (int handle : reloadedTextures) {
                    check(backend.textureDisposals.getOrDefault(handle, 0) == 0, "Other map unload must not dispose this atlas");
                }
                service.unload(TiledMap.class, "shared");
            }
            for (int handle : reloadedTextures) {
                check(backend.textureDisposals.getOrDefault(handle, 0) == 0, "Unloading map must leave atlas textures alive");
            }
            service.dispose();
            backend.checkDeletedOnce(reloadedTextures, "Service dispose must dispose reloaded atlas textures");
            backend.checkDeletedOnce(ownedMapTextures, "Service dispose must not dispose previously unloaded map again");
        } finally {
            service.dispose();
        }
    }

    private static void partialLoadTextureFailures(Backend backend) {
        FileHandle directory;
        try {
            directory = new FileHandle(java.nio.file.Files.createTempDirectory("lute-partial-assets-").toFile());
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        try {
            for (boolean atlas : new boolean[]{true, false}) {
                String[] phases = atlas
                    ? new String[]{"allocation", "construction", "filter", "wrap"}
                    : new String[]{"allocation", "construction", "filter", "map-processing"};
                for (String phase : phases) {
                    partialLoadTextureFailure(backend, directory, atlas, phase);
                }
            }
            sharedMapTextureOwnership(backend, directory);
            System.out.println("Partial asset ownership: 8 failure/retry cases and shared TMX dependency passed");
        } finally {
            directory.deleteDirectory();
        }
    }

    private static void partialLoadTextureFailure(Backend backend, FileHandle directory, boolean atlas, String phase) {
        var source = new FileHandle("raw_assets/maps/test_tiled/objects/player/idle_up_02.png");
        source.copyTo(directory.child("first.png"));
        directory.child("second.png").delete();
        if (!"allocation".equals(phase)) { source.copyTo(directory.child("second.png")); }
        directory.child("partial.atlas").writeString("""
            first.png
            size: 32, 32
            format: RGBA8888
            filter: Nearest, Nearest
            first
              bounds: 0, 0, 8, 8

            second.png
            size: 32, 32
            format: RGBA8888
            filter: Nearest, Nearest
            second
              bounds: 0, 0, 8, 8
            """, false);
        writePartialMap(directory, "map-processing".equals(phase));
        Asset<?> asset = atlas
            ? new Asset.Atlas("partial-atlas", directory.child("partial.atlas").path())
            : new Asset.Map("partial-map", directory.child("partial.tmx").path());
        var service = newService(path -> new java.io.File(path).isAbsolute()
            ? new FileHandle(path) : backend.resolver().resolve(path));
        if (!atlas) { loadEmptyMapAtlas(service, directory, asset.id()); }
        String operation = (atlas ? "Atlas " : "TMX ") + phase;
        int firstHandle = backend.nextTexture;
        if (Set.of("construction", "filter", "wrap").contains(phase)) {
            backend.failTextureParameterHandle = firstHandle + 1;
            // Texture construction writes four parameters; loader filter and wrap follow.
            backend.failTextureParameterCall = switch (phase) {
                case "construction" -> 1;
                case "filter" -> 5;
                case "wrap" -> 7;
                default -> throw new AssertionError(phase);
            };
        }
        try {
            service.queue(asset);
            RuntimeException failure = captureRuntime(service::update, operation + " must fail after allocation");
            if (backend.failTextureParameterHandle != 0) {
                check(backend.textureParameterFailure != null, operation + " must reach the injected GL failure");
                check(hasCause(failure, backend.textureParameterFailure), operation + " must preserve the original cause");
            }
            if ("map-processing".equals(phase)) {
                check(failure.getCause() instanceof NumberFormatException,
                    operation + " must reach actual map property processing after texture allocation");
            }
            Set<Integer> failedHandles = backend.handlesSince(firstHandle);
            check(failedHandles.size() == ("allocation".equals(phase) ? 1 : 2),
                operation + " must fail only after the expected real texture allocations");
            check(!service.isLoaded(asset.type(), asset.id()), operation + " must not leave a loaded logical key");
            rejectsRuntime(() -> service.get(asset.type(), asset.id()), operation + " must not be retrievable");
            backend.checkDeletedOnce(failedHandles, operation + " cleanup");
            check(service.update(), operation + " must remove the failed entry, not implicitly retry");
            check(backend.handlesSince(firstHandle).equals(failedHandles), operation + " must not allocate on another update");

            backend.clearTextureFailure();
            source.copyTo(directory.child("second.png"));
            writePartialMap(directory, false);
            Object recovered = service.load(asset);
            check(service.get(asset.type(), asset.id()) == recovered, operation + " explicit retry must retain identity");
            Set<Integer> liveHandles = backend.handlesSince(firstHandle + failedHandles.size());
            check(liveHandles.size() == 2, operation + " retry must allocate both textures anew");
            for (int handle : liveHandles) {
                check(backend.textureDisposals.getOrDefault(handle, 0) == 0, operation + " retry textures must remain alive");
            }
            if (recovered instanceof TextureAtlas recoveredAtlas) {
                check(recoveredAtlas.findRegion("first") != null && recoveredAtlas.findRegion("second") != null,
                    operation + " retry must build usable regions from both pages");
                check(textureHandles(recoveredAtlas).equals(liveHandles), operation + " retry must own both textures");
                recoveredAtlas.findRegion("second").getTexture().bind();
            } else {
                TiledMap recoveredMap = (TiledMap) recovered;
                check(recoveredMap.getTileSets().getTileSet("first") != null
                        && recoveredMap.getTileSets().getTileSet("second") != null,
                    operation + " retry must build both tilesets");
                for (int id : new int[]{1, 2}) {
                    Texture texture = recoveredMap.getTileSets().getTile(id).getTextureRegion().getTexture();
                    check(liveHandles.contains(texture.getTextureObjectHandle()), operation + " retry tiles must use live textures");
                    texture.bind();
                }
            }
            service.dispose();
            backend.checkDeletedOnce(failedHandles, operation + " must not dispose failed allocations twice");
            backend.checkDeletedOnce(liveHandles, operation + " retry disposal");
        } finally {
            backend.clearTextureFailure();
            service.dispose();
        }
    }

    private static void sharedMapTextureOwnership(Backend backend, FileHandle directory) {
        writePartialMap(directory, false);
        FileHandle file = directory.child("partial.tmx");
        file.writeString(file.readString().replace("source=\"second.png\"", "source=\"first.png\""), false);
        var service = newService(path -> new java.io.File(path).isAbsolute()
            ? new FileHandle(path) : backend.resolver().resolve(path));
        loadEmptyMapAtlas(service, directory, "shared-texture");
        int firstHandle = backend.nextTexture;
        try {
            TiledMap map = service.load(new Asset.Map("shared-texture", file.path()));
            check(backend.nextTexture == firstHandle + 1, "Duplicate TMX dependencies must allocate just one owned texture");
            check(map.getTileSets().getTile(1).getTextureRegion().getTexture()
                    == map.getTileSets().getTile(2).getTextureRegion().getTexture(),
                "Tilesets referencing the same path must share the map-owned texture");
            service.dispose();
            backend.checkDeletedOnce(backend.handlesSince(firstHandle), "Shared TMX dependency disposal");
        } finally {
            service.dispose();
        }
    }

    private static Asset.Map tutorialMap(String id) {
        // Keep the original composite implementation as the standalone-texture benchmark baseline.
        return new Asset.Map(id, perType ? "maps/test_tiled/test_tiled.tmx" : "../raw_assets/maps/test_tiled/test_tiled.tmx");
    }

    private static void loadEmptyMapAtlas(AssetStorage service, FileHandle directory, String id) {
        if (!perType) { return; }
        FileHandle empty = directory.child("empty.atlas");
        empty.writeString("", false);
        service.load(new Asset.Atlas(id, empty.path()));
    }

    private static void writePartialMap(FileHandle directory, boolean invalidProperty) {
        directory.child("partial.tmx").writeString("""
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="1" height="1" tilewidth="8" tileheight="8">
              %s
              <tileset firstgid="1" name="first" tilewidth="32" tileheight="32" tilecount="1" columns="1">
                <image width="32" height="32" source="first.png"/>
              </tileset>
              <tileset firstgid="2" name="second" tilewidth="32" tileheight="32" tilecount="1" columns="1">
                <image width="32" height="32" source="second.png"/>
              </tileset>
            </map>
            """.formatted(invalidProperty
                ? "<properties><property name=\"broken\" type=\"int\" value=\"not-an-int\"/></properties>" : ""), false);
    }

    private static RuntimeException captureRuntime(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (RuntimeException expected) {
            return expected;
        }
    }

    private static boolean hasCause(Throwable failure, Throwable expected) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause == expected) { return true; }
        }
        return false;
    }

    private static void animationReload(Backend backend) {
        var service = new AssetService(backend.resolver());
        var definition = Asset.Atlas.of("OBJECTS", "test_tiled.atlas");
        var engine = new Engine();
        engine.addSystem(new AnimationSystem(service));
        var animation = new Animation2D("OBJECTS", "player", Animation2D.AnimationType.IDLE,
            Animation.PlayMode.LOOP, 1, .125f);
        var graphic = new Graphic(null, Color.WHITE);
        var entity = new Entity().add(animation).add(graphic).add(new Facing(Facing.Direction.DOWN));
        engine.addEntity(entity);
        try {
            service.load(definition);
            engine.update(0);
            var firstAnimation = animation.getAnimation();
            var firstTexture = graphic.getRegion().getTexture();
            engine.update(.01f);
            check(animation.getAnimation() == firstAnimation, "Unchanged animation must reuse the cache");
            service.unload(TextureAtlas.class, "OBJECTS");
            rejectsRuntime(() -> engine.update(0), "An unloaded atlas must not render cached disposed regions");
            service.load(definition);
            engine.update(0);
            check(animation.getAnimation() != firstAnimation, "Reload must refresh an unchanged component's cached animation");
            check(graphic.getRegion().getTexture() != firstTexture, "Reload must replace disposed atlas regions in Graphic");
            var slower = new Animation2D("OBJECTS", "player", Animation2D.AnimationType.IDLE,
                Animation.PlayMode.NORMAL, 1, .25f);
            engine.addEntity(new Entity().add(slower).add(new Graphic(null, Color.WHITE))
                .add(new Facing(Facing.Direction.DOWN)));
            engine.update(0);
            check(slower.getAnimation().getFrameDuration() == .25f
                    && animation.getAnimation().getFrameDuration() == .125f,
                "Entities must retain their own frame durations for the same atlas key");
            check(animation.getAnimation() != slower.getAnimation()
                    && animation.getAnimation().getPlayMode() == Animation.PlayMode.LOOP
                    && slower.getAnimation().getPlayMode() == Animation.PlayMode.NORMAL,
                "Entities must not share mutable play modes");
        } finally {
            engine.removeAllEntities();
            service.dispose();
        }
    }

    private static void loadingScreen(Backend backend) {
        var service = new AssetService(backend.resolver());
        int[] callbacks = {0};
        var screen = new LoadingScreen(service, () -> callbacks[0]++, List.of(
            Asset.Sound.of("screen-first", "first.wav"), Asset.Music.of("screen-second", "second.ogg")));
        try {
            int before = backend.audioResources.size();
            screen.show();
            check(backend.audioResources.size() == before, "LoadingScreen.show must queue without loading");
            screen.render(0);
            check(callbacks[0] == 0, "LoadingScreen must not transition while work remains");
            screen.render(0);
            screen.render(0);
            check(callbacks[0] == 1, "LoadingScreen must transition exactly once after the last direct load");
            var empty = new LoadingScreen(service, () -> callbacks[0]++, List.of());
            empty.show();
            empty.render(0);
            empty.render(0);
            check(callbacks[0] == 2, "An empty loading screen must also transition only once");
            String failingPath = "assets/audio/screen-failure.wav";
            var retry = new LoadingScreen(service, () -> callbacks[0]++,
                List.of(Asset.Sound.of("screen-failure", "screen-failure.wav")));
            backend.failAudio.add(failingPath);
            try {
                retry.show();
                rejectsRuntime(() -> retry.render(0), "LoadingScreen must propagate a load failure");
                check(callbacks[0] == 2, "Loading failure must not invoke the game-screen transition");
                retry.render(0);
                check(callbacks[0] == 2, "Rendering after failure must not transition without an explicit retry");
            } finally {
                backend.failAudio.remove(failingPath);
            }
            retry.show();
            retry.render(0);
            check(callbacks[0] == 3, "A failed loading screen can explicitly queue a retry");
        } finally {
            service.dispose();
        }
    }

    private static void audioEvents(Backend backend) {
        var service = new AssetService(backend.resolver());
        try {
            var field = GameCore.class.getDeclaredField("engine");
            field.setAccessible(true);
            Object previous = field.get(GameCore.INSTANCE);
            try {
                var engine = new Engine();
                field.set(GameCore.INSTANCE, engine);
                var events = new EventSystem();
                engine.addSystem(events);
                var audio = new AudioSystem(service);
                engine.addSystem(audio);
                var manifest = ManifestLoader.parseMapManifest("test_tiled");
                var weapons = io.mindspice.lute.engine.data.json.DataLoader.parseWeaponData();
                var weaponSounds = WeaponAssets.of("sword", weapons);
                weaponSounds.all().forEach(service::load);
                manifest.musicAssets().values().forEach(service::load);
                var sound = backend.resource(service.get(Sound.class, weaponSounds.attack().id()));
                events.emitEvent(new Event.Sound.Play(weaponSounds.attack().id()));
                events.emitEvent(new Event.Sound.PlayAtVolume(weaponSounds.attack().id(), 2));
                events.emitEvent(new Event.Map.Load(manifest));
                events.update(0);
                check(sound.plays == 2 && Math.abs(sound.volume - .33f) < .0001f,
                    "Sound events must resolve the equipped weapon's logical key");
                audio.setSoundVolume(.4f);
                events.emitEvent(new Event.Sound.PlayAtVolume(weaponSounds.attack().id(), .5f));
                events.update(0);
                check(Math.abs(sound.volume - .2f) < .0001f, "Key-based sound playback must preserve volume scaling");
                var music = backend.resource(service.get(Music.class, "TOWN"));
                check(music.plays == 1 && music.looping && music.volume == .5f,
                    "Map start music must resolve and play the TOWN key");
                events.emitEvent(new Event.Map.Load(manifest));
                events.update(0);
                check(music.plays == 1, "Repeated map music selection must not restart the same loaded music");
                service.unload(Music.class, "TOWN");
                audio.setMusicVolume(.7f);
                service.load(manifest.musicAssets().get("TOWN"));
                audio.setMusicVolume(.6f);
                audio.playMusic("TOWN");
                var replacement = backend.resource(service.get(Music.class, "TOWN"));
                check(replacement != music && replacement.plays == 1 && music.stops == 0 && replacement.volume == .6f,
                    "Music reload must play the replacement without touching the disposed instance");
            } finally {
                field.set(GameCore.INSTANCE, previous);
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        } finally {
            service.dispose();
        }
    }

    private static Set<Integer> textureHandles(TextureAtlas atlas) {
        Set<Integer> handles = new HashSet<>();
        for (var texture : atlas.getTextures()) {
            handles.add(texture.getTextureObjectHandle());
        }
        return handles;
    }

    private static void terminalDisposal(Backend backend) {
        var service = newService(backend.resolver());
        Sound sound = service.load(Asset.Sound.of("loaded", "loaded.wav"));
        Music music = service.load(Asset.Music.of("loaded", "loaded.ogg"));
        service.queue(Asset.Sound.of("pending", "pending.wav"));
        int before = backend.audioResources.size();
        service.dispose();
        service.dispose();
        check(backend.resource(sound).disposals == 1 && backend.resource(music).disposals == 1,
            "Terminal disposal must dispose each owned resource exactly once");
        check(backend.audioResources.size() == before, "Dispose must cancel pending work without loading it");
        check(!service.isLoaded(Sound.class, "loaded") && !service.isLoaded(Music.class, "loaded")
            && !service.isLoaded(Sound.class, "pending"), "Disposed service must report no loaded keys");
        rejectsDisposed(() -> service.load(Asset.Sound.of("new", "new.wav")));
        rejectsDisposed(() -> service.queue(Asset.Sound.of("new", "new.wav")));
        rejectsDisposed(() -> service.get(Sound.class, "loaded"));
        rejectsDisposed(() -> service.unload(Sound.class, "loaded"));
        rejectsDisposed(() -> service.unload(Sound.class, "missing"));
        rejectsDisposed(service::update);
        rejectsDisposed(service::debugDiagnostics);
        check(backend.audioResources.size() == before, "Rejected operations must not create resources");
    }

    private static void disposalFailures(Backend backend) {
        var service = newService(backend.resolver());
        Sound first = service.load(Asset.Sound.of("dispose-failure", "dispose-failure.wav"));
        Music second = service.load(Asset.Music.of("dispose-next", "dispose-next.ogg"));
        String failingPath = backend.resource(first).path;
        backend.failDisposal.add(failingPath);
        try {
            backend.failDisposal.add(backend.resource(second).path);
            RuntimeException failure = captureRuntime(service::dispose, "A disposal failure must propagate");
            check(failure.getSuppressed().length == 1, "Later disposal failures must be suppressed on the first failure");
            check(backend.resource(first).disposals == 1 && backend.resource(second).disposals == 1,
                "A failed disposal must not prevent cleanup of the other owned resources");
            check(!service.isLoaded(Sound.class, "dispose-failure"), "Disposal failure must not leave loaded entries");
            service.dispose();
            check(backend.resource(first).disposals == 1, "Terminal disposal must not retry an already disposed resource");
        } finally {
            backend.failDisposal.remove(failingPath);
            backend.failDisposal.remove(backend.resource(second).path);
            service.dispose();
        }

        var unloading = newService(backend.resolver());
        Sound resource = unloading.load(Asset.Sound.of("unload-failure", "unload-failure.wav"));
        String unloadPath = backend.resource(resource).path;
        backend.failDisposal.add(unloadPath);
        try {
            rejectsRuntime(() -> unloading.unload(Sound.class, "unload-failure"), "Unload disposal failure must propagate");
            check(!unloading.isLoaded(Sound.class, "unload-failure"), "Failed unload disposal must still remove the key");
            unloading.unload(Sound.class, "unload-failure");
            check(backend.resource(resource).disposals == 1, "Repeated unload must not retry failed disposal");
        } finally {
            backend.failDisposal.remove(unloadPath);
            unloading.dispose();
        }
    }

    private static void rejectsArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " must reject the argument");
        } catch (IllegalArgumentException | NullPointerException expected) {
            // Both standard argument-validation exception conventions are accepted.
        }
    }

    private static void rejectsRuntime(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (RuntimeException expected) {
            // The contract requires failure, not a backend-specific exception class.
        }
    }

    private static void rejectsDisposed(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Operations after dispose must reject use of the terminal service");
        } catch (IllegalStateException expected) {
            // Terminal-state errors must be distinguished from missing resources.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class AudioResource {
        private final String path;
        private int disposals;
        private int plays;
        private int stops;
        private float volume;
        private boolean looping;

        private AudioResource(String path) { this.path = path; }
    }

    static final class Backend {
        private final Map<Object, AudioResource> audioResources = new IdentityHashMap<>();
        private final Set<String> failAudio = new HashSet<>();
        private final Set<String> nullAudio = new HashSet<>();
        private final Set<String> failDisposal = new HashSet<>();
        private String diagnostics = "";
        private final Map<Integer, Integer> textureDisposals = new HashMap<>();
        private int nextTexture = 1;
        private int boundTexture;
        private final Map<Integer, Integer> textureParameterCalls = new HashMap<>();
        private int failTextureParameterHandle;
        private int failTextureParameterCall;
        private RuntimeException textureParameterFailure;

        private Set<Integer> handlesSince(int firstHandle) {
            Set<Integer> handles = new HashSet<>();
            for (int handle = firstHandle; handle < nextTexture; handle++) { handles.add(handle); }
            return handles;
        }

        private void clearTextureFailure() {
            failTextureParameterHandle = 0;
            failTextureParameterCall = 0;
            textureParameterFailure = null;
        }

        FileHandleResolver resolver() {
            return path -> new FileHandle("assets/" + path);
        }

        private AudioResource resource(Object audio) {
            var resource = audioResources.get(audio);
            check(resource != null, "Resource must come from the actual Gdx.audio branch");
            return resource;
        }

        void install() {
            Gdx.app = proxy(Application.class, (object, method, arguments) -> switch (method.getName()) {
                case "getType" -> Application.ApplicationType.Desktop;
                case "debug" -> {
                    if ("AssetService".equals(arguments[0])) { diagnostics = (String) arguments[1]; }
                    yield null;
                }
                default -> defaultValue(object, method, arguments);
            });
            Gdx.files = proxy(Files.class, (object, method, arguments) -> switch (method.getName()) {
                case "internal", "classpath", "local" -> new FileHandle("assets/" + arguments[0]);
                case "absolute" -> new FileHandle((String) arguments[0]);
                default -> defaultValue(object, method, arguments);
            });
            Gdx.graphics = proxy(Graphics.class, Backend::defaultValue);
            Gdx.gl = Gdx.gl20 = proxy(GL20.class, (object, method, arguments) -> switch (method.getName()) {
                case "glGenTexture" -> nextTexture++;
                case "glBindTexture" -> {
                    boundTexture = (Integer) arguments[1];
                    check(boundTexture == 0 || textureDisposals.getOrDefault(boundTexture, 0) == 0,
                        "Must not bind a disposed texture");
                    yield null;
                }
                case "glTexParameteri" -> {
                    int call = textureParameterCalls.merge(boundTexture, 1, Integer::sum);
                    if (boundTexture == failTextureParameterHandle && call == failTextureParameterCall) {
                        textureParameterFailure = new IllegalStateException("Injected texture parameter failure");
                        throw textureParameterFailure;
                    }
                    yield null;
                }
                case "glDeleteTexture" -> {
                    int handle = (Integer) arguments[0];
                    check(handle > 0 && handle < nextTexture, "Only allocated textures may be disposed");
                    textureDisposals.merge(handle, 1, Integer::sum);
                    yield null;
                }
                default -> defaultValue(object, method, arguments);
            });
            Gdx.audio = proxy(Audio.class, (object, method, arguments) -> switch (method.getName()) {
                case "newSound" -> newAudio(Sound.class, (FileHandle) arguments[0]);
                case "newMusic" -> newAudio(Music.class, (FileHandle) arguments[0]);
                default -> defaultValue(object, method, arguments);
            });
        }

        private <T> T newAudio(Class<T> type, FileHandle file) {
            if (nullAudio.contains(file.path())) { return null; }
            if (failAudio.contains(file.path())) {
                throw new IllegalStateException("Injected audio load failure: " + file.path());
            }
            var resource = new AudioResource(file.path());
            T audio = proxy(type, (object, method, arguments) -> {
                if ("dispose".equals(method.getName())) {
                    resource.disposals++;
                    if (failDisposal.contains(resource.path)) { throw new IllegalStateException("Injected disposal failure"); }
                    return null;
                }
                if (resource.disposals > 0 && !Set.of("hashCode", "equals", "toString").contains(method.getName())) {
                    throw new AssertionError("Access to disposed audio resource: " + method.getName());
                }
                switch (method.getName()) {
                    case "play" -> {
                        resource.plays++;
                        if (arguments != null && arguments.length > 0) { resource.volume = (Float) arguments[0]; }
                    }
                    case "stop" -> resource.stops++;
                    case "setVolume" -> resource.volume = (Float) arguments[0];
                    case "setLooping" -> resource.looping = (Boolean) arguments[0];
                }
                return defaultValue(object, method, arguments);
            });
            audioResources.put(audio, resource);
            return audio;
        }

        private void checkDeletedOnce(Set<Integer> handles, String operation) {
            for (int handle : handles) {
                check(textureDisposals.getOrDefault(handle, 0) == 1, operation + ": texture " + handle + " must be deleted once");
            }
        }

        void checkAllDisposed() {
            for (var resource : audioResources.values()) {
                check(resource.disposals == 1, "Audio resource must be disposed exactly once: " + resource.path);
            }
            for (int handle = 1; handle < nextTexture; handle++) {
                check(textureDisposals.getOrDefault(handle, 0) == 1, "Every allocated texture must be disposed exactly once: " + handle);
            }
        }

        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static Object defaultValue(Object proxy, java.lang.reflect.Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "AssetServiceTest mock " + proxy.getClass().getInterfaces()[0].getSimpleName();
                default -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) { yield false; }
                    if (type == int.class) { yield 0; }
                    if (type == long.class) { yield 0L; }
                    if (type == float.class) { yield 0f; }
                    if (type == double.class) { yield 0d; }
                    yield null;
                }
            };
        }
    }
}
