package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.utils.Disposable;

/** Test/benchmark adapters only. Both implementations have the same extra dispatch hop. */
interface AssetStorage extends Disposable {
    <T> T load(Asset<T> asset);
    void queue(Asset<?> asset);
    <T> T get(Class<T> type, String id);
    boolean isLoaded(Class<?> type, String id);
    void unload(Class<?> type, String id);
    boolean update();
    void debugDiagnostics();

    static AssetStorage create(boolean perType, FileHandleResolver resolver) {
        return perType ? new PerType(new PerTypeAssetService(resolver)) : new Composite(new AssetService(resolver));
    }

    record Composite(AssetService service) implements AssetStorage {
        public <T> T load(Asset<T> asset) { return service.load(asset); }
        public void queue(Asset<?> asset) { service.queue(asset); }
        public <T> T get(Class<T> type, String id) { return service.get(type, id); }
        public boolean isLoaded(Class<?> type, String id) { return service.isLoaded(type, id); }
        public void unload(Class<?> type, String id) { service.unload(type, id); }
        public boolean update() { return service.update(); }
        public void debugDiagnostics() { service.debugDiagnostics(); }
        public void dispose() { service.dispose(); }
    }

    record PerType(PerTypeAssetService service) implements AssetStorage {
        public <T> T load(Asset<T> asset) { return service.load(asset); }
        public void queue(Asset<?> asset) { service.queue(asset); }
        public <T> T get(Class<T> type, String id) { return service.get(type, id); }
        public boolean isLoaded(Class<?> type, String id) { return service.isLoaded(type, id); }
        public void unload(Class<?> type, String id) { service.unload(type, id); }
        public boolean update() { return service.update(); }
        public void debugDiagnostics() { service.debugDiagnostics(); }
        public void dispose() { service.dispose(); }
    }
}
