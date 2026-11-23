package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import io.mindspice.lute.engine.components.Facing.Direction;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Transform;

import java.util.ArrayList;
import java.util.List;

/** Transient, map-owned numbers. Anchors stay at the hit position even if the victim moves. */
final class DamageFeedback implements Component {
    static final Family FAMILY = Family.all(DamageFeedback.class).get();
    static final ComponentMapper<DamageFeedback> MAPPER = ComponentMapper.getFor(DamageFeedback.class);
    static final float DURATION = .65f;
    final List<Number> numbers = new ArrayList<>();

    static void land(Entity target, Health health, float base, Direction facing) {
        float before = health.getCurrent();
        health.damage(base * MathUtils.random(.9f, 1.1f));
        float taken = before - health.getCurrent();
        var transform = Transform.MAPPER.get(target);
        if (taken <= 0 || transform == null) { return; }
        var feedback = MAPPER.get(target);
        if (feedback == null) { feedback = new DamageFeedback(); target.add(feedback); }
        var position = transform.getPosition();
        var size = transform.getSize();
        feedback.numbers.add(new Number(taken, health.getCurrent() / health.getMaximum(), facing,
            position.x + size.x / 2, position.y + size.y));
    }

    void advance(float dt) {
        numbers.removeIf(number -> (number.age += dt) >= DURATION);
    }

    static final class Number {
        final float amount, remaining, x, y;
        final Direction facing;
        float age;

        Number(float amount, float remaining, Direction facing, float x, float y) {
            this.amount = amount;
            this.remaining = remaining;
            this.facing = facing;
            this.x = x;
            this.y = y;
        }
    }
}
