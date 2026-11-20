package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;
import io.mindspice.lute.engine.singletons.GameConsts;

final class ArrowFlight {
    final Entity owner;
    final Projectile type;
    final Vector2 tip;
    final Vector2 direction;
    private final Vector2 start, aimPoint, aimDirection;
    private final float damage, flightDistance, aimProgress, arcScale;
    private final boolean crossesAim;
    private final Vector2[] triangle;
    private final float collisionPad;
    private float elapsed;

    ArrowFlight(Entity owner, Projectile type, Vector2 start, Vector2 aimPoint, float damage) {
        this.owner = owner;
        this.type = type;
        this.start = start;
        this.aimPoint = aimPoint;
        this.damage = damage;
        tip = start.cpy();
        float distance = start.dst(aimPoint);
        aimDirection = distance == 0 ? switch (Facing.MAPPER.get(owner).getDirection()) {
            case UP -> new Vector2(0, 1);
            case DOWN -> new Vector2(0, -1);
            case LEFT -> new Vector2(-1, 0);
            case RIGHT -> new Vector2(1, 0);
        } : aimPoint.cpy().sub(start).nor();
        direction = aimDirection.cpy();
        flightDistance = type.speed() * type.lifetime();
        aimProgress = distance == 0 ? 1f : distance / flightDistance;
        crossesAim = distance > 0 && aimProgress <= 1f;
        // The offset is zero at launch and at the crosshair. Bound its magnitude
        // over the entire lifetime, including the descent past a nearby crosshair.
        float peak = Math.min(aimProgress / 2f, 1f);
        float maxOffset = Math.max(peak * (aimProgress - peak), Math.abs(aimProgress - 1f));
        arcScale = Math.min(type.arcHeight(), flightDistance * 0.15f) / maxOffset;
        float length = type.collisionBounds().length() * GameConsts.UNIT_SCALE;
        float halfWidth = type.collisionBounds().width() * GameConsts.UNIT_SCALE / 2;
        triangle = new Vector2[]{new Vector2(), new Vector2(-length, halfWidth), new Vector2(-length, -halfWidth)};
        collisionPad = length + halfWidth;
    }

    void worldBoundsVertex(int index, Vector2 out) {
        var vertex = triangle[index];
        out.set(tip.x + vertex.x * direction.x - vertex.y * direction.y,
            tip.y + vertex.x * direction.y + vertex.y * direction.x);
    }

    boolean finished(World world, float dt) {
        float progress = elapsed / type.lifetime();
        elapsed = Math.min(elapsed + dt, type.lifetime());
        float until = elapsed / type.lifetime();
        // Sweep chords along the whole curve, using the exact crosshair as a waypoint.
        while (progress < until) {
            float nextProgress = Math.min(until, progress + 1f / 16f);
            if (crossesAim && progress < aimProgress && aimProgress <= nextProgress) {
                nextProgress = aimProgress;
            }
            var next = start.cpy().mulAdd(aimDirection, flightDistance * nextProgress);
            if (crossesAim && nextProgress == aimProgress) {
                next.set(aimPoint);
            } else {
                next.y += arcScale * nextProgress * (aimProgress - nextProgress);
            }
            if (advance(world, next)) { return true; }
            progress = nextProgress;
        }
        return elapsed >= type.lifetime();
    }

    private boolean advance(World world, Vector2 next) {
        if (next.equals(tip)) { return false; }
        direction.set(next).sub(tip).nor();
        if (hit(world, next)) { return true; }
        tip.set(next);
        return false;
    }

    private boolean hit(World world, Vector2 next) {
        var sweep = new ArrowTipSweep(tip, direction, triangle, tip.dst(next));
        class Nearest implements QueryCallback {
            float distance = Float.POSITIVE_INFINITY;
            Entity target;
            @Override public boolean reportFixture(Fixture fixture) {
                var body = fixture.getBody();
                if (body.getUserData() == owner) { return true; }
                if (fixture.getUserData() != Hurtbox.INSTANCE) {
                    if (fixture.isSensor()) { return true; }
                    for (var other : body.getFixtureList()) {
                        if (other.getUserData() == Hurtbox.INSTANCE) { return true; }
                    }
                }
                float at = sweep.distance(fixture);
                if (at < distance) {
                    distance = at;
                    target = body.getUserData() instanceof Entity e ? e : null;
                }
                return true;
            }
        }
        var nearest = new Nearest();
        world.QueryAABB(nearest, Math.min(tip.x, next.x) - collisionPad, Math.min(tip.y, next.y) - collisionPad,
            Math.max(tip.x, next.x) + collisionPad, Math.max(tip.y, next.y) + collisionPad);
        var health = nearest.target == null ? null : Health.MAPPER.get(nearest.target);
        if (health != null && health.isAlive()) { health.damage(damage); }
        return Float.isFinite(nearest.distance);
    }
}
