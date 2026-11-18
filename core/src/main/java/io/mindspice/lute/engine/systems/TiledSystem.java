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
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.engine.tiled.TiledPhysics;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;
import io.mindspice.lute.engine.tiled.properties.PhysicObjectProps;

import java.util.ArrayList;
import java.util.List;

public class TiledSystem extends EntitySystem implements EventEmitter {
    private final AssetService assetService;
    private final World physicsWorld;
    private final InputManager inputManager;
    private RuntimeMap active;
    // These outlive a map runtime (including hide/show), but not this gameplay session.
    private Entry entry;
    private PlayerState playerState;
    private Travel pendingTravel;

    public TiledSystem(AssetService assetService, World physicsWorld, InputManager inputManager) {
        this.assetService = assetService;
        this.physicsWorld = physicsWorld;
        this.inputManager = inputManager;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        hideMap(engine);
        entry = null;
        playerState = null;
    }

    public MapTriggers getMapTriggers() {
        return active == null ? null : active.triggers;
    }

    public TiledMap getCurrentMap() {
        return active == null ? null : active.map;
    }

    public void showMap(MapAssets initialAssets) {
        if (active != null) { return; }
        install(prepare(entry == null ? new Entry(initialAssets, null) : entry));
    }

    public void hideMap() {
        hideMap(getEngine());
    }

    private void hideMap(Engine engine) {
        pendingTravel = null;
        if (active == null) { return; }
        removeRuntime(engine);
        bindMap(engine, null);
        releaseAssets(active.ownedAssets, List.of());
        active = null;
    }

    boolean requestTravel(Entity player, String mapId, String spawn) {
        if (active == null || active.player != player) { return false; }
        if (pendingTravel == null) { pendingTravel = new Travel(player, mapId, spawn); }
        return true;
    }

    /** Called by GameScreen after Ashley and Box2D finish updating. */
    public void applyPendingTravel() {
        var travel = pendingTravel;
        pendingTravel = null;
        if (travel == null || !getEngine().getEntities().contains(travel.player(), true)) { return; }
        RuntimeMap next;
        try {
            var assets = active.entry.assets();
            if (!assets.mapAsset().id().equals(travel.mapId())) {
                assets = ManifestLoader.parseMapManifest(travel.mapId());
            }
            next = prepare(new Entry(assets, travel.spawn()));
        } catch (RuntimeException failure) {
            Gdx.app.error("TiledSystem", "Cannot travel to " + travel.mapId() + "/" + travel.spawn(), failure);
            return;
        }
        install(next);
    }

    void setMap(TiledMap map, MapAssets assets) {
        install(prepareRuntime(map, new Entry(assets, null), new ArrayList<>()));
    }

    private RuntimeMap prepare(Entry destination) {
        var acquired = new ArrayList<Asset<?>>();
        try {
            AudioSystem.validateMapMusic(destination.assets());
            for (var asset : destination.assets().all()) {
                if (!assetService.isRegistered(asset)) { acquired.add(asset); }
                assetService.load(asset);
            }
            var map = assetService.get(TiledMap.class, destination.assets().mapAsset().id());
            return prepareRuntime(map, destination, acquired);
        } catch (RuntimeException | Error failure) {
            releaseAssets(acquired, List.of());
            throw failure;
        }
    }

    // Build the replacement without registering entities or stepping the shared World.
    // Authored-data failures leave the active runtime intact.
    private RuntimeMap prepareRuntime(TiledMap map, Entry destination, List<Asset<?>> acquired) {
        if (physicsWorld.isLocked()) { throw new IllegalStateException("Cannot change maps during a physics step"); }
        var triggers = MapTriggers.parse(map);
        var point = destination.spawn() == null ? triggers.points().stream()
            .filter(p -> p.type() == MapTriggers.SpawnType.PLAYER).findFirst().orElse(null)
            : triggers.travelDestination(destination.spawn());
        var next = new RuntimeMap(map, destination, triggers, acquired);
        try {
            initMapBoundary(next);
            loadMapObjects(next);
            if (point != null) { spawnPlayerEntity(next, point.position()); }
            var factory = getEngine().getSystem(EntityFactory.class);
            for (var marker : triggers.points()) {
                switch (marker.type()) {
                    case NPC -> next.entities.add(factory.getMapActor(marker.position(), false));
                    case ENEMY -> next.entities.add(factory.getMapActor(marker.position(), true));
                    default -> { }
                }
            }
            next.sensors = getEngine().getSystem(MapSensorSystem.class).prepareMap(triggers);
            return next;
        } catch (RuntimeException | Error failure) {
            for (var entity : next.entities) {
                var physics = Physic.MAPPER.get(entity);
                if (physics != null) { physicsWorld.destroyBody(physics.getBody()); }
            }
            for (var body : next.bodies) { physicsWorld.destroyBody(body); }
            throw failure;
        }
    }

    private void install(RuntimeMap next) {
        var engine = getEngine();
        var previous = active;
        if (previous != null) {
            removeRuntime(engine);
            var destinationAssets = next.entry.assets().all();
            for (var asset : previous.ownedAssets) {
                if (destinationAssets.contains(asset)) { next.ownedAssets.add(asset); }
            }
        }

        active = next;
        entry = next.entry;
        var sensors = engine.getSystem(MapSensorSystem.class);
        sensors.installMap(next.triggers, next.sensors);
        next.sensors = null;
        for (var entity : next.entities) { engine.addEntity(entity); }
        if (next.player != null) {
            if (playerState == null) { playerState = PlayerState.of(next.player); }
            var controller = Controller.MAPPER.get(next.player);
            if (controller != null) { inputManager.regKeyListener(controller.getInputListener()); }
            if (entry.spawn() != null) { sensors.suppressArrivalPortals(next.player); }
        }
        var physics = engine.getSystem(PhysicSystem.class);
        if (physics != null) { physics.resetClock(); }
        bindMap(engine, next);
        if (previous != null) { releaseAssets(previous.ownedAssets, next.ownedAssets); }
        eventEmitter.accept(new Event.Map.Changed(next.entry.assets().mapAsset().id()));
    }

    private void removeRuntime(Engine engine) {
        var sensors = engine.getSystem(MapSensorSystem.class);
        if (sensors != null) { sensors.clearMap(); }
        inputManager.releaseHeldInputs();
        for (var entity : active.entities) {
            var controller = Controller.MAPPER.get(entity);
            if (controller != null) { inputManager.unRegKeyListener(controller.getInputListener()); }
            engine.removeEntity(entity);
        }
        for (var body : active.bodies) { physicsWorld.destroyBody(body); }
        if (active.player != null) {
            active.player.remove(Health.class);
            active.player.remove(Loadout.class);
            active.player.remove(Facing.class);
        }
    }

    private static void bindMap(Engine engine, RuntimeMap next) {
        var renderer = engine.getSystem(RenderSystem.class);
        if (renderer != null) { renderer.setMap(next == null ? null : next.map); }
        var camera = engine.getSystem(CameraSystem.class);
        if (camera != null && next != null) { camera.setMap(next.map, next.player); }
        var audio = engine.getSystem(AudioSystem.class);
        if (audio != null) { audio.setMap(next == null ? null : next.entry.assets()); }
    }

    private void releaseAssets(List<Asset<?>> owned, List<Asset<?>> retained) {
        for (int i = owned.size() - 1; i >= 0; i--) {
            var asset = owned.get(i);
            if (!retained.contains(asset)) { assetService.unload(asset.type(), asset.id()); }
        }
    }

    private void loadMapObjects(RuntimeMap next) {
        for (var layer : next.map.getLayers()) {
            switch (layer) {
                case MapLayer l when l.getName().equals("Objects") || l.getName().equals("Collisions") -> loadObjectLayer(next, layer);
                case MapLayer l when l.getName().equals("Triggers") || l.getName().equals("Trigger") -> { }
                case TiledMapTileLayer t -> loadTileLayer(next, t);
                default -> Gdx.app.debug("TiledSystem", "Skipping load for layer: " + layer.getName());
            }
        }
    }

    private void spawnPlayerEntity(RuntimeMap next, Vector2 pixelPosition) {
        next.player = getEngine().getSystem(EntityFactory.class).getPlayerEntity(pixelPosition, playerState);
        next.entities.add(next.player);
        var properties = next.map.getProperties();
        next.player.add(new BowAim(properties.get("tilewidth", Integer.class) * GameConsts.UNIT_SCALE,
            properties.get("tileheight", Integer.class) * GameConsts.UNIT_SCALE));
    }

    private void initMapBoundary(RuntimeMap next) {
        var dimensions = next.map.getProperties();
        int width = dimensions.get("width", 0, Integer.class);
        int height = dimensions.get("height", 0, Integer.class);
        int tileWidth = dimensions.get("tilewidth", 0, Integer.class);
        int tileHeight = dimensions.get("tileheight", 0, Integer.class);

        float mapW = (float) width * tileWidth * GameConsts.UNIT_SCALE;
        float mapH = (float) height * tileHeight * GameConsts.UNIT_SCALE;
        if (width <= 0 || tileWidth <= 0 || height <= 0 || tileHeight <= 0
            || !Float.isFinite(mapW) || !Float.isFinite(mapH) || mapW <= 0 || mapH <= 0) {
            throw new IllegalArgumentException("Map boundaries require positive finite map and tile dimensions");
        }
        var body = createStaticBody(next, Vector2.Zero);
        float halfW = mapW * .5f;
        float halfH = mapH * .5f;
        float thickness = .5f;
        var shape = new PolygonShape();
        try {
            boundaryEdge(body, shape, thickness, halfH, new Vector2(-thickness, halfH));
            boundaryEdge(body, shape, thickness, halfH, new Vector2(mapW + thickness, halfH));
            boundaryEdge(body, shape, halfW, thickness, new Vector2(halfW, -thickness));
            boundaryEdge(body, shape, halfW, thickness, new Vector2(halfW, mapH + thickness));
        } finally {
            shape.dispose();
        }
    }

    private static void boundaryEdge(Body body, PolygonShape shape, float halfW, float halfH, Vector2 center) {
        shape.setAsBox(halfW, halfH, center, 0f);
        body.createFixture(shape, 0f).setFriction(0f);
    }

    private Body createStaticBody(RuntimeMap next, Vector2 position) {
        var definition = new BodyDef();
        definition.type = BodyDef.BodyType.StaticBody;
        definition.position.set(position);
        definition.fixedRotation = true;
        var body = physicsWorld.createBody(definition);
        body.setUserData("environment");
        next.bodies.add(body);
        return body;
    }

    private void loadTileLayer(RuntimeMap next, TiledMapTileLayer layer) {
        for (int y = 0; y < layer.getHeight(); y++) {
            for (int x = 0; x < layer.getWidth(); x++) {
                var cell = layer.getCell(x, y);
                if (cell == null || cell.getTile().getObjects().getCount() == 0) { continue; }
                var body = createStaticBody(next, new Vector2(x, y));
                addTileFixtures(body, cell.getTile().getObjects());
            }
        }
    }

    private static void addTileFixtures(Body body, MapObjects objects) {
        for (var object : objects) {
            var fixture = TiledPhysics.fixtureDefOf(object, GameConsts.PHYSIC_SCALING, Vector2.Zero);
            try {
                body.createFixture(fixture).setUserData(object.getName());
            } finally {
                fixture.shape.dispose();
            }
        }
    }

    private void loadObjectLayer(RuntimeMap next, MapLayer layer) {
        for (var mapObj : layer.getObjects()) {
            if (mapObj instanceof TiledMapTileMapObject object) {
                String type = object.getProperties().get("type",
                    object.getTile().getProperties().get("type", "", String.class), String.class);
                var factory = getEngine().getSystem(EntityFactory.class);
                var assets = next.entry.assets();
                var entity = switch (type) {
                    case "PhysicObject" -> factory.getPhysicObjectEntity(PhysicObjectProps.fromMapObj(object), object, assets);
                    case "AnimatedPhysicObject" -> factory.getAnimatedPhysicObjectEntity(AnimatedPhysicObjectProps.fromMapObj(object), object, assets);
                    default -> throw new GdxRuntimeException("Unsupported tile object class: '" + type
                        + "' (object ID: " + object.getProperties().get("id") + ")");
                };
                next.entities.add(entity);
            } else if (!(mapObj instanceof PointMapObject)) {
                throw new GdxRuntimeException("Unsupported Obj: " + mapObj.getClass().getSimpleName());
            }
        }
    }

    private record Entry(MapAssets assets, String spawn) { }
    private record Travel(Entity player, String mapId, String spawn) { }

    private static final class RuntimeMap {
        final TiledMap map;
        final Entry entry;
        final MapTriggers triggers;
        final List<Asset<?>> ownedAssets;
        final List<Body> bodies = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        List<MapSensorSystem.SensorBody> sensors;
        Entity player;

        RuntimeMap(TiledMap map, Entry entry, MapTriggers triggers, List<Asset<?>> ownedAssets) {
            this.map = map;
            this.entry = entry;
            this.triggers = triggers;
            this.ownedAssets = ownedAssets;
        }
    }
}
