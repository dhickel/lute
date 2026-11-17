package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.events.Event;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Standalone lifecycle checks with fake audio assets and the real Ashley/event systems. */
public final class AudioSystemTest {
    private static final MapAssets MAP = new MapAssets(new Asset.Map("map", "map.tmx"), "music",
        new Asset.Atlas("map", "map.atlas"), Map.of("music", new Asset.Music("music", "music.ogg")), Map.of());
    private static int checks;

    public static void main(String[] args) throws Exception {
        var previousApp = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (proxy, method, values) -> null);
        try {
            constructionDoesNotSubscribe();
            for (boolean pending : new boolean[]{true, false}) {
                removeAndReattach(pending);
                reattachBeforeBoundary(pending);
                replaceAudio(pending);
                replaceBus(pending);
                for (boolean busFirst : new boolean[]{true, false}) {
                    replaceBoth(pending, busFirst);
                    removeBoth(pending, busFirst);
                }
                disposeViaScreenContract(pending);
            }
            audioBeforeBusAndRepeatedBusReuse();
            hideShowReuse();
            removalDuringDispatch(false);
            removalDuringDispatch(true);
            reattachmentDuringDispatch(false);
            reattachmentDuringDispatch(true);
            busReplacementDuringDispatch();
            disposalDuringDispatch();
            retirementDuringFailingDispatch();
            queuedMutationDuringDispatch();
            exactConsumerIdentity();
            unloadedMusicIsNotTouched();
            oldDisposalDoesNotStopReplacementMusic();
            System.out.println("AudioSystemTest: " + checks + " checks passed (fake audio; real Ashley/event lifecycle)");
        } finally {
            Gdx.app = previousApp;
        }
    }

    private static void constructionDoesNotSubscribe() {
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        audio.dispose();
        audio.dispose();
        check(assets.musicReads == 0 && assets.soundReads == 0, "Construction/disposal needs no global engine or asset access");
    }

    private static void removeAndReattach(boolean pending) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(events);
        engine.addSystem(audio);
        if (!pending) { cycle(events, assets); }
        engine.removeSystem(audio);
        engine.removeSystem(audio);
        queuedRemoval(events);
        silent(events, assets);
        empty(events);
        for (int i = 0; i < 3; i++) {
            engine.addSystem(audio);
            engine.addSystem(audio);
            cycle(events, assets);
            engine.removeSystem(audio);
            queuedRemoval(events);
            events.update(0);
            empty(events);
        }
        check(assets.musicStops == assets.musicPlays, "Removal stops owned music once and permits reattachment");
    }

    private static void reattachBeforeBoundary(boolean pending) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(events);
        engine.addSystem(audio);
        if (!pending) { cycle(events, assets); }
        for (int i = 0; i < 3; i++) {
            engine.removeSystem(audio);
            queuedRemoval(events);
            engine.addSystem(audio);
        }
        cycle(events, assets);
        cycle(events, assets);
        engine.removeAllSystems();
        empty(events);
    }

    private static void replaceAudio(boolean pending) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var oldAssets = new FakeAssets();
        var oldAudio = new AudioSystem(oldAssets);
        var liveAssets = new FakeAssets();
        engine.addSystem(events);
        engine.addSystem(oldAudio);
        if (!pending) { cycle(events, oldAssets); }
        engine.addSystem(new AudioSystem(liveAssets));
        oldAudio.dispose();
        int oldReads = oldAssets.totalReads();
        cycle(events, liveAssets);
        cycle(events, liveAssets);
        check(oldAssets.totalReads() == oldReads, "Replacement leaves only live audio handling all three event types");
        engine.removeAllSystems();
        empty(events);
    }

    private static void replaceBus(boolean pending) throws Exception {
        var engine = new Engine();
        var oldEvents = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(oldEvents);
        engine.addSystem(audio);
        if (!pending) { cycle(oldEvents, assets); }
        var liveEvents = new EventSystem();
        engine.addSystem(liveEvents);
        empty(oldEvents);
        cycle(liveEvents, assets);
        silent(oldEvents, assets);
        check(assets.musicStops == 0 && assets.musicPlays == 1,
            "Replacing only the event bus preserves retained audio playback");
        audio.dispose();
        queuedRemoval(liveEvents);
        silent(liveEvents, assets);
        empty(liveEvents);
        engine.removeAllSystems();
    }

    private static void replaceBoth(boolean pending, boolean busFirst) throws Exception {
        var engine = new Engine();
        var oldEvents = new EventSystem();
        var oldAssets = new FakeAssets();
        var oldAudio = new AudioSystem(oldAssets);
        engine.addSystem(oldEvents);
        engine.addSystem(oldAudio);
        if (!pending) { cycle(oldEvents, oldAssets); }
        var liveEvents = new EventSystem();
        var liveAssets = new FakeAssets();
        var liveAudio = new AudioSystem(liveAssets);
        if (busFirst) {
            engine.addSystem(liveEvents);
            empty(oldEvents);
            engine.addSystem(liveAudio);
        } else {
            engine.addSystem(liveAudio);
            engine.addSystem(liveEvents);
            empty(oldEvents);
        }
        oldAudio.dispose();
        int oldReads = oldAssets.totalReads();
        cycle(liveEvents, liveAssets);
        silent(oldEvents, oldAssets);
        check(oldAssets.totalReads() == oldReads, "Either replacement order detaches the original owning bus");
        engine.removeAllSystems();
        empty(liveEvents);
    }

    private static void removeBoth(boolean pending, boolean busFirst) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(events);
        engine.addSystem(audio);
        if (!pending) { cycle(events, assets); }
        if (busFirst) {
            engine.removeSystem(events);
            empty(events);
            engine.removeSystem(audio);
        } else {
            engine.removeSystem(audio);
            queuedRemoval(events);
            engine.removeSystem(events);
        }
        audio.dispose();
        empty(events);
        silent(events, assets);
    }

    private static void disposeViaScreenContract(boolean pending) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(events);
        engine.addSystem(audio);
        if (!pending) { cycle(events, assets); }
        // GameScreen.dispose() disposes Disposable systems without removing them.
        for (var system : engine.getSystems()) {
            if (system instanceof Disposable disposable) { disposable.dispose(); }
        }
        audio.dispose();
        queuedRemoval(events);
        silent(events, assets);
        empty(events);
        engine.removeSystem(events);
        var replacement = new EventSystem();
        engine.addSystem(replacement);
        engine.addSystem(audio);
        empty(replacement);
        silent(replacement, assets);
        engine.removeAllSystems();
    }

    private static void audioBeforeBusAndRepeatedBusReuse() throws Exception {
        var engine = new Engine();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(audio);
        var events = new EventSystem();
        for (int i = 0; i < 3; i++) {
            engine.addSystem(events);
            cycle(events, assets);
            events.emitEvent(new Event.Sound.Play("sound"));
            engine.removeSystem(events);
            engine.removeSystem(events);
            empty(events);
        }
        engine.addSystem(events);
        cycle(events, assets);
        engine.removeAllSystems();
        empty(events);
    }

    private static void hideShowReuse() {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        engine.addSystem(events);
        engine.addSystem(new AudioSystem(assets));
        cycle(events, assets);
        for (int i = 0; i < 3; i++) {
            // Hiding clears entities, not systems; subsequent show emits Map.Load again.
            engine.removeAllEntities();
            cycle(events, assets);
        }
        check(assets.musicStops == 0 && assets.musicPlays == 1, "Hide/show reuse retains audio and does not restart the same music");
        engine.removeAllSystems();
    }

    private static void removalDuringDispatch(boolean replace) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var oldAssets = new FakeAssets();
        var liveAssets = new FakeAssets();
        var oldAudio = new AudioSystem(oldAssets);
        engine.addSystem(events);
        boolean[] removed = {false};
        events.subscribe(Event.Sound.Play.class, event -> {
            if (removed[0]) { return; }
            removed[0] = true;
            engine.removeSystem(oldAudio);
            if (replace) { engine.addSystem(new AudioSystem(liveAssets)); }
        });
        engine.addSystem(oldAudio);
        events.emitEvent(new Event.Sound.Play("sound"));
        events.emitEvent(new Event.Sound.PlayAtVolume("sound", 0.5f));
        events.update(0);
        check(oldAssets.totalReads() == 0 && liveAssets.totalReads() == 0,
            "Detached audio callbacks cannot touch assets in the same event or later queued events");
        if (replace) { cycle(events, liveAssets); }
        else { silent(events, oldAssets); }
        engine.removeAllSystems();
        check(events.subscriptions.isEmpty(), "Dispatch replacement leaves no pending audio registration after removal");
    }

    private static void reattachmentDuringDispatch(boolean reattachBus) throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        boolean[] reattached = {false};
        engine.addSystem(events);
        events.subscribe(Event.Sound.Play.class, event -> {
            if (reattached[0]) { return; }
            reattached[0] = true;
            if (reattachBus) {
                engine.removeSystem(events);
                engine.addSystem(events);
            } else {
                engine.removeSystem(audio);
                engine.addSystem(audio);
            }
        });
        engine.addSystem(audio);
        events.emitEvent(new Event.Sound.Play("sound"));
        events.emitEvent(new Event.Map.Load(MAP));
        events.emitEvent(new Event.Sound.PlayAtVolume("sound", 0.5f));
        events.update(0);
        check(assets.musicReads == 1 && assets.soundReads == 2,
            "Same-instance reattachment can reuse its still-active callbacks once in the running batch");
        check(listenerCount(events) == 4 && events.subscriptions.isEmpty() && events.unSubscriptions.isEmpty(),
            "Dispatch-end removal precedes reattachment registration without cancellation or duplicate consumers");
        cycle(events, assets);
        engine.removeAllSystems();
        check(listenerCount(events) == 1 && events.subscriptions.isEmpty() && events.unSubscriptions.isEmpty(),
            "Final retirement releases reattached audio without removing the unrelated listener");
    }

    private static void busReplacementDuringDispatch() throws Exception {
        var engine = new Engine();
        var oldEvents = new EventSystem();
        var liveEvents = new EventSystem();
        var assets = new FakeAssets();
        engine.addSystem(oldEvents);
        oldEvents.subscribe(Event.Sound.Play.class, event -> engine.addSystem(liveEvents));
        engine.addSystem(new AudioSystem(assets));
        oldEvents.emitEvent(new Event.Sound.Play("sound"));
        oldEvents.emitEvent(new Event.Sound.PlayAtVolume("sound", 0.5f));
        oldEvents.update(0);
        check(assets.totalReads() == 0, "Bus retirement stops current and later queued callbacks before audio reattaches elsewhere");
        check(listenerCount(oldEvents) == 1 && oldEvents.subscriptions.isEmpty() && oldEvents.unSubscriptions.isEmpty(),
            "Retired bus removes all audio-owned registrations at dispatch end without another update");
        cycle(liveEvents, assets);
        engine.removeAllSystems();
        empty(liveEvents);
    }

    private static void disposalDuringDispatch() throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var audio = new AudioSystem(assets);
        engine.addSystem(events);
        events.subscribe(Event.Sound.Play.class, event -> {
            audio.dispose();
            assets.loaded = false;
        });
        engine.addSystem(audio);
        events.emitEvent(new Event.Sound.Play("sound"));
        events.emitEvent(new Event.Map.Load(MAP));
        events.emitEvent(new Event.Sound.PlayAtVolume("sound", 0.5f));
        events.update(0);
        check(assets.totalReads() == 0, "Disposal guards all three callbacks before queued removal, even after assets unload");
        check(listenerCount(events) == 1 && events.subscriptions.isEmpty() && events.unSubscriptions.isEmpty(),
            "Dispatch-end boundary removes disposed audio registrations while retaining unrelated listeners");
        engine.removeAllSystems();
    }

    private static void retirementDuringFailingDispatch() throws Exception {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var failure = new IllegalStateException("Injected callback failure");
        engine.addSystem(events);
        events.subscribe(Event.Sound.Play.class, event -> {
            engine.removeSystem(events);
            throw failure;
        });
        engine.addSystem(new AudioSystem(assets));
        events.emitEvent(new Event.Sound.Play("sound"));
        try {
            events.update(0);
            throw new AssertionError("Callback exception must propagate");
        } catch (IllegalStateException actual) {
            check(actual == failure, "Event dispatch preserves callback failure");
        }
        check(assets.totalReads() == 0 && listenerCount(events) == 1
                && events.subscriptions.isEmpty() && events.unSubscriptions.isEmpty(),
            "Retired bus removes audio-owned registrations even when its last dispatch throws, with no later update");
        engine.removeAllSystems();
    }

    private static void queuedMutationDuringDispatch() throws Exception {
        var events = new EventSystem();
        var oldListener = new EqualConsumer();
        var newListener = new EqualConsumer();
        boolean[] changed = {false};
        events.subscribe(Event.Player.Moved.class, event -> {
            if (changed[0]) { return; }
            changed[0] = true;
            events.unsubscribe(Event.Player.Moved.class, oldListener);
            events.subscribe(Event.Player.Moved.class, newListener);
            check(events.subscriptions.size() == 1 && events.unSubscriptions.size() == 1,
                "Callback mutations remain queued during listener iteration");
        });
        events.subscribe(Event.Player.Moved.class, oldListener);
        events.emitEvent(new Event.Player.Moved());
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(oldListener.calls == 2 && newListener.calls == 0,
            "Ordinary queued mutations do not change listeners anywhere in the running dispatch batch");
        check(events.subscriptions.isEmpty() && events.unSubscriptions.isEmpty() && listenerCount(events) == 2,
            "Queued mutations apply once at the dispatch-end boundary");
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(oldListener.calls == 2 && newListener.calls == 1,
            "The next dispatch sees exactly the new listener set");
    }

    private static void exactConsumerIdentity() {
        var events = new EventSystem();
        var first = new EqualConsumer();
        var second = new EqualConsumer();
        events.subscribe(Event.Player.Moved.class, first);
        events.subscribe(Event.Player.Moved.class, second);
        events.update(0);
        events.unsubscribe(Event.Player.Moved.class, first);
        events.update(0);
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(first.calls == 0 && second.calls == 1, "Deferred teardown uses exact consumer identity, not equals");
        events.subscribe(Event.Player.Moved.class, first);
        events.unsubscribe(Event.Player.Moved.class, first);
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(first.calls == 0 && second.calls == 2, "Cancellation of pending registration uses exact consumer identity");
        events.subscribe(Event.Player.Moved.class, first);
        events.update(0);
        events.unsubscribe(Event.Player.Moved.class, second);
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(first.calls == 1 && second.calls == 2, "Active teardown uses exact consumer identity, not equals");
        events.unsubscribe(Event.Player.Moved.class, first);
        events.subscribe(Event.Player.Moved.class, first);
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(first.calls == 2, "Re-registration survives earlier queued removal without duplicating the active consumer");
        events.unsubscribe(Event.Player.Moved.class, first);
        events.subscribe(Event.Player.Moved.class, first);
        events.unsubscribe(Event.Player.Moved.class, first);
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
        check(first.calls == 2, "A later queued removal cancels the intervening re-registration");
    }

    private static void unloadedMusicIsNotTouched() {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        engine.addSystem(events);
        engine.addSystem(new AudioSystem(assets));
        cycle(events, assets);
        assets.loaded = false;
        engine.removeAllSystems();
        check(assets.musicStops == 0, "Cleanup never stops music already unloaded by its asset owner");
    }

    private static void oldDisposalDoesNotStopReplacementMusic() {
        var engine = new Engine();
        var events = new EventSystem();
        var assets = new FakeAssets();
        var oldAudio = new AudioSystem(assets);
        engine.addSystem(events);
        engine.addSystem(oldAudio);
        cycle(events, assets);
        engine.addSystem(new AudioSystem(assets));
        cycle(events, assets);
        oldAudio.dispose();
        oldAudio.dispose();
        check(assets.musicStops == 1 && assets.musicPlays == 2,
            "Late disposal of replaced audio does not stop live playback of the same shared music asset");
        cycle(events, assets);
        engine.removeAllSystems();
    }

    private static void cycle(EventSystem events, FakeAssets assets) {
        int musicReads = assets.musicReads;
        int soundReads = assets.soundReads;
        emit(events);
        check(assets.musicReads == musicReads + 1, "Live audio handles Map.Load exactly once");
        check(assets.soundReads == soundReads + 2, "Live audio handles Play and PlayAtVolume exactly once each");
        int end = assets.volumes.size();
        check(Math.abs(assets.volumes.get(end - 2) - 0.33f) < 0.0001f
                && Math.abs(assets.volumes.get(end - 1) - 0.165f) < 0.0001f,
            "Live consumers preserve normal and scaled sound volumes");
    }

    private static void silent(EventSystem events, FakeAssets assets) {
        int reads = assets.totalReads();
        emit(events);
        check(assets.totalReads() == reads, "Detached audio handles no events");
    }

    private static void emit(EventSystem events) {
        events.emitEvent(new Event.Map.Load(MAP));
        events.emitEvent(new Event.Sound.Play("sound"));
        events.emitEvent(new Event.Sound.PlayAtVolume("sound", 0.5f));
        events.emitEvent(new Event.Player.Moved());
        events.update(0);
    }

    private static int listenerCount(EventSystem events) throws Exception {
        var field = EventSystem.class.getDeclaredField("listeners");
        field.setAccessible(true);
        var listeners = (Map<?, ?>) field.get(events);
        return listeners.values().stream().mapToInt(value -> ((List<?>) value).size()).sum();
    }

    private static void queuedRemoval(EventSystem events) {
        check(events.subscriptions.isEmpty() && events.unSubscriptions.size() >= 3
                && events.unSubscriptions.size() % 3 == 0,
            "Active-bus teardown queues exact-consumer removals and cancels earlier pending registrations");
    }

    private static void empty(EventSystem events) throws Exception {
        check(listenerCount(events) == 0 && events.subscriptions.isEmpty() && events.unSubscriptions.isEmpty(),
            "Owning bus holds no active or queued consumers after a safe update or retirement boundary");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class EqualConsumer implements Consumer<Event.Player.Moved> {
        int calls;
        @Override public void accept(Event.Player.Moved event) { calls++; }
        @Override public boolean equals(Object other) { return other instanceof EqualConsumer; }
        @Override public int hashCode() { return 1; }
    }

    private static final class FakeAssets extends AssetService {
        int musicReads;
        int soundReads;
        int musicPlays;
        int musicStops;
        boolean loaded = true;
        final List<Float> volumes = new ArrayList<>();
        final Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class},
            (proxy, method, args) -> {
                if (method.getName().equals("play")) { musicPlays++; }
                if (method.getName().equals("stop")) { musicStops++; }
                return null;
            });
        final Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("play")) { throw new AssertionError("Unexpected sound call: " + method); }
                volumes.add((Float) args[0]);
                return 1L;
            });

        FakeAssets() { super(null); }

        @Override public <T> T get(Class<T> type, String id) {
            if (!loaded) { throw new AssertionError("Audio touched an unloaded asset"); }
            if (type == Music.class) { musicReads++; return type.cast(music); }
            if (type == Sound.class) { soundReads++; return type.cast(sound); }
            throw new AssertionError("Unexpected asset type: " + type);
        }

        @Override public boolean isLoaded(Class<?> type, String id) { return loaded; }
        int totalReads() { return musicReads + soundReads; }
    }
}
