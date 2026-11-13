package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;

import java.util.concurrent.ThreadLocalRandom;

public class AudioSystem extends EntitySystem implements EventListener {


    private final AssetService assetService;
    private Music currMusic;
    private MapAssets currMapAssets;
    private Asset.Music currMusicAsset;
    private float musicVolume = 0.5f;
    private float soundVolume = 0.33f;


    public AudioSystem(AssetService assetService) {
        this.assetService = assetService;
        this.currMusic = null;
        this.currMusicAsset = null;
        this.currMapAssets = null;


        eventSubscriber.accept(Event.Map.Load.class, this::onMapLoad);

        eventSubscriber.accept(Event.Sound.Play.class, play -> this.playSound(play.sound()));
        eventSubscriber.accept(Event.Sound.PlayAtVolume.class, pav -> this.playSoundAtVol(pav.sound(), pav.volume()));

        eventSubscriber.accept(Event.Player.Moved.class, _ -> this.playSound(currMapAssets.soundAssets().get("SWORD_HIT")));

    }


    public void setMusicVolume(float musicVolume) {
        this.musicVolume = MathUtils.clamp(musicVolume, 0f, 1f);
        if (currMusic != null) {
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
        this.currMapAssets = load.mapAssets();

        String mapMusic = currMapAssets.startMusic();
        if (mapMusic.equals("RANDOM")) {
            currMapAssets.musicAssets().values().stream()
                .skip(ThreadLocalRandom.current().nextInt(currMapAssets.musicAssets().size()))
                .findFirst()
                .ifPresent(this::playMusic);
        } else {
            Asset.Music music = currMapAssets.musicAssets().get(mapMusic);
            playMusic(music);
            if (music == null) {
                throw new GdxRuntimeException("Failed to find music asset for key: " + mapMusic);
            }
        }
    }

    public void playMusic(Asset.Music musicAsset) {
        if (currMusicAsset == musicAsset) { return; }

        if (this.currMusic != null) {
            currMusic.stop();
        }

        currMusic = assetService.get(musicAsset);
        currMusicAsset = musicAsset;

        currMusic.setLooping(true);
        currMusic.setVolume(musicVolume);
        currMusic.play();
    }

    public void playSound(Asset.Sound soundAsset) {
        assetService.get(soundAsset).play(soundVolume);
    }

    public void playSoundAtVol(Asset.Sound soundAsset, float volume) {
        float scaledVolume = soundVolume * Math.clamp(volume, 0f, 1f);
        assetService.get(soundAsset).play(scaledVolume);
    }
}
