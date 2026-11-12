package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.assets.tiled.TiledAshleyLoader;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.concurrent.ThreadLocalRandom;

public class TiledSystem extends EntitySystem implements EventListener, EventEmitter {
    private final AssetService assetService;
    private final TiledAshleyLoader loader;
    private final World physicsWorld;
    private TiledMap currMap;
    private MapAssets currMapAssets;

    public TiledSystem(AssetService assetService, TiledAshleyLoader loader, World physicsWorld) {
        this.assetService = assetService;
        this.loader = loader;
        this.physicsWorld = physicsWorld;

        eventSubscriber.accept(Event.Map.Load.class, (load) -> this.loadMap(load.mapAssets()));
    }


    public TiledMap loadMap(MapAssets assets) {
        var tiledMap = assetService.get(assets.mapAsset());
        setMap(tiledMap, assets);
        return tiledMap;
    }

    public void setMap(TiledMap map, MapAssets assets) {
        if (currMap != null) { unloadCurrentMap(currMap != map); }

        currMap = map;
        currMapAssets = assets;
        loadMapObjects(map);

        eventEmitter.accept(new Event.Map.Changed(map));


        // Play selected music asset from tiled map properties, fallback to random.
        String mapMusic = map.getProperties().get("music", "RANDOM", String.class);
        if (mapMusic.equals("RANDOM")) {
            currMapAssets.musicAssets().values().stream()
                .skip(ThreadLocalRandom.current().nextInt(currMapAssets.musicAssets().size()))
                .findFirst()
                .ifPresent(ma -> eventEmitter.accept(new Event.Music.Changed(ma)));
        } else {
            Asset.Music music = currMapAssets.musicAssets().get(mapMusic);
            if (music == null) {
                throw new GdxRuntimeException("Failed to find music asset for key: " + mapMusic);
            }
        }
    }


    private void unloadCurrentMap(boolean unloadAsset) {
        if (unloadAsset) { assetService.unload(currMapAssets.mapAsset()); }
        Array<Body> bodies = new Array<>();
        physicsWorld.getBodies(bodies);
        for (Body body : bodies) {
            if ("environment".equals(body.getUserData())) { physicsWorld.destroyBody(body); }
        }
    }

    private void loadMapObjects(TiledMap map) {
        for (var layer : map.getLayers()) {
            switch (layer) {
                case MapLayer l when l.getName().equals("Objects") -> loadObjectLayer(layer);
                case MapLayer l when l.getName().equals("Collisions") -> loadObjectLayer(layer);
                case TiledMapTileLayer t -> loadTileLayer(t);
                default -> { Gdx.app.debug("TiledSystem", " Skipping load for layer: " + layer.getName()); }
            }
        }
        initMapBoundary(map);
    }

    public void initMapBoundary(TiledMap map) {
        int width = map.getProperties().get("width", 0, Integer.class);
        int tileWidth = map.getProperties().get("tilewidth", 0, Integer.class);
        int height = map.getProperties().get("height", 0, Integer.class);
        int tileHeight = map.getProperties().get("tileheight", 0, Integer.class);

        float mapW = width * tileWidth * GameConsts.UNIT_SCALE;
        float mapH = height * tileHeight * GameConsts.UNIT_SCALE;
        float halfMapH = mapH * 0.5f;
        float halfMapW = mapW * 0.5f;
        float boxThickness = 0.5f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.setZero();
        bodyDef.fixedRotation = true;
        Body body = physicsWorld.createBody(bodyDef);
        body.setUserData("environment");

        // Left Edge
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(boxThickness, halfMapH, new Vector2(-boxThickness, halfMapH), 0f);
        body.createFixture(shape, 0f).setFriction(0f);
        shape.dispose();

        // Right Edge
        shape = new PolygonShape();
        shape.setAsBox(boxThickness, halfMapH, new Vector2(mapW + boxThickness, halfMapH), 0f);
        body.createFixture(shape, 0f).setFriction(0f);
        shape.dispose();

        // Bottom Edge
        shape = new PolygonShape();
        shape.setAsBox(halfMapW, boxThickness, new Vector2(halfMapW, -boxThickness), 0f);
        body.createFixture(shape, 0f).setFriction(0f);
        shape.dispose();

        // Top Edge
        shape = new PolygonShape();
        shape.setAsBox(halfMapW, boxThickness, new Vector2(halfMapW, mapH + boxThickness), 0f);
        body.createFixture(shape, 0f).setFriction(0f);
        shape.dispose();
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
                loader.loadObject(tmObj, currMapAssets);
            } else { throw new GdxRuntimeException("Unsupported Obj: " + mapObj.getClass().getSimpleName()); }
        }

    }

}
