package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class AudioSystem extends EntitySystem implements EventListener, Disposable {


    private final AssetService assetService;
    private final Consumer<Event.Map.Load> mapLoadConsumer = this::onMapLoad;
    private final Consumer<Event.Sound.Play> soundConsumer = play -> playSound(play.soundId());
    private final Consumer<Event.Sound.PlayAtVolume> volumeConsumer = play -> playSoundAtVol(play.soundId(), play.volume());
    private EventSystem events;
    private boolean disposed;
    private Music currMusic;
    private String currMusicId;
    private float musicVolume = 0.5f;
    private float soundVolume = 0.33f;


    public AudioSystem(AssetService assetService) {
        this.assetService = assetService;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        attachEvents(engine.getSystem(EventSystem.class));
    }

    @Override
    public void removedFromEngine(Engine engine) {
        detachEvents(events);
        stopMusic();
        super.removedFromEngine(engine);
    }

    void attachEvents(EventSystem events) {
        if (disposed || events == this.events) { return; }
        detachEvents(this.events);
        this.events = events;
        if (events == null) { return; }
        subscribe(events, Event.Map.Load.class, mapLoadConsumer);
        subscribe(events, Event.Sound.Play.class, soundConsumer);
        subscribe(events, Event.Sound.PlayAtVolume.class, volumeConsumer);
    }

    void detachEvents(EventSystem events) {
        if (events == null || events != this.events) { return; }
        unsubscribeImmediately(events, Event.Map.Load.class, mapLoadConsumer);
        unsubscribeImmediately(events, Event.Sound.Play.class, soundConsumer);
        unsubscribeImmediately(events, Event.Sound.PlayAtVolume.class, volumeConsumer);
        this.events = null;
    }

    @Override
    public void dispose() {
        if (disposed) { return; }
        disposed = true;
        detachEvents(events);
        stopMusic();
    }

    private void stopMusic() {
        if (currMusic != null && assetService.isLoaded(Music.class, currMusicId)
            && assetService.get(Music.class, currMusicId) == currMusic) {
            currMusic.stop();
        }
        currMusic = null;
        currMusicId = null;
    }

    public void setMusicVolume(float musicVolume) {
        this.musicVolume = MathUtils.clamp(musicVolume, 0f, 1f);
        if (currMusicId != null && assetService.isLoaded(Music.class, currMusicId)
            && assetService.get(Music.class, currMusicId) == currMusic) {
            currMusic.setVolume(this.musicVolume);
        }
    }

    public void setSoundVolume(float soundVolume) {
        this.soundVolume = MathUtils.clamp(soundVolume, 0f, 1f);
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    private void onMapLoad(Event.Map.Load load) {
        var mapAssets = load.mapAssets();
        String mapMusic = mapAssets.startMusic();
        if (mapMusic.isBlank()) { return; }

        if ("RANDOM".equals(mapMusic)) {
            if (mapAssets.musicAssets().isEmpty()) {
                throw new GdxRuntimeException("No music assets available for RANDOM selection");
            }
            mapAssets.musicAssets().values().stream()
                .skip(ThreadLocalRandom.current().nextInt(mapAssets.musicAssets().size()))
                .findFirst()
                .ifPresent(music -> playMusic(music.id()));
        } else {
            var music = mapAssets.musicAssets().get(mapMusic);
            if (music == null) {
                throw new GdxRuntimeException("Failed to find music asset for key: " + mapMusic);
            }
            playMusic(music.id());
        }
    }

    public void playMusic(String musicId) {
        Music music = assetService.get(Music.class, musicId);
        if (music == currMusic && musicId.equals(currMusicId)) { return; }

        stopMusic();
        currMusic = music;
        currMusicId = musicId;

        currMusic.setLooping(true);
        currMusic.setVolume(musicVolume);
        currMusic.play();
    }

    public void playSound(String soundId) {
        assetService.get(Sound.class, soundId).play(soundVolume);
    }

    public void playSoundAtVol(String soundId, float volume) {
        float scaledVolume = soundVolume * Math.clamp(volume, 0f, 1f);
        assetService.get(Sound.class, soundId).play(scaledVolume);
    }
}
