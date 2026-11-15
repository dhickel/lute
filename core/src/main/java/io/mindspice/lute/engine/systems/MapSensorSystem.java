package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.MapTriggers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Map-owned sensor contacts. Combat remains the world's sole contact listener. */
public final class MapSensorSystem extends EntitySystem implements EntityListener, EventEmitter, Disposable {
    private static final Family PLAYERS = Family.all(Player.class, Physic.class, Transform.class).get();
    private final World world;
    private final List<SensorBody> sensors = new ArrayList<>();
    private final List<ContactChange> pending = new ArrayList<>();
    private final Map<Entity, Physic> players = new IdentityHashMap<>();
    private MapTriggers triggers;

    public MapSensorSystem(World world) {
        this.world = world;
    }

    @Override
    public void addedToEngine(Engine engine) {
        engine.addEntityListener(PLAYERS, this);
        for (var player : engine.getEntitiesFor(PLAYERS)) { entityAdded(player); }
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(this);
        clearMap();
        players.clear();
    }

    public void setMap(MapTriggers triggers) {
        clearMap();
        this.triggers = triggers;
        try {
            for (var sensor : triggers.sensors()) {
                var bounds = sensor.bounds();
                var definition = new BodyDef();
                definition.position.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
                var body = world.createBody(definition);
                var owned = new SensorBody(sensor, body);
                sensors.add(owned);
                var shape = new PolygonShape();
                try {
                    shape.setAsBox(bounds.width / 2, bounds.height / 2);
                    var fixture = new FixtureDef();
                    fixture.shape = shape;
                    fixture.isSensor = true;
                    body.createFixture(fixture).setUserData(owned);
                } finally {
                    shape.dispose();
                }
            }
        } catch (RuntimeException | Error failure) {
            clearMap();
            throw failure;
        }
    }

    public void clearMap() {
        if (world.isLocked()) { throw new IllegalStateException("Cannot clear map sensors during a physics step"); }
        for (var sensor : sensors) {
            for (var player : sensor.contacts.keySet()) { emitExit(sensor, player); }
            sensor.live = false;
            world.destroyBody(sensor.body);
        }
        sensors.clear();
        pending.clear();
        triggers = null;
    }

    // Called only by CombatSystem's listener; never mutate the locked world here.
    void contact(Fixture first, Fixture second, boolean entered) {
        queueContact(first, second, entered);
        queueContact(second, first, entered);
    }

    private void queueContact(Fixture sensor, Fixture fixture, boolean entered) {
        if (sensor.getUserData() instanceof SensorBody owned && owned.live && !fixture.isSensor()
            && fixture.getBody().getUserData() instanceof Entity player && players.containsKey(player)) {
            pending.add(new ContactChange(owned, fixture, player, entered));
        }
    }

    @Override
    public void update(float deltaTime) {
        afterPhysicsStep();
    }

    // PhysicSystem also drains after EACH fixed tick, before interpolation or the next tick.
    void afterPhysicsStep() {
        if (world.isLocked()) { return; }
        for (var sensor : sensors) {
            sensor.arrivals.removeIf(player -> !overlaps(sensor, player));
        }
        var entered = new ArrayList<ContactChange>();
        for (var change : pending) {
            var sensor = change.sensor();
            if (!sensor.live || !players.containsKey(change.player())) { continue; }
            boolean wasInside = sensor.contacts.containsKey(change.player());
            if (change.entered()) {
                sensor.contacts.computeIfAbsent(change.player(), ignored -> identitySet()).add(change.fixture());
            } else {
                var contacts = sensor.contacts.get(change.player());
                // A teleport can move an existing pair back into the same sensor before Box2D retires it.
                if (contacts != null && !overlaps(sensor.data.bounds(), change.fixture())) {
                    contacts.remove(change.fixture());
                    if (contacts.isEmpty()) { sensor.contacts.remove(change.player()); }
                }
            }
            boolean isInside = sensor.contacts.containsKey(change.player());
            if (!wasInside && isInside) {
                emitEnter(sensor, change.player());
                entered.add(change);
            } else if (wasInside && !isInside) {
                emitExit(sensor, change.player());
            }
        }
        pending.clear();

        Set<Entity> moved = identitySet();
        for (var change : entered) {
            var sensor = change.sensor();
            var player = change.player();
            if (sensor.data.targetMap() == null || sensor.arrivals.contains(player) || moved.contains(player)
                || !sensor.contacts.containsKey(player) || !overlaps(sensor, player)) { continue; }
            if (!"THIS".equalsIgnoreCase(sensor.data.targetMap())) {
                // Future cross-map transitions belong here; no transition is performed today.
                Gdx.app.log("MapSensorSystem", "Unsupported teleport target map: " + sensor.data.targetMap());
                continue;
            }
            teleport(player, triggers.teleportDestination(sensor.data.targetSpawn()).position());
            moved.add(player);
        }
    }

    private void teleport(Entity player, Vector2 pixelPoint) {
        var physic = players.get(player);
        var body = physic.getBody();
        var transform = Transform.MAPPER.get(player);
        var position = pixelPoint.cpy().scl(GameConsts.UNIT_SCALE).sub(transform.getSize().x / 2, transform.getSize().y / 2);
        body.setTransform(position, body.getAngle());
        body.setLinearVelocity(0, 0);
        body.setAngularVelocity(0);
        body.setAwake(true);
        physic.getPrevPos().set(position);
        transform.getPosition().set(position);

        // Reconcile immediately: teleports must not lose generic exit/enter events or activate arrival portals.
        for (var sensor : sensors) {
            boolean wasInside = sensor.contacts.containsKey(player);
            Set<Fixture> touching = identitySet();
            for (var fixture : body.getFixtureList()) {
                if (!fixture.isSensor() && overlaps(sensor.data.bounds(), fixture)) { touching.add(fixture); }
            }
            if (touching.isEmpty()) {
                sensor.contacts.remove(player);
                sensor.arrivals.remove(player);
                if (wasInside) { emitExit(sensor, player); }
            } else {
                sensor.contacts.put(player, touching);
                if (sensor.data.targetMap() != null) { sensor.arrivals.add(player); }
                if (!wasInside) { emitEnter(sensor, player); }
            }
        }
    }

    private boolean overlaps(SensorBody sensor, Entity player) {
        var physic = players.get(player);
        if (physic == null) { return false; }
        for (var fixture : physic.getBody().getFixtureList()) {
            if (!fixture.isSensor() && overlaps(sensor.data.bounds(), fixture)) { return true; }
        }
        return false;
    }

    private static boolean overlaps(Rectangle bounds, Fixture fixture) {
        var body = fixture.getBody();
        return switch (fixture.getShape()) {
            case PolygonShape shape -> {
                float[] vertices = new float[shape.getVertexCount() * 2];
                var point = new Vector2();
                for (int i = 0; i < shape.getVertexCount(); i++) {
                    shape.getVertex(i, point);
                    point.set(body.getWorldPoint(point));
                    vertices[i * 2] = point.x;
                    vertices[i * 2 + 1] = point.y;
                }
                float x = bounds.x, y = bounds.y, right = x + bounds.width, top = y + bounds.height;
                yield Intersector.overlapConvexPolygons(vertices, new float[]{x, y, right, y, right, top, x, top}, null);
            }
            case CircleShape shape -> {
                var center = body.getWorldPoint(shape.getPosition());
                yield Intersector.overlaps(new Circle(center, shape.getRadius()), bounds);
            }
            default -> throw new IllegalStateException("Unsupported player fixture shape: " + fixture.getShape().getType());
        };
    }

    private void emitEnter(SensorBody sensor, Entity player) {
        eventEmitter.accept(new Event.Sensor.Enter(sensor.data.name(), sensor.data.type(), player));
    }

    private void emitExit(SensorBody sensor, Entity player) {
        eventEmitter.accept(new Event.Sensor.Exit(sensor.data.name(), sensor.data.type(), player));
    }

    @Override
    public void entityAdded(Entity entity) {
        players.put(entity, Physic.MAPPER.get(entity));
    }

    @Override
    public void entityRemoved(Entity entity) {
        players.remove(entity);
        pending.removeIf(change -> change.player() == entity);
        for (var sensor : sensors) {
            if (sensor.contacts.remove(entity) != null) { emitExit(sensor, entity); }
            sensor.arrivals.remove(entity);
        }
    }

    @Override
    public void dispose() {
        clearMap();
        players.clear();
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static final class SensorBody {
        final MapTriggers.Sensor data;
        final Body body;
        final Map<Entity, Set<Fixture>> contacts = new IdentityHashMap<>();
        final Set<Entity> arrivals = identitySet();
        boolean live = true;

        SensorBody(MapTriggers.Sensor data, Body body) {
            this.data = data;
            this.body = body;
        }
    }

    private record ContactChange(SensorBody sensor, Fixture fixture, Entity player, boolean entered) { }
}
