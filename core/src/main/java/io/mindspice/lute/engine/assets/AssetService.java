package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.FixedTmxMapLoader;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class AssetService implements Disposable {
    private static final class Entry<T> {
        final Asset<T> definition;
        T value;
        Entry(Asset<T> definition) { this.definition = definition; }
    }

    private final FileHandleResolver resolver;
    private final Map<String, Entry<Sound>> sounds = new HashMap<>();
    private final Map<String, Entry<Music>> music = new HashMap<>();
    private final Map<String, Entry<TextureAtlas>> atlases = new HashMap<>();
    private final Map<String, Entry<TiledMap>> maps = new HashMap<>();
    private final LinkedHashSet<Entry<?>> queue = new LinkedHashSet<>();
    private boolean disposed;

    public AssetService(FileHandleResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "Asset resolver must not be null");
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, Entry<T>> entriesFor(Class<T> type) {
        Map<?, ?> selected = switch (type) {
            case Class<?> assetType when assetType == Sound.class -> sounds;
            case Class<?> assetType when assetType == Music.class -> music;
            case Class<?> assetType when assetType == TextureAtlas.class -> atlases;
            case Class<?> assetType when assetType == TiledMap.class -> maps;
            case null, default -> throw new IllegalArgumentException("Unsupported asset type: " + type);
        };
        return (Map<String, Entry<T>>) selected;
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Asset logical ID must not be null or blank");
        }
    }

    public <T> T load(Asset<T> asset) {
        queue(asset);
        var entry = entriesFor(asset.type()).get(asset.id());
        if (entry.value == null) { loadEntry(entry); }
        return get(asset.type(), asset.id());
    }

    public void queue(Asset<?> asset) {
        requireOpen();
        if (asset == null) { throw new IllegalArgumentException("Asset definition must not be null"); }
        queueChecked(asset);
    }

    private <T> void queueChecked(Asset<T> asset) {
        var table = entriesFor(asset.type());
        var existing = table.get(asset.id());
        if (existing != null) {
            if (!existing.definition.path().equals(asset.path())) {
                throw new IllegalArgumentException("Conflicting asset definition for " + key(asset) + ": '"
                    + existing.definition.path() + "' versus '" + asset.path() + "'");
            }
            return;
        }
        var entry = new Entry<>(asset);
        table.put(asset.id(), entry);
        queue.add(entry);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type, String id) {
        requireOpen();
        var table = entriesFor(type);
        validateId(id);
        Entry<?> entry = table.get(id);
        if (entry == null || entry.value == null) {
            throw new IllegalStateException("Asset is not loaded: " + type.getSimpleName() + "[" + id + "]");
        }
        return (T) entry.value;
    }

    public boolean isLoaded(Class<?> type, String id) {
        var table = entriesFor(type);
        validateId(id);
        var entry = table.get(id);
        return entry != null && entry.value != null;
    }

    public void unload(Class<?> type, String id) {
        requireOpen();
        var table = entriesFor(type);
        validateId(id);
        var entry = table.remove(id);
        if (entry == null) { return; }
        queue.remove(entry);
        if (entry.value != null) { ((Disposable) entry.value).dispose(); }
    }

    public boolean update() {
        requireOpen();
        if (!queue.isEmpty()) { loadEntry(queue.getFirst()); }
        return queue.isEmpty();
    }

    private <T> void loadEntry(Entry<T> entry) {
        Object value = null;
        var asset = entry.definition;
        try {
            value = Objects.requireNonNull(loadDirect(asset), "Loader returned null");
            entry.value = asset.type().cast(value);
            queue.remove(entry);
        } catch (RuntimeException failure) {
            entriesFor(asset.type()).remove(asset.id());
            queue.remove(entry);
            if (value instanceof Disposable resource) {
                try { resource.dispose(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw new GdxRuntimeException("Failed to load " + key(asset) + " from '" + asset.path() + "'", failure);
        }
    }

    private Object loadDirect(Asset<?> asset) {
        return switch (asset) {
            case Asset.Sound sound -> Gdx.audio.newSound(resolver.resolve(sound.path()));
            case Asset.Music track -> Gdx.audio.newMusic(resolver.resolve(track.path()));
            case Asset.Atlas atlas -> DirectAtlasLoader.load(resolver.resolve(atlas.path()));
            case Asset.Map map -> {
                var parameters = new BaseTiledMapLoader.Parameters();
                parameters.projectFilePath = GameConsts.PROJECT_PATH;
                yield new FixedTmxMapLoader(resolver).load(map.path(), parameters);
            }
        };
    }

    public void debugDiagnostics() {
        requireOpen();
        var diagnostics = new StringBuilder();
        allEntries().forEach(entry -> diagnostics.append(key(entry.definition)).append(" ")
            .append(entry.value == null ? "queued" : "loaded").append(" ")
            .append(entry.definition.path()).append('\n'));
        Gdx.app.debug("AssetService", diagnostics.toString());
    }

    private Stream<Entry<?>> allEntries() {
        return Stream.of(sounds, music, atlases, maps).flatMap(table -> table.values().stream());
    }

    private static String key(Asset<?> asset) { return asset.type().getSimpleName() + "[" + asset.id() + "]"; }

    private void requireOpen() {
        if (disposed) { throw new IllegalStateException("AssetService is disposed"); }
    }

    @Override public void dispose() {
        if (disposed) { return; }
        disposed = true;
        var resources = allEntries().map(entry -> entry.value).filter(Objects::nonNull).toList();
        sounds.clear();
        music.clear();
        atlases.clear();
        maps.clear();
        queue.clear();
        RuntimeException failure = null;
        for (Object resource : resources) {
            try { ((Disposable) resource).dispose(); }
            catch (RuntimeException exception) {
                if (failure == null) { failure = exception; }
                else { failure.addSuppressed(exception); }
            }
        }
        if (failure != null) { throw failure; }
    }
}
