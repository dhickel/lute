package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Intersector;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Map-owned sensor contacts. Combat remains the world's sole contact listener. */
public final class MapSensorSystem extends EntitySystem implements EntityListener, EventEmitter, Disposable {
    private static final Family PLAYERS = Family.all(Player.class, Physic.class, Transform.class).get();
    private static final float OVERLAP_TOLERANCE = 10 * Math.ulp(1f); // Box2D's b2TestOverlap distance tolerance.
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
                var body = world.createBody(new BodyDef());
                var owned = new SensorBody(sensor, body);
                sensors.add(owned);
                var geometry = sensor.geometry();
                if (geometry.radius() > 0) {
                    var shape = new CircleShape();
                    try {
                        shape.setPosition(geometry.center());
                        shape.setRadius(geometry.radius());
                        createFixture(owned, shape);
                    } finally {
                        shape.dispose();
                    }
                } else {
                    var shape = new PolygonShape();
                    try {
                        for (var polygon : geometry.polygons()) {
                            shape.set(polygon);
                            createFixture(owned, shape);
                        }
                    } finally {
                        shape.dispose();
                    }
                }
            }
        } catch (RuntimeException | Error failure) {
            clearMap();
            throw failure;
        }
    }

    private static void createFixture(SensorBody sensor, Shape shape) {
        var definition = new FixtureDef();
        definition.shape = shape;
        definition.isSensor = true;
        sensor.body.createFixture(definition).setUserData(sensor);
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
        if (sensor.getUserData() instanceof SensorBody owned && owned.live
            && fixture.getBody().getUserData() instanceof Entity player && players.containsKey(player)
            && (!entered || !fixture.isSensor())) {
            pending.add(new ContactChange(owned, fixturePair(sensor, fixture), player, entered));
        }
    }

    @Override
    public void update(float deltaTime) {
        afterPhysicsStep();
    }

    // PhysicSystem also drains after EACH fixed tick, before interpolation or the next tick.
    void afterPhysicsStep() {
        if (world.isLocked()) { return; }
        var previous = new LinkedHashMap<Occupancy, Boolean>();
        for (var sensor : sensors) {
            sensor.arrivals.removeIf(player -> !overlaps(sensor, player));
            sensor.contacts.entrySet().removeIf(entry -> {
                var player = entry.getKey();
                previous.put(new Occupancy(sensor, player), true);
                // Reconciled arrivals may never get a native end callback. Refresh the whole union:
                // a move across an internal seam must not exit while new native pairs are pending.
                var touching = touchingPairs(sensor, player);
                entry.setValue(touching);
                return touching.isEmpty();
            });
        }
        for (var change : pending) {
            var sensor = change.sensor();
            var player = change.player();
            if (!sensor.live || !players.containsKey(player)) { continue; }
            previous.putIfAbsent(new Occupancy(sensor, player), sensor.contacts.containsKey(player));
            var pair = change.pair();
            // Fixture removal can queue an end callback before its native shape is destroyed.
            boolean touching = isLivePlayerFixture(player, pair) && overlaps(pair.detector(), pair.player());
            if (change.entered() && touching) {
                sensor.contacts.computeIfAbsent(player, ignored -> new HashSet<>()).add(pair);
            } else if (!touching) {
                var contacts = sensor.contacts.get(player);
                if (contacts != null) {
                    contacts.remove(pair);
                    if (contacts.isEmpty()) { sensor.contacts.remove(player); }
                }
            }
        }
        pending.clear();

        // Decide once per fixed tick, after ALL fixture changes: a seam is not an exit/reentry.
        var entered = new ArrayList<Occupancy>();
        for (var entry : previous.entrySet()) {
            var visit = entry.getKey();
            boolean inside = visit.sensor().contacts.containsKey(visit.player());
            if (!entry.getValue() && inside) {
                emitEnter(visit.sensor(), visit.player());
                entered.add(visit);
            } else if (entry.getValue() && !inside) {
                emitExit(visit.sensor(), visit.player());
            }
        }

        Set<Entity> moved = identitySet();
        for (var visit : entered) {
            var sensor = visit.sensor();
            var player = visit.player();
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
            var touching = touchingPairs(sensor, player);
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

    private boolean isLivePlayerFixture(Entity player, FixturePair pair) {
        var fixture = pair.player();
        return players.get(player).getBody().getFixtureList().contains(fixture, true) && !fixture.isSensor()
            && fixture.getShape() == pair.playerShape();
    }

    private static FixturePair fixturePair(Fixture detector, Fixture player) {
        // Box2D pools Fixture wrappers. The cached Shape wrapper changes when a Fixture is reused.
        return new FixturePair(detector, player, player.getShape());
    }

    private Set<FixturePair> touchingPairs(SensorBody sensor, Entity player) {
        Set<FixturePair> touching = new HashSet<>();
        var physic = players.get(player);
        if (physic == null) { return touching; }
        for (var fixture : physic.getBody().getFixtureList()) {
            if (fixture.isSensor()) { continue; }
            for (var detector : sensor.body.getFixtureList()) {
                if (overlaps(detector, fixture)) { touching.add(fixturePair(detector, fixture)); }
            }
        }
        return touching;
    }

    private boolean overlaps(SensorBody sensor, Entity player) {
        return !touchingPairs(sensor, player).isEmpty();
    }

    // Use the actual convex fixtures for both contacts and teleport reconciliation, never their AABBs.
    // Polygon skin radii matter too: native sensor contacts include these rounded edges.
    private static boolean overlaps(Fixture first, Fixture second) {
        var a = first.getShape();
        var b = second.getShape();
        float radius = a.getRadius() + b.getRadius() + OVERLAP_TOLERANCE;
        if (a instanceof CircleShape circleA && b instanceof CircleShape circleB) {
            var center = first.getBody().getWorldPoint(circleA.getPosition()).cpy();
            return center.dst2(second.getBody().getWorldPoint(circleB.getPosition())) <= radius * radius;
        }
        if (a instanceof PolygonShape polygonA && b instanceof PolygonShape polygonB) {
            var verticesA = worldVertices(first, polygonA);
            var verticesB = worldVertices(second, polygonB);
            if (Intersector.overlapConvexPolygons(verticesA, verticesB, null)) { return true; }
            for (int i = 0; i < verticesA.length; i += 2) {
                if (nearOutline(verticesB, verticesA[i], verticesA[i + 1], radius)) { return true; }
            }
            for (int i = 0; i < verticesB.length; i += 2) {
                if (nearOutline(verticesA, verticesB[i], verticesB[i + 1], radius)) { return true; }
            }
            return false;
        }
        if (a instanceof CircleShape circle && b instanceof PolygonShape polygon) {
            return circlePolygon(first, circle, second, polygon, radius);
        }
        if (a instanceof PolygonShape polygon && b instanceof CircleShape circle) {
            return circlePolygon(second, circle, first, polygon, radius);
        }
        throw new IllegalStateException("Unsupported player fixture shape: " + b.getType());
    }

    private static boolean circlePolygon(Fixture circleFixture, CircleShape circle,
                                         Fixture polygonFixture, PolygonShape polygon, float radius) {
        var center = circleFixture.getBody().getWorldPoint(circle.getPosition());
        var vertices = worldVertices(polygonFixture, polygon);
        return Intersector.isPointInPolygon(vertices, 0, vertices.length, center.x, center.y)
            || nearOutline(vertices, center.x, center.y, radius);
    }

    private static boolean nearOutline(float[] vertices, float x, float y, float radius) {
        for (int i = 0; i < vertices.length; i += 2) {
            int next = (i + 2) % vertices.length;
            if (Intersector.distanceSegmentPoint(vertices[i], vertices[i + 1], vertices[next], vertices[next + 1], x, y)
                <= radius) { return true; }
        }
        return false;
    }

    private static float[] worldVertices(Fixture fixture, PolygonShape shape) {
        var vertices = new float[shape.getVertexCount() * 2];
        var point = new Vector2();
        for (int i = 0; i < shape.getVertexCount(); i++) {
            shape.getVertex(i, point);
            point.set(fixture.getBody().getWorldPoint(point));
            vertices[i * 2] = point.x;
            vertices[i * 2 + 1] = point.y;
        }
        return vertices;
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
        final Map<Entity, Set<FixturePair>> contacts = new IdentityHashMap<>();
        final Set<Entity> arrivals = identitySet();
        boolean live = true;

        SensorBody(MapTriggers.Sensor data, Body body) {
            this.data = data;
            this.body = body;
        }
    }

    private record FixturePair(Fixture detector, Fixture player, Shape playerShape) { }
    private record ContactChange(SensorBody sensor, FixturePair pair, Entity player, boolean entered) { }
    private record Occupancy(SensorBody sensor, Entity player) { }
}
