package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAsset;

import java.util.function.Consumer;

public class TiledService {
    private final AssetService assetService;

    private Consumer<TiledMap> mapChangeConsumer;
    private Consumer<TiledMapTileMapObject> loadObjectConsumer;

    private TiledMap currMap;


    public TiledService(AssetService assetService) {
        this.assetService = assetService;
        this.mapChangeConsumer = null;
        this.loadObjectConsumer = null;
        this.currMap = null;
    }


    public TiledMap loadMap(MapAsset map) {
        var tiledMap = assetService.load(map);
        tiledMap.getProperties().put("mapAsset", tiledMap);
        return tiledMap;
    }

    public void setMap(TiledMap map) {
        if (currMap != null) {
            assetService.unload(currMap.getProperties().get("mapAsset", MapAsset.class));
        }
        currMap = map;
        loadMapObjects(map);
        if (mapChangeConsumer != null) {
            mapChangeConsumer.accept(map);
        }
    }

    private void loadMapObjects(TiledMap map) {
        for (var layer : map.getLayers()) {
            if (layer.getName().equals("Objects")) { loadObjectLayer(layer); }
        }

    }

    private void loadObjectLayer(MapLayer layer) {
        if (loadObjectConsumer == null) { return; }

        for (var mapObj : layer.getObjects()) {
            if (mapObj instanceof TiledMapTileMapObject tmObj) {
                loadObjectConsumer.accept(tmObj);
            } else { throw new GdxRuntimeException("Unsupported Obj: " + mapObj.getClass().getSimpleName()); }
        }

    }

    public void setMapChangeConsumer(Consumer<TiledMap> mapChangeConsumer) {
        this.mapChangeConsumer = mapChangeConsumer;
    }

    public void setLoadObjectConsumer(Consumer<TiledMapTileMapObject> loadObjectConsumer) {
        this.loadObjectConsumer = loadObjectConsumer;
    }
}
