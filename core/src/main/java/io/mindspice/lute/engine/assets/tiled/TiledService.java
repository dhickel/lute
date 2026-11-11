package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAsset;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;

import java.util.function.Consumer;

public class TiledService implements EventEmitter {
    private final AssetService assetService;

    private Consumer<TiledMapTileMapObject> loadObjectConsumer;
    private LoadTileConsumer loadTileConsumer;
    private TiledMap currMap;


    public TiledService(AssetService assetService) {
        this.assetService = assetService;
        this.loadObjectConsumer = null;
        this.currMap = null;
        this.loadTileConsumer = null;
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
//        if (mapChangeConsumer != null) {
//            mapChangeConsumer.accept(map);
//        }
        eventEmitter.accept(Event.Map.Loaded.of(map));
    }

    private void loadMapObjects(TiledMap map) {
        for (var layer : map.getLayers()) {
            switch (layer) {
                case MapLayer l when l.getName().equals("Objects") -> loadObjectLayer(layer);
                //  case MapLayer l when l.getName().equals("Collisions") -> loadObjectLayer(layer);
                case TiledMapTileLayer t -> loadTileLayer(t);
                default -> {// DO nothing}
                }
            }
        }

//
//            if (layer.getName().equals("Objects")) {
//              loadObjectLayer(layer);
//            } else if (layer instanceof TiledMapTileLayer tileLayer) {
//                loadTileLayer(tileLayer);
//            }
//        }

    }

    private void loadTileLayer(TiledMapTileLayer tileLayer) {
        if (loadTileConsumer == null) { return; }

        for (int y = 0; y < tileLayer.getHeight(); y++) {
            for (int x = 0; x < tileLayer.getWidth(); x++) {
                TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                if (cell == null) { continue; }
                loadTileConsumer.accept(cell.getTile(), x, y);
            }
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



    public void setLoadObjectConsumer(Consumer<TiledMapTileMapObject> loadObjectConsumer) {
        this.loadObjectConsumer = loadObjectConsumer;
    }


    public void setLoadTileConsumer(LoadTileConsumer loadTileConsumer) {
        this.loadTileConsumer = loadTileConsumer;
    }

    @FunctionalInterface
    public interface LoadTileConsumer {
        void accept(TiledMapTile tile, float x, float y);
    }
}
