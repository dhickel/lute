package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class AudioSubscriptionLifecycleTest implements EventEmitter {
    private static final String NORMAL = "normal";
    private static final String QUIET = "quiet";
    private static final List<Playback> SOUND_PAIR = List.of(new Playback(NORMAL, 0.5f), new Playback(QUIET, 0.125f));

    private record Playback(String id, float volume) { }

    private final Engine engine = new Engine();
    private final List<Playback> originalCalls = new ArrayList<>();
    private final List<Playback> replacementCalls = new ArrayList<>();

    public static void main(String[] args) throws ReflectiveOperationException {
        new AudioSubscriptionLifecycleTest().run();
    }

    private void run() throws ReflectiveOperationException {
        var engineField = GameCore.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        var previousEngine = engineField.get(GameCore.INSTANCE);
        var previousApp = Gdx.app;
        var previousAudio = Gdx.audio;
        var originalAssets = new AssetService(FileHandle::new);
        var replacementAssets = new AssetService(FileHandle::new);
        var original = new AudioSystem(originalAssets);
        var replacement = new AudioSystem(replacementAssets);
        try {
            engineField.set(GameCore.INSTANCE, engine);
            Gdx.app = stub(Application.class, (proxy, method, args) -> {
                if (method.getName().equals("debug")) { return null; }
                throw new AssertionError("Unexpected Application call: " + method);
            });
            loadSounds(originalAssets, originalCalls);
            loadSounds(replacementAssets, replacementCalls);
            original.setSoundVolume(0.5f);
            replacement.setSoundVolume(0.5f);
            engine.addSystem(new EventSystem());

            engine.addSystem(original);
            emitSounds();
            engine.removeSystem(original);
            engine.removeSystem(original);
            engine.addSystem(replacement);
            drainAndExpect("teardown and replacement before first drain", List.of(), SOUND_PAIR);

            engine.removeSystem(replacement);
            engine.removeSystem(replacement);
            emitSounds();
            drainAndExpect("drained listener removed; no live audio", List.of(), List.of());

            engine.addSystem(original);
            emitSounds();
            drainAndExpect("original re-added on retained bus", SOUND_PAIR, List.of());

            engine.removeSystem(original);
            engine.removeSystem(original);
            engine.addSystem(original);
            emitSounds();
            drainAndExpect("same instance removed and re-added before drain", SOUND_PAIR, List.of());

            engine.addSystem(replacement); // Ashley removes the installed AudioSystem of the same class.
            emitSounds();
            drainAndExpect("live original replaced on retained bus", List.of(), SOUND_PAIR);
            drainAndExpect("empty drain does not replay events", List.of(), List.of());
        } finally {
            try {
                var installed = engine.getSystem(AudioSystem.class);
                if (installed != null) { engine.removeSystem(installed); }
                originalAssets.dispose();
                replacementAssets.dispose();
            } finally {
                Gdx.audio = previousAudio;
                Gdx.app = previousApp;
                engineField.set(GameCore.INSTANCE, previousEngine);
            }
        }
    }

    private void emitSounds() {
        eventEmitter.accept(new Event.Sound.Play(NORMAL));
        eventEmitter.accept(new Event.Sound.PlayAtVolume(QUIET, 0.25f));
    }

    private void drainAndExpect(String phase, List<Playback> originalExpected, List<Playback> replacementExpected) {
        engine.update(0f);
        if (!originalCalls.equals(originalExpected) || !replacementCalls.equals(replacementExpected)) {
            throw new AssertionError(phase + ": expected original=" + originalExpected + ", replacement=" + replacementExpected
                + "; got original=" + originalCalls + ", replacement=" + replacementCalls);
        }
        originalCalls.clear();
        replacementCalls.clear();
        System.out.println("PASS: " + phase);
    }

    private static void loadSounds(AssetService assets, List<Playback> calls) {
        Gdx.audio = stub(Audio.class, (proxy, method, args) -> {
            if (!method.getName().equals("newSound")) { throw new AssertionError("Unexpected Audio call: " + method); }
            var id = ((FileHandle) args[0]).name();
            return stub(Sound.class, (sound, soundMethod, soundArgs) -> switch (soundMethod.getName()) {
                case "play" -> {
                    calls.add(new Playback(id, (float) soundArgs[0]));
                    yield 1L;
                }
                case "dispose" -> null;
                default -> throw new AssertionError("Unexpected Sound call: " + soundMethod);
            });
        });
        assets.load(new Asset.Sound(NORMAL, NORMAL));
        assets.load(new Asset.Sound(QUIET, QUIET));
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
