package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.gameplay.HoardRun;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.random.RandomGenerator;

public class TiledSystem extends EntitySystem implements EventEmitter {
    private final AssetService assetService;
    private final World physicsWorld;
    private final InputManager inputManager;
    private final boolean assetValidation;
    private final boolean hammerDemo;
    private final RandomGenerator arenaRandom = RandomGenerator.getDefault();
    private RuntimeMap active;
    // These outlive a map runtime (including hide/show), but not this gameplay session.
    private Entry entry;
    private PlayerState playerState;
    private Travel pendingTravel;
    private boolean pendingRespawn;
    private double simulationSeconds;
    private DeathSystem.Corpse hiddenPlayerCorpse;
    // Only unfinished deaths survive map teardown; after expiry the next runtime spawns normally.
    private final Map<String, Map<String, DeathSystem.Corpse>> offMapCorpses = new HashMap<>();

    public TiledSystem(AssetService assetService, World physicsWorld, InputManager inputManager, boolean assetValidation) {
        this(assetService, physicsWorld, inputManager, assetValidation, false);
    }

    public TiledSystem(AssetService assetService, World physicsWorld, InputManager inputManager,
                       boolean assetValidation, boolean hammerDemo) {
        this.assetService = assetService;
        this.physicsWorld = physicsWorld;
        this.inputManager = inputManager;
        this.assetValidation = assetValidation;
        this.hammerDemo = hammerDemo;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        hideMap(engine);
        entry = null;
        playerState = null;
        hiddenPlayerCorpse = null;
        offMapCorpses.clear();
    }

    public MapTriggers getMapTriggers() {
        return active == null ? null : active.triggers;
    }

    public TiledMap getCurrentMap() {
        return active == null ? null : active.map;
    }

    /** Call on the simulation thread outside a Box2D step. Null means no clear spot in this batch. */
    public Entity trySpawnAreaEnemy(MapTriggers.SpawnArea area, RandomGenerator random) {
        return trySpawnAreaEnemy(area, area.spawnKey(), random);
    }

    Entity trySpawnAreaEnemy(MapTriggers.SpawnArea area, String key, RandomGenerator random) {
        if (physicsWorld.isLocked()) { throw new IllegalStateException("Cannot spawn during a physics step"); }
        var footprints = active == null ? null : active.enemyFootprints.get(area);
        var footprint = footprints == null ? null : footprints.get(key);
        if (footprint == null) { throw new IllegalArgumentException("Expected a configured enemy area from the active map"); }
        var candidate = new Rectangle();
        var occupied = new boolean[1];
        var bounds = area.bounds();
        for (int attempt = 0; attempt < 32; attempt++) {
            float x = bounds.x + random.nextFloat() * bounds.width;
            float y = bounds.y + random.nextFloat() * bounds.height;
            candidate.set(footprint).setPosition(x + footprint.x, y + footprint.y);
            if (!active.mapBounds.contains(candidate) || !area.contains(candidate)
                || (area.polygon() != null && !area.polygon().contains(x, y))) { continue; }
            // Conservative broad phase. Sensors (including awareness and hurtboxes) do not block;
            // all solid fixtures do, including actors inserted earlier in this same tick.
            occupied[0] = false;
            physicsWorld.QueryAABB(fixture -> {
                occupied[0] = !fixture.isSensor();
                return !occupied[0];
            }, candidate.x, candidate.y, candidate.x + candidate.width, candidate.y + candidate.height);
            if (occupied[0]) { continue; }
            var pixels = new Vector2(x, y).scl(1f / GameConsts.UNIT_SCALE);
            var actor = getEngine().getSystem(EntityFactory.class).getMapActor(pixels, Actor.Role.ENEMY, key, area.facing());
            // Runtime-owned but not tied to a persistent point/corpse on reentry.
            active.entities.add(actor);
            // The body participates in the next fixed tick. Ashley may not publish the entity
            // until PhysicSystem returns; entityAdded ends this map-owned registration window.
            if (active.arena != null && active.arena.contains(area)) { active.arrivingEnemies.add(actor); }
            getEngine().addEntity(actor);
            return actor;
        }
        return null;
    }

    Entity activePlayer() { return active.player; }

    void addArenaEntity(Entity entity) {
        active.entities.add(entity);
        if (ActorAi.MAPPER.has(entity)) { active.arrivingEnemies.add(entity); }
        getEngine().addEntity(entity);
    }

    void collectPickups() {
        var collected = new ArrayList<Entity>();
        for (var contact : physicsWorld.getContactList()) {
            if (!contact.isTouching()) { continue; }
            var a = contact.getFixtureA();
            var b = contact.getFixtureB();
            var other = !a.isSensor() && a.getBody().getUserData() == active.player ? b
                : !b.isSensor() && b.getBody().getUserData() == active.player ? a : null;
            if (other == null || !(other.getBody().getUserData() instanceof Entity object)) { continue; }
            var pickup = object.getComponent(Pickup.class);
            if (pickup == null || object.isScheduledForRemoval() || collected.contains(object)) { continue; }
            switch (pickup.rule().type()) {
                case HEAL -> { if (!HealthFeedback.healPlayer(active.player, pickup.rule().amount())) { continue; } }
                case WEAPON_BAG -> Controller.MAPPER.get(active.player).collectWeapon(pickup.weapon());
            }
            collected.add(object);
        }
        for (var object : collected) {
            active.entities.remove(object);
            removeEntity(getEngine(), object);
        }
    }

    public void showMap(MapAssets initialAssets) {
        if (active != null) { return; }
        install(prepare(entry == null ? new Entry(initialAssets, null) : entry));
    }

    Health playerHealth() {
        if (active != null && active.arenaHealth != null) { return active.arenaHealth; }
        if (active != null && active.player != null) { return Health.MAPPER.get(active.player); }
        return playerState == null ? null : playerState.health();
    }

    boolean ownsArrivingEnemy(Entity entity) {
        return active != null && !entity.isScheduledForRemoval() && active.arrivingEnemies.contains(entity);
    }

    boolean owns(Entity entity) {
        return active != null && active.entities.contains(entity);
    }

    public void hideMap() {
        hideMap(getEngine());
    }

    @Override
    public void update(float deltaTime) {
        if (active == null) { return; }
        simulationSeconds += deltaTime;
        offMapCorpses.values().removeIf(corpses -> {
            corpses.values().removeIf(corpse -> corpse.expired(simulationSeconds));
            return corpses.isEmpty();
        });
    }

    private void hideMap(Engine engine) {
        pendingTravel = null;
        pendingRespawn = false;
        if (active == null) { return; }
        var death = engine.getSystem(DeathSystem.class);
        if (death != null) { death.update(0f); }
        // Arena visits start fresh, even if the previous death pose had not yet expired.
        hiddenPlayerCorpse = active.player != null && active.arena == null
            ? DeathSystem.snapshot(active.player, simulationSeconds) : null;
        pendingRespawn = false;
        stashActiveCorpses();
        removeRuntime(engine);
        bindMap(engine, null);
        releaseAssets(active.ownedAssets, List.of());
        active = null;
    }

    boolean requestTravel(Entity player, String mapId, String spawn) {
        if (active == null || active.player != player || !Health.MAPPER.get(player).isAlive()) { return false; }
        if (pendingTravel == null) { pendingTravel = new Travel(player, mapId, spawn); }
        return true;
    }

    /** Called by GameScreen after Ashley and Box2D finish updating. */
    public void applyPendingTravel() {
        var travel = pendingTravel;
        pendingTravel = null;
        if (travel == null || active == null || active.player != travel.player()
            || !Health.MAPPER.get(travel.player()).isAlive()) { return; }
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

    void detachController(Entity player) {
        var controller = Controller.MAPPER.get(player);
        if (controller != null) {
            inputManager.unRegKeyListener(controller.getInputListener());
            controller.resetInput();
            player.remove(Controller.class);
        }
    }

    boolean retireCorpse(Entity entity) {
        if (active == null) { return false; }
        if (active.player == entity) {
            if (active.arena == null) {
                pendingRespawn = true;
                entity.remove(Graphic.class);
                return true;
            }
            active.player = null;
            detachPlayerState(entity);
        }
        active.actorSpawns.remove(entity);
        active.arrivingEnemies.remove(entity);
        active.entities.remove(entity);
        return false; // DeathSystem removes the entity from Ashley (and thus the body from Box2D).
    }

    /** Replace just the player after the engine update, with families registered before portal checks. */
    public void applyPendingRespawn() {
        if (!pendingRespawn || physicsWorld.isLocked()) { return; }
        pendingRespawn = false;
        pendingTravel = null;
        var engine = getEngine();
        var corpse = active.player;
        var replacement = createPlayer(active, respawnPoint(active));
        inputManager.releaseHeldInputs();
        removeEntity(engine, corpse);
        active.entities.remove(corpse);
        detachPlayerState(corpse);
        active.player = replacement;
        active.entities.add(replacement);
        Health.MAPPER.get(replacement).restore();
        engine.addEntity(replacement);
        inputManager.regKeyListener(Controller.MAPPER.get(replacement).getInputListener());
        engine.getSystem(MapSensorSystem.class).suppressArrivalPortals(replacement);
        var camera = engine.getSystem(CameraSystem.class);
        if (camera != null) { camera.setMap(active.map, replacement); }
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
        var mapId = destination.assets().mapAsset().id();
        var arena = HoardSystem.arena(mapId, triggers);
        var next = new RuntimeMap(map, destination, triggers, acquired, arena, hammerDemo && arena != null);
        var corpses = active != null && active.entry.assets().mapAsset().id().equals(mapId)
            ? activeCorpses() : offMapCorpses.getOrDefault(mapId, Map.of());
        var death = getEngine().getSystem(DeathSystem.class);
        var point = destination.spawn() == null ? respawnPoint(next) : triggers.travelDestination(destination.spawn());
        if (next.arena != null && point == null) { throw new IllegalArgumentException("Arena requires a player spawn"); }
        try {
            initMapBoundary(next);
            loadMapObjects(next);
            if (point != null) {
                next.player = createPlayer(next, point);
                next.entities.add(next.player);
                if (hiddenPlayerCorpse != null) { death.restore(next.player, hiddenPlayerCorpse, simulationSeconds); }
            }
            var factory = getEngine().getSystem(EntityFactory.class);
            record FootprintKey(String spawnKey, Facing.Direction facing) { }
            var stagedFootprints = new HashMap<FootprintKey, Rectangle>();
            BiFunction<String, Facing.Direction, Rectangle> enemyFootprint = (key, facing) ->
                stagedFootprints.computeIfAbsent(new FootprintKey(key, facing),
                    spawn -> factory.enemySpawnFootprint(spawn.spawnKey(), spawn.facing()));
            for (var area : triggers.areas()) {
                if (area.type() == MapTriggers.SpawnType.ENEMY && area.behaviorKey() != null) {
                    var footprints = new HashMap<String, Rectangle>();
                    footprints.put(area.spawnKey(), enemyFootprint.apply(area.spawnKey(), area.facing()));
                    if (next.arena != null && next.arena.contains(area)) {
                        for (var entry : HoardSystem.WAVE_ROSTER) {
                            footprints.put(entry.key(), enemyFootprint.apply(entry.key(), area.facing()));
                        }
                    }
                    next.enemyFootprints.put(area, footprints);
                }
            }
            for (var marker : triggers.points()) {
                if (next.arena != null && "idle_dredge".equals(marker.behaviorKey())) { continue; }
                switch (marker.type()) {
                    case NPC, ENEMY -> {
                        var role = marker.type() == MapTriggers.SpawnType.NPC ? Actor.Role.NPC : Actor.Role.ENEMY;
                        var bounty = next.arena != null && role == Actor.Role.ENEMY
                            ? HoardSystem.rosterBounty(marker.spawnKey()) : null;
                        var actor = factory.getMapActor(marker.position(), role, marker.spawnKey(), marker.facing());
                        next.entities.add(actor);
                        if (bounty != null) { actor.add(bounty); }
                        // Arena actors belong to this run, including authored point enemies.
                        if (next.arena == null) { next.actorSpawns.put(actor, marker.name()); }
                        var corpse = corpses.get(marker.name());
                        if (corpse != null) { death.restore(actor, corpse, simulationSeconds); }
                    }
                    case ITEM, OBJECT -> next.entities.add(factory.getSpawnedObject(marker.position(), marker.spawnKey(), marker.facing()));
                    default -> { }
                }
            }
            if (assetValidation && next.arena != null) {
                HoardSystem.validateDelayedSpawns(factory, triggers, next.player, enemyFootprint);
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
        pendingRespawn = false;
        var previous = active;
        if (previous != null) {
            stashActiveCorpses();
            removeRuntime(engine);
            var destinationAssets = next.entry.assets().all();
            for (var asset : previous.ownedAssets) {
                if (destinationAssets.contains(asset)) { next.ownedAssets.add(asset); }
            }
        }

        active = next;
        entry = next.entry;
        hiddenPlayerCorpse = null;
        offMapCorpses.remove(entry.assets().mapAsset().id());
        var sensors = engine.getSystem(MapSensorSystem.class);
        sensors.installMap(next.triggers, next.sensors);
        next.sensors = null;
        for (var entity : next.entities) { engine.addEntity(entity); }
        if (next.player != null) {
            playerState = next.sessionPlayer;
            var controller = Controller.MAPPER.get(next.player);
            if (controller != null) {
                inputManager.regKeyListener(controller.getInputListener());
                if (entry.spawn() != null) { sensors.suppressArrivalPortals(next.player); }
            }
        }
        var physics = engine.getSystem(PhysicSystem.class);
        if (physics != null) { physics.resetClock(); }
        if (next.arena != null) { engine.addEntityListener(next); }
        bindMap(engine, next);
        if (previous != null) { releaseAssets(previous.ownedAssets, next.ownedAssets); }
        eventEmitter.accept(new Event.Map.Changed(next.entry.assets().mapAsset().id()));
    }

    private Map<String, DeathSystem.Corpse> activeCorpses() {
        var corpses = new HashMap<String, DeathSystem.Corpse>();
        active.actorSpawns.forEach((actor, marker) -> {
            var corpse = DeathSystem.snapshot(actor, simulationSeconds);
            if (corpse != null) { corpses.put(marker, corpse); }
        });
        return corpses;
    }

    private void stashActiveCorpses() {
        var corpses = activeCorpses();
        if (!corpses.isEmpty()) { offMapCorpses.put(active.entry.assets().mapAsset().id(), corpses); }
    }

    private void removeRuntime(Engine engine) {
        if (active.arena != null) {
            engine.removeEntityListener(active);
            active.arrivingEnemies.clear();
        }
        var sensors = engine.getSystem(MapSensorSystem.class);
        if (sensors != null) { sensors.clearMap(); }
        inputManager.releaseHeldInputs();
        for (var entity : active.entities) { removeEntity(engine, entity); }
        for (var body : active.bodies) { physicsWorld.destroyBody(body); }
        if (active.player != null) { detachPlayerState(active.player); }
    }

    private void removeEntity(Engine engine, Entity entity) {
        detachController(entity);
        engine.removeEntity(entity);
        entity.remove(HealthFeedback.class);
    }

    private static void detachPlayerState(Entity player) {
        player.remove(Health.class);
        player.remove(Loadout.class);
        player.remove(Facing.class);
    }

    private void bindMap(Engine engine, RuntimeMap next) {
        var hoard = engine.getSystem(HoardSystem.class);
        if (hoard != null) {
            hoard.bind(this, next == null ? null : next.arena, arenaRandom,
                next == null ? null : next.triggers, next != null && next.hammerDemo);
        }
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

    private static MapTriggers.SpawnPoint respawnPoint(RuntimeMap runtime) {
        for (var point : runtime.triggers.points()) {
            if (point.type() == MapTriggers.SpawnType.PLAYER) { return point; }
        }
        var arrival = runtime.entry.spawn();
        return arrival == null ? null : runtime.triggers.travelDestination(arrival);
    }

    private Entity createPlayer(RuntimeMap runtime, MapTriggers.SpawnPoint point) {
        var player = getEngine().getSystem(EntityFactory.class).getPlayerEntity(point.position(), playerState, point.facing());
        // Keep ordinary health and equipment out of the visit's temporary inventory and health pool.
        runtime.sessionPlayer = PlayerState.of(player);
        if (runtime.arenaHealth != null) {
            var loadout = runtime.sessionPlayer.loadout().copy();
            if (runtime.hammerDemo) { loadout.setWeapon("basic_hammer"); }
            var controller = runtime.hammerDemo
                ? new Controller(GameCore.INSTANCE.getGameData().weaponsData().weapons().keySet().stream().sorted().toList())
                : Controller.forArena(loadout.getWeapon().id());
            player.add(runtime.arenaHealth).add(loadout).add(controller).add(new DredgeLunge());
        }
        var properties = runtime.map.getProperties();
        player.add(new BowAim(properties.get("tilewidth", Integer.class) * GameConsts.UNIT_SCALE,
            properties.get("tileheight", Integer.class) * GameConsts.UNIT_SCALE));
        return player;
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
        next.mapBounds.set(0, 0, mapW, mapH);
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

    private static final class RuntimeMap implements EntityListener {
        final TiledMap map;
        final Entry entry;
        final MapTriggers triggers;
        final List<Asset<?>> ownedAssets;
        final List<Body> bodies = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final Set<Entity> arrivingEnemies = Collections.newSetFromMap(new IdentityHashMap<>());
        final Rectangle mapBounds = new Rectangle();
        final Map<MapTriggers.SpawnArea, Map<String, Rectangle>> enemyFootprints = new IdentityHashMap<>();
        final Map<Entity, String> actorSpawns = new IdentityHashMap<>();
        List<MapSensorSystem.SensorBody> sensors;
        final List<MapTriggers.SpawnArea> arena;
        final boolean hammerDemo;
        final Health arenaHealth; // Remains available to the HUD after the corpse is removed.
        Entity player;
        PlayerState sessionPlayer;

        @Override public void entityAdded(Entity entity) { arrivingEnemies.remove(entity); }
        @Override public void entityRemoved(Entity entity) { arrivingEnemies.remove(entity); }

        RuntimeMap(TiledMap map, Entry entry, MapTriggers triggers, List<Asset<?>> ownedAssets,
                   List<MapTriggers.SpawnArea> arena, boolean hammerDemo) {
            this.arena = arena;
            this.hammerDemo = hammerDemo;
            arenaHealth = arena == null ? null : new Health(HoardRun.PLAYER_HEALTH);
            this.map = map;
            this.entry = entry;
            this.triggers = triggers;
            this.ownedAssets = ownedAssets;
        }
    }
}
