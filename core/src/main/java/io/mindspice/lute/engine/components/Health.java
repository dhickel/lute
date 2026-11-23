package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class Health implements Component {
    public static final ComponentMapper<Health> MAPPER = ComponentMapper.getFor(Health.class);
    private final int maximum;
    private int current;

    public Health(int maximum) {
        this.maximum = maximum;
        this.current = maximum;
    }

    public int getMaximum() { return maximum; }
    public int getCurrent() { return current; }
    public float remainingRatio() { return (float) current / maximum; }
    public boolean isAlive() { return current > 0; }

    public void restore() { current = maximum; }

    public void damage(int amount) {
        current = Math.max(0, current - amount);
    }
}
