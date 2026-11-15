package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;

import java.util.concurrent.ThreadLocalRandom;

public class AudioSystem extends EntitySystem implements EventListener {


    private final AssetService assetService;
    private Music currMusic;
    private String currMusicId;
    private float musicVolume = 0.5f;
    private float soundVolume = 0.33f;


    public AudioSystem(AssetService assetService) {
        this.assetService = assetService;
        this.currMusic = null;
        this.currMusicId = null;


        eventSubscriber.accept(Event.Map.Load.class, this::onMapLoad);
        eventSubscriber.accept(Event.Sound.Play.class, play -> this.playSound(play.soundId()));
        eventSubscriber.accept(Event.Sound.PlayAtVolume.class, pav -> this.playSoundAtVol(pav.soundId(), pav.volume()));


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

        if (currMusic != null && assetService.isLoaded(Music.class, currMusicId)
            && assetService.get(Music.class, currMusicId) == currMusic) {
            currMusic.stop();
        }

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
