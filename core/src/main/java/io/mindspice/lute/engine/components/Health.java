package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class Health implements Component {
    public static final ComponentMapper<Health> MAPPER = ComponentMapper.getFor(Health.class);
    private final float maximum;
    private float current;

    public Health(float maximum) {
        this.maximum = maximum;
        this.current = maximum;
    }

    public float getMaximum() { return maximum; }
    public float getCurrent() { return current; }
    public boolean isAlive() { return current > 0f; }

    public void damage(float amount) {
        current = Math.max(0f, current - amount);
    }
}
