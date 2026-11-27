package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class AudioSystem extends EntitySystem implements EventListener {


    private final AssetService assetService;
    private Music currMusic;
    private String currMusicId;
    private float musicVolume = 0.5f;
    private float soundVolume = 0.33f;
    private final Consumer<Event.Sound.Play> playSound = event -> playSound(event.soundId());
    private final Consumer<Event.Sound.PlayAtVolume> playAtVolume = event -> playSoundAtVol(event.soundId(), event.volume());

    public AudioSystem(AssetService assetService) {
        this.assetService = assetService;
    }

    @Override
    public void addedToEngine(Engine engine) {
        eventSubscriber.accept(Event.Sound.Play.class, playSound);
        eventSubscriber.accept(Event.Sound.PlayAtVolume.class, playAtVolume);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        eventUnSubscriber.accept(Event.Sound.Play.class, playSound);
        eventUnSubscriber.accept(Event.Sound.PlayAtVolume.class, playAtVolume);
        stopMusic();
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

    public static void validateMapMusic(MapAssets map) {
        String id = map.startMusic();
        if (id == null || (!id.isBlank() && (id.equals("RANDOM") ? map.musicAssets().isEmpty() : !map.musicAssets().containsKey(id)))) {
            throw new GdxRuntimeException("Invalid start_music for map '" + map.mapAsset().id() + "': " + id);
        }
    }

    public void setMap(MapAssets map) {
        if (map == null || map.startMusic().isBlank()) {
            stopMusic();
        } else if ("RANDOM".equals(map.startMusic())) {
            map.musicAssets().keySet().stream()
                .skip(ThreadLocalRandom.current().nextInt(map.musicAssets().size()))
                .findFirst()
                .ifPresent(this::playMusic);
        } else {
            playMusic(map.startMusic());
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

    private void stopMusic() {
        if (currMusic != null && assetService.isLoaded(Music.class, currMusicId)
            && assetService.get(Music.class, currMusicId) == currMusic) {
            currMusic.stop();
        }
        currMusic = null;
        currMusicId = null;
    }

    public void playSound(String soundId) {
        assetService.get(Sound.class, soundId).play(soundVolume);
    }

    public void playSoundAtVol(String soundId, float volume) {
        float scaledVolume = soundVolume * Math.clamp(volume, 0f, 1f);
        assetService.get(Sound.class, soundId).play(scaledVolume);
    }
}
