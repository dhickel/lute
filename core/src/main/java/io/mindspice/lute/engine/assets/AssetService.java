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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AssetService implements Disposable {
    private record Key(Class<?> type, String id) {
        private Key {
            if (type != Sound.class && type != Music.class && type != TextureAtlas.class && type != TiledMap.class) {
                throw new IllegalArgumentException("Unsupported asset type: " + type);
            }
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Asset logical ID must not be null or blank");
            }
        }

        @Override public String toString() { return type.getSimpleName() + "[" + id + "]"; }
    }

    private record Entry(Asset<?> definition, Object value) { }

    private final FileHandleResolver resolver;
    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private boolean disposed;

    public AssetService(FileHandleResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "Asset resolver must not be null");
    }

    public <T> T load(Asset<T> asset) {
        queue(asset);
        var key = new Key(asset.type(), asset.id());
        Entry entry = entries.get(key);
        if (entry.value() == null) {
            loadEntry(key, entry);
        }
        return get(asset.type(), asset.id());
    }

    public void queue(Asset<?> asset) {
        requireOpen();
        if (asset == null) { throw new IllegalArgumentException("Asset definition must not be null"); }
        var key = new Key(asset.type(), asset.id());
        Entry existing = entries.get(key);
        if (existing != null && !existing.definition().path().equals(asset.path())) {
            throw new IllegalArgumentException("Conflicting asset definition for " + key + ": '"
                + existing.definition().path() + "' versus '" + asset.path() + "'");
        }
        entries.putIfAbsent(key, new Entry(asset, null));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type, String id) {
        requireOpen();
        var key = new Key(type, id);
        Entry entry = entries.get(key);
        if (entry == null || entry.value() == null) {
            throw new IllegalStateException("Asset is not loaded: " + key);
        }
        // Every stored value is validated with its key's Class at the load boundary.
        return (T) entry.value();
    }

    public boolean isLoaded(Class<?> type, String id) {
        Entry entry = entries.get(new Key(type, id));
        return entry != null && entry.value() != null;
    }

    public void unload(Class<?> type, String id) {
        requireOpen();
        Entry entry = entries.remove(new Key(type, id));
        if (entry != null && entry.value() != null) {
            ((Disposable) entry.value()).dispose();
        }
    }

    public boolean update() {
        requireOpen();
        for (var item : entries.entrySet()) {
            if (item.getValue().value() == null) {
                loadEntry(item.getKey(), item.getValue());
                break;
            }
        }
        return entries.values().stream().noneMatch(entry -> entry.value() == null);
    }

    private void loadEntry(Key key, Entry entry) {
        Object value = null;
        try {
            value = loadDirect(entry.definition());
            Object checked = key.type().cast(Objects.requireNonNull(value, "Loader returned null"));
            entries.put(key, new Entry(entry.definition(), checked));
        } catch (RuntimeException failure) {
            entries.remove(key);
            if (value instanceof Disposable resource) {
                try { resource.dispose(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw new GdxRuntimeException("Failed to load " + key + " from '" + entry.definition().path() + "'", failure);
        }
    }

    private Object loadDirect(Asset<?> asset) {
        return switch (asset) {
            case Asset.Sound sound -> Gdx.audio.newSound(resolver.resolve(sound.path()));
            case Asset.Music music -> Gdx.audio.newMusic(resolver.resolve(music.path()));
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
        StringBuilder diagnostics = new StringBuilder();
        entries.forEach((key, entry) -> diagnostics.append(key).append(" ")
            .append(entry.value() == null ? "queued" : "loaded").append(" ")
            .append(entry.definition().path()).append('\n'));
        Gdx.app.debug("AssetService", diagnostics.toString());
    }

    private void requireOpen() {
        if (disposed) { throw new IllegalStateException("AssetService is disposed"); }
    }

    @Override
    public void dispose() {
        if (disposed) { return; }
        disposed = true;
        var resources = entries.values().stream().map(Entry::value).filter(Objects::nonNull).toList();
        entries.clear();
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
