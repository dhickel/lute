package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.data.json.ArrowData;
import io.mindspice.lute.engine.singletons.GameConsts;

final class ArrowFlight {
    final Entity owner;
    final ArrowData type;
    final Vector2 tip;
    final Vector2 direction;
    private final Vector2 start, end;
    private final float damage, duration, lift;
    private float progress;

    ArrowFlight(Entity owner, ArrowData type, Vector2 start, Vector2 end, float damage) {
        this.owner = owner;
        this.type = type;
        this.start = start;
        this.end = end;
        this.damage = damage;
        tip = start.cpy();
        direction = end.cpy().sub(start).nor();
        float distance = start.dst(end);
        duration = distance / type.speed();
        lift = Math.min(type.arcHeight(), distance * 0.15f);
    }

    boolean finished(World world, float dt) {
        float until = duration == 0 ? 1 : Math.min(1, progress + dt / duration);
        // Subdivide the curve even on long frames. Each chord sweeps the same triangle we draw.
        while (progress < until) {
            progress = Math.min(until, progress + 1f / 16f);
            var next = start.cpy().lerp(end, progress);
            next.y += 4 * lift * progress * (1 - progress);
            direction.set(next).sub(tip).nor();
            if (!direction.isZero() && hit(world, next)) { return true; }
            tip.set(next);
        }
        return progress >= 1;
    }

    private boolean hit(World world, Vector2 next) {
        float length = type.headLength() * GameConsts.UNIT_SCALE;
        float width = type.headWidth() * GameConsts.UNIT_SCALE;
        var sweep = new ArrowTipSweep(tip, direction, length, width, tip.dst(next));
        float pad = length + width / 2;
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
        world.QueryAABB(nearest, Math.min(tip.x, next.x) - pad, Math.min(tip.y, next.y) - pad,
            Math.max(tip.x, next.x) + pad, Math.max(tip.y, next.y) + pad);
        var health = nearest.target == null ? null : Health.MAPPER.get(nearest.target);
        if (health != null && health.isAlive()) { health.damage(damage); }
        return Float.isFinite(nearest.distance);
    }
}
