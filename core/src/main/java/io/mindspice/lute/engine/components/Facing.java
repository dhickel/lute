package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class Facing implements Component {
    public static final ComponentMapper<Facing> MAPPER = ComponentMapper.getFor(Facing.class);
    private Direction direction;

    public Facing(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public enum Direction {
        UP, DOWN, LEFT, RIGHT;
        public final String atlasKey;

        Direction() {
            atlasKey = name().toLowerCase();
        }
    }


}
