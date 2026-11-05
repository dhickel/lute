package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

public enum MapAsset implements Asset<TiledMap> {

    MAIN("test_tiled.tmx");

    private final AssetDescriptor<TiledMap> descriptor;


    MapAsset(String mapName) {
        BaseTiledMapLoader.Parameters params = new BaseTiledMapLoader.Parameters();
        params.projectFilePath = "game.tiled-project";
        this.descriptor = new AssetDescriptor<>("maps/" + mapName, TiledMap.class, params);
    }


    @Override
    public AssetDescriptor<TiledMap> getDescriptor() {
        return this.descriptor;
    }

}
