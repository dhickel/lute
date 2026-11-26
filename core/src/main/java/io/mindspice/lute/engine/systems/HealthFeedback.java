package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.components.Facing.Direction;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.AttackDamage;

import java.util.ArrayList;
import java.util.List;

/** Transient, map-owned numbers anchored where the HP change happened. */
final class HealthFeedback implements Component {
    static final Family FAMILY = Family.all(HealthFeedback.class).get();
    static final ComponentMapper<HealthFeedback> MAPPER = ComponentMapper.getFor(HealthFeedback.class);
    static final float DURATION = .65f;
    final List<Number> numbers = new ArrayList<>();

    static int land(Entity target, Health health, AttackDamage damage, float scale, Direction facing) {
        int before = health.getCurrent();
        health.damage(damage.roll(MathUtils.random, scale));
        int taken = before - health.getCurrent();
        var transform = Transform.MAPPER.get(target);
        if (taken <= 0 || transform == null) { return taken; }
        add(target, taken, health.remainingRatio(), facing, transform.getPosition(), transform.getSize());
        return taken;
    }

    /** Use for gameplay heals; full-health spawn and reset deliberately bypass feedback. */
    static boolean healPlayer(Entity player, int amount) {
        var health = Health.MAPPER.get(player);
        int before = health.getCurrent();
        if (!health.heal(amount)) { return false; }
        var transform = Transform.MAPPER.get(player);
        if (transform != null) {
            // Pickups resolve inside the physics tick, before the render transform is interpolated.
            var physic = Physic.MAPPER.get(player);
            var position = physic == null ? transform.getPosition() : physic.getBody().getPosition();
            add(player, health.getCurrent() - before, health.remainingRatio(), null, position, transform.getSize());
        }
        return true;
    }

    private static void add(Entity target, int amount, float remaining, Direction facing, Vector2 position, Vector2 size) {
        var feedback = MAPPER.get(target);
        if (feedback == null) { feedback = new HealthFeedback(); target.add(feedback); }
        feedback.numbers.add(new Number(amount, remaining, facing, position.x + size.x / 2, position.y + size.y));
    }

    void advance(float dt) {
        numbers.removeIf(number -> (number.age += dt) >= DURATION);
    }

    static final class Number {
        final int amount;
        final float remaining, x, y;
        final Direction facing; // Healing rises straight up, without a hit facing.
        float age;

        boolean isHeal() { return facing == null; }

        Number(int amount, float remaining, Direction facing, float x, float y) {
            this.amount = amount;
            this.remaining = remaining;
            this.facing = facing;
            this.x = x;
            this.y = y;
        }
    }
}
