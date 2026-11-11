package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAsset;
import io.mindspice.lute.engine.assets.tiled.TiledAshleyLoader;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.events.EventListener;

public class TiledSystem extends EntitySystem implements EventListener, EventEmitter {
    private final AssetService assetService;
    private final TiledAshleyLoader loader;
    private TiledMap currMap;

    public TiledSystem(AssetService assetService, TiledAshleyLoader loader) {
        this.assetService = assetService;
        this.loader = loader;

        eventSubscriber.accept(Event.Map.Load.class, (load) -> this.loadMap(load.mapAsset()));
    }


    public TiledMap loadMap(MapAsset map) {
        var tiledMap = assetService.load(map);
        tiledMap.getProperties().put("mapAsset", tiledMap);
        setMap(tiledMap);
        return tiledMap;
    }

    public void setMap(TiledMap map) {
        if (currMap != null) {
            assetService.unload(currMap.getProperties().get("mapAsset", MapAsset.class));
        }
        currMap = map;
        loadMapObjects(map);
         eventEmitter.accept(new Event.Map.Changed(map));
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
    }

    private void loadTileLayer(TiledMapTileLayer tileLayer) {
        for (int y = 0; y < tileLayer.getHeight(); y++) {
            for (int x = 0; x < tileLayer.getWidth(); x++) {
                TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                if (cell == null) { continue; }
                loader.loadTile(cell.getTile(), x, y);
            }
        }
    }

    private void loadObjectLayer(MapLayer layer) {
        for (var mapObj : layer.getObjects()) {
            if (mapObj instanceof TiledMapTileMapObject tmObj) {
                loader.loadObject(tmObj);
            } else { throw new GdxRuntimeException("Unsupported Obj: " + mapObj.getClass().getSimpleName()); }
        }

    }

}
