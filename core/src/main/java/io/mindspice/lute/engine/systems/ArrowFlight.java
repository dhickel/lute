package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.data.json.AttackDamage;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;
import io.mindspice.lute.engine.singletons.GameConsts;

final class ArrowFlight {
    private static final float MAX_LIFT_RATIO = .15f;

    final Entity owner;
    final Projectile type;
    final Vector2 tip;
    final Vector2 direction;
    Entity damagedVictim;
    private final Vector2 start, aimPoint, travel;
    private final AttackDamage damage;
    private final float drawScale;
    private final Facing.Direction launchFacing;
    private final double span, rise, bend, sweepStep, endProgress;
    private final Vector2[] vertices;
    private final float collisionPad;
    private double elapsed, progress;

    ArrowFlight(Entity owner, Projectile type, Vector2 start, Vector2 aimPoint, AttackDamage damage, float drawScale) {
        this.owner = owner;
        this.type = type;
        this.start = start;
        this.aimPoint = aimPoint;
        this.damage = damage;
        this.drawScale = drawScale;
        launchFacing = Facing.MAPPER.get(owner).getDirection();
        tip = start.cpy();
        float distance = start.dst(aimPoint);
        travel = distance == 0 ? switch (launchFacing) {
            case UP -> new Vector2(0, 1);
            case DOWN -> new Vector2(0, -1);
            case LEFT -> new Vector2(-1, 0);
            case RIGHT -> new Vector2(1, 0);
        } : aimPoint.cpy().sub(start);
        span = travel.len();
        double blend = northBlend(travel.x, travel.y);
        double lift = lift(type, distance * MAX_LIFT_RATIO, blend);
        rise = travel.y + 4 * lift;
        bend = 8 * lift;
        // Keep the parabola within a quarter sprite pixel of each collision chord.
        sweepStep = bend == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(2 * GameConsts.UNIT_SCALE / bend);
        direction = travel.cpy().nor();
        updateDirection(0);
        var bounds = type.collision().bounds();
        float length = (bounds.y2() - bounds.y1()) * GameConsts.UNIT_SCALE;
        float halfWidth = (bounds.x2() - bounds.x1()) * GameConsts.UNIT_SCALE / 2;
        vertices = CharacterFixtures.centeredVertices(type.collision());
        float pad = length + halfWidth;
        for (var vertex : vertices) {
            // Map up to the flight tangent, with the top midpoint of the bounds at the art tip.
            vertex.set(vertex.y - length / 2, -vertex.x);
            pad = Math.max(pad, vertex.len());
        }
        collisionPad = pad;
        // A near-north shot that hasn't cleared the launch strip ends at its apex.
        double apex = bend > 0 ? rise / bend : Double.POSITIVE_INFINITY;
        endProgress = blend > 0 && blend < 1 && Math.abs(travel.x) * apex < 32 * GameConsts.UNIT_SCALE + collisionPad
            ? apex : Double.POSITIVE_INFINITY;
    }

    private static double northBlend(double x, double y) {
        double angle = Math.toDegrees(Math.atan2(Math.abs(x), y));
        return Math.max(0, Math.min(1, (angle - 10) / 15));
    }

    private static double lift(Projectile type, double distanceLimit, double blend) {
        return Math.min(type.arcHeight(), distanceLimit) * blend * blend * (3 - 2 * blend);
    }

    // Find the first intercept after release. If none fits within the lifetime, aim directly.
    static Vector2 intercept(Projectile type, Vector2 start, Vector2 center, Vector2 velocity, float beforeRelease) {
        if (velocity.isZero() || center.equals(start)) { return center.cpy(); }
        double vx = velocity.x, vy = velocity.y;
        double x = (double) center.x - start.x + vx * beforeRelease;
        double y = (double) center.y - start.y + vy * beforeRelease;
        double distanceSquared = x * x + y * y;
        if (distanceSquared == 0) { return start.cpy(); }

        // The arc is at least as long as its chord. Solve |position + velocity*t| <= speed*t
        // first, so even an arbitrarily short crossing window is not lost to time sampling.
        double speed = type.speed(), high = type.lifetime();
        double a = vx * vx + vy * vy - speed * speed;
        double dot = x * vx + y * vy;
        double cross = x * vy - y * vx;
        double discriminant = speed * speed * distanceSquared - cross * cross;
        if (discriminant < 0 || (a >= 0 && dot >= 0)) { return center.cpy(); }
        double root = Math.sqrt(discriminant);
        // Use the equivalent root expression that doesn't subtract nearly equal terms.
        double low = dot <= 0 ? distanceSquared / (root - dot) : (-dot - root) / a;
        if (a > 0) { high = Math.min(high, (root - dot) / a); }
        if (low > high) { return center.cpy(); }

        double time = low;
        if (type.arcHeight() > 0 && northBlend(x + vx * low, y + vy * low) > 0) {
            // Arc length changes by at most 3 times aim displacement: the chord contributes
            // at most 1, and lift is < 1-Lipschitz (including the north taper) and contributes at most 2.
            double rate = speed + 3 * Math.hypot(vx, vy);
            time = firstReachable(type, x, y, vx, vy, low, high,
                reachGap(type, x, y, vx, vy, low), reachGap(type, x, y, vx, vy, high), rate);
        }
        if (Double.isNaN(time)) { return center.cpy(); }
        return new Vector2((float) (start.x + x + vx * time), (float) (start.y + y + vy * time));
    }

    private static double firstReachable(Projectile type, double x, double y, double vx, double vy,
                                         double low, double high, double lowGap, double highGap, double rate) {
        if (lowGap <= 0) { return low; }
        // Conservative lower bound from both ends; positive means the whole interval is unreachable.
        if (lowGap + highGap > rate * (high - low)) { return Double.NaN; }
        // Stop at about the precision of the returned float aim, not a fixed timestep.
        if (high - low <= 1e-7 * high) { return highGap <= 0 ? high : Double.NaN; }
        double mid = (low + high) / 2;
        double midGap = reachGap(type, x, y, vx, vy, mid);
        double first = firstReachable(type, x, y, vx, vy, low, mid, lowGap, midGap, rate);
        return Double.isNaN(first) ? firstReachable(type, x, y, vx, vy, mid, high, midGap, highGap, rate) : first;
    }

    private static double reachGap(Projectile type, double x, double y, double vx, double vy, double time) {
        x += vx * time;
        y += vy * time;
        double distance = Math.hypot(x, y);
        double lift = lift(type, distance * MAX_LIFT_RATIO, northBlend(x, y));
        return distanceAt(1, distance, x, y + 4 * lift, 8 * lift) - time * type.speed();
    }

    int boundsVertexCount() { return vertices.length; }

    void worldBoundsVertex(int index, Vector2 out) {
        var vertex = vertices[index];
        out.set(tip.x + vertex.x * direction.x - vertex.y * direction.y,
            tip.y + vertex.x * direction.y + vertex.y * direction.x);
    }

    boolean finished(World world, float dt) {
        if (dt <= 0) { return false; }
        elapsed = Math.min(elapsed + dt, type.lifetime());
        double end = Math.min(endProgress, progressAtDistance(elapsed * type.speed()));
        while (progress < end) {
            double nextProgress = Math.min(end, progress + sweepStep);
            // Preserve aim as an exact sweep boundary, not a trajectory change.
            if (progress < 1) { nextProgress = Math.min(1, nextProgress); }
            var next = nextProgress == 1 && !start.equals(aimPoint) ? aimPoint : new Vector2(
                (float) (start.x + travel.x * nextProgress),
                (float) (start.y + nextProgress * (rise - bend * nextProgress / 2)));
            if (advance(world, next)) { return true; }
            progress = nextProgress;
        }
        updateDirection(progress);
        return progress >= endProgress || elapsed >= type.lifetime();
    }

    private double progressAtDistance(double distance) {
        if (bend == 0) { return distance / span; }
        double low = progress;
        double high = Math.max(1, low * 2);
        while (distanceAt(high) < distance) { high *= 2; }
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (distanceAt(mid) < distance) { low = mid; }
            else { high = mid; }
        }
        return high;
    }

    private double distanceAt(double at) {
        return distanceAt(at, span, travel.x, rise, bend);
    }

    private static double distanceAt(double at, double span, double x, double rise, double bend) {
        double drop = bend * at;
        // For nearly straight intervals avoid subtracting almost equal antiderivative values.
        if (drop <= 1e-6 * span) { return at * Math.hypot(x, rise - drop / 2); }
        return (arcIntegral(x, rise) - arcIntegral(x, rise - drop)) / bend;
    }

    // Integral of hypot(horizontal, slope), with a separate exact vertical limit.
    private static double arcIntegral(double x, double slope) {
        x = Math.abs(x);
        double y = Math.abs(slope);
        if (x == 0) { return slope * y / 2; }
        double magnitude = Math.hypot(x, y);
        return Math.copySign((y * magnitude + x * x * Math.log((y + magnitude) / x)) / 2, slope);
    }

    private void updateDirection(double at) {
        float y = (float) (rise - bend * at);
        if (travel.x != 0 || y != 0) { direction.set(travel.x, y).nor(); }
    }

    private boolean advance(World world, Vector2 next) {
        if (next.equals(tip)) { return false; }
        direction.set(next).sub(tip).nor();
        if (hit(world, next)) { return true; }
        tip.set(next);
        return false;
    }

    private boolean hit(World world, Vector2 next) {
        var sweep = new ArrowTipSweep(tip, direction, vertices, tip.dst(next));
        class Nearest implements QueryCallback {
            float distance = Float.POSITIVE_INFINITY;
            Entity target;
            @Override public boolean reportFixture(Fixture fixture) {
                var body = fixture.getBody();
                if (body.getUserData() == owner) { return true; }
                if (body.getUserData() instanceof Entity entity) {
                    var health = Health.MAPPER.get(entity);
                    if (health != null && !health.isAlive()) { return true; }
                }
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
        if (health != null && health.isAlive()
            && HealthFeedback.land(nearest.target, health, damage, drawScale, launchFacing) > 0) {
            damagedVictim = nearest.target;
        }
        return Float.isFinite(nearest.distance);
    }
}
