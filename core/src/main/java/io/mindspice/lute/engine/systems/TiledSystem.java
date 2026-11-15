package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.PointMapObject;
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
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.Controller;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.TiledPhysics;
import io.mindspice.lute.engine.tiled.properties.PhysicObjectProps;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;

import java.util.function.Consumer;

public class TiledSystem extends EntitySystem implements EventListener, EventEmitter, Disposable {
    private final AssetService assetService;

    private final World physicsWorld;
    private TiledMap currMap;
    private MapAssets currMapAssets;
    private MapTriggers mapTriggers;
    private final Array<Body> mapBodies = new Array<>();
    private final Array<Entity> mapEntities = new Array<>();
    private final Consumer<Event.Map.Load> onMapLoad = load -> loadMap(load.mapAssets());

    public TiledSystem(AssetService assetService, World physicsWorld) {
        this.assetService = assetService;
        this.physicsWorld = physicsWorld;
    }

    @Override
    public void addedToEngine(Engine engine) {
        eventSubscriber.accept(Event.Map.Load.class, onMapLoad);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        eventUnSubscriber.accept(Event.Map.Load.class, onMapLoad);
        unloadCurrentMap(engine, false);
    }

    @Override
    public void dispose() {
        var engine = getEngine();
        if (engine == null) { return; }
        eventUnSubscriber.accept(Event.Map.Load.class, onMapLoad);
        unloadCurrentMap(engine, false);
    }

    public MapTriggers getMapTriggers() {
        return mapTriggers;
    }

    public TiledMap getCurrentMap() {
        return currMap;
    }

    public TiledMap loadMap(MapAssets assets) {
        var tiledMap = assetService.get(TiledMap.class, assets.mapAsset().id());
        setMap(tiledMap, assets);
        return tiledMap;
    }

    public void setMap(TiledMap map, MapAssets assets) {
        if (physicsWorld.isLocked()) { throw new IllegalStateException("Cannot change maps during a physics step"); }
        var parsed = MapTriggers.parse(map);
        var sensors = getEngine().getSystem(MapSensorSystem.class);
        if (sensors == null) { throw new IllegalStateException("Map loading requires MapSensorSystem"); }
        if (currMap != null) { unloadCurrentMap(getEngine(), currMap != map); }

        currMap = map;
        currMapAssets = assets;
        mapTriggers = parsed;
        try {
            sensors.setMap(parsed);
            loadMapObjects(map);
            for (var point : parsed.points()) {
                if (point.type() == MapTriggers.SpawnType.PLAYER) { spawnPlayerEntity(point.position()); }
            }
        } catch (RuntimeException | Error failure) {
            unloadCurrentMap(getEngine(), false);
            throw failure;
        }

        eventEmitter.accept(new Event.Map.Changed(map));
    }


    private void unloadCurrentMap(Engine engine, boolean unloadAsset) {
        var sensors = engine.getSystem(MapSensorSystem.class);
        if (sensors != null) { sensors.clearMap(); }
        for (var entity : mapEntities) {
            var controller = Controller.MAPPER.get(entity);
            if (controller != null) {
                GameCore.INSTANCE.getInputManager().unRegKeyListener(controller.getInputListener());
            }
            engine.removeEntity(entity);
        }
        mapEntities.clear();
        for (var body : mapBodies) { physicsWorld.destroyBody(body); }
        mapBodies.clear();
        if (unloadAsset && currMapAssets != null) {
            assetService.unload(TiledMap.class, currMapAssets.mapAsset().id());
        }
        currMap = null;
        currMapAssets = null;
        mapTriggers = null;
    }

    private void loadMapObjects(TiledMap map) {
        for (var layer : map.getLayers()) {
            switch (layer) {
                case MapLayer l when l.getName().equals("Objects") -> loadObjectLayer(layer);
                case MapLayer l when l.getName().equals("Collisions") -> loadObjectLayer(layer);
                case MapLayer l when l.getName().equals("Triggers") || l.getName().equals("Trigger") -> { }
                case TiledMapTileLayer t -> loadTileLayer(t);
                default -> Gdx.app.debug("TiledSystem", " Skipping load for layer: " + layer.getName());
            }
        }
        initMapBoundary(map);
    }

    private void spawnPlayerEntity(Vector2 pixelPosition) {
        Entity entity = getEngine()
            .getSystem(EntityFactory.class)
            .getPlayerEntity(pixelPosition, currMapAssets);
        addMapEntity(entity);
    }

    private void addMapEntity(Entity entity) {
        mapEntities.add(entity);
        getEngine().addEntity(entity);
    }

    public void initMapBoundary(TiledMap map) {
        int width = map.getProperties().get("width", 0, Integer.class);
        int tileWidth = map.getProperties().get("tilewidth", 0, Integer.class);
        int height = map.getProperties().get("height", 0, Integer.class);
        int tileHeight = map.getProperties().get("tileheight", 0, Integer.class);

        float mapW = (float) width * tileWidth * GameConsts.UNIT_SCALE;
        float mapH = (float) height * tileHeight * GameConsts.UNIT_SCALE;
        if (width <= 0 || tileWidth <= 0 || height <= 0 || tileHeight <= 0
            || !Float.isFinite(mapW) || !Float.isFinite(mapH) || mapW <= 0 || mapH <= 0) {
            throw new IllegalArgumentException("Map boundaries require positive finite map and tile dimensions");
        }
        float halfMapH = mapH * 0.5f;
        float halfMapW = mapW * 0.5f;
        float boxThickness = 0.5f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.setZero();
        bodyDef.fixedRotation = true;
        Body body = physicsWorld.createBody(bodyDef);
        body.setUserData("environment");
        mapBodies.add(body);

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
                if (cell == null || cell.getTile().getObjects().getCount() == 0) {
                    continue;
                }
                createBody(cell.getTile().getObjects(), new Vector2(x, y));

            }
        }
    }


    // create tile collisions
    private void createBody(MapObjects mapObjects, Vector2 position) {
        var bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(position);
        bodyDef.fixedRotation = true;

        var body = physicsWorld.createBody(bodyDef);
        body.setUserData("environment");
        mapBodies.add(body);

        for (var mapObject : mapObjects) {
            var fixtureDef = TiledPhysics.fixtureDefOf(mapObject, GameConsts.PHYSIC_SCALING, Vector2.Zero);
            var fixture = body.createFixture(fixtureDef);
            fixture.setUserData(mapObject.getName());
            fixtureDef.shape.dispose();
        }
    }


    private void loadObjectLayer(MapLayer layer) {
        for (var mapObj : layer.getObjects()) {
            if (mapObj instanceof TiledMapTileMapObject tmObj) {
                String objectClass = tmObj.getProperties().get("type",
                    tmObj.getTile().getProperties().get("type", "", String.class), String.class);
                var factory = getEngine().getSystem(EntityFactory.class);
                switch (objectClass) {
                    case "PhysicObject" -> {
                        var props = PhysicObjectProps.fromMapObj(tmObj);
                        addMapEntity(factory.getPhysicObjectEntity(props, tmObj, currMapAssets));
                    }
                    case "AnimatedPhysicObject" -> {
                        var props = AnimatedPhysicObjectProps.fromMapObj(tmObj);
                        addMapEntity(factory.getAnimatedPhysicObjectEntity(props, tmObj, currMapAssets));
                    }
                    default -> throw new GdxRuntimeException("Unsupported tile object class: '" + objectClass
                        + "' (object ID: " + tmObj.getProperties().get("id") + ")");
                }
            } else if (!(mapObj instanceof PointMapObject)) {
                throw new GdxRuntimeException("Unsupported Obj: " + mapObj.getClass().getSimpleName());
            }
        }

    }

}
