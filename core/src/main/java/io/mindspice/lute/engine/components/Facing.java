package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.Vector2;

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

    public void faceAlong(Vector2 offset) {
        if (offset.isZero()) { return; }
        direction = Math.abs(offset.x) > Math.abs(offset.y)
            ? (offset.x > 0f ? Direction.RIGHT : Direction.LEFT)
            : (offset.y > 0f ? Direction.UP : Direction.DOWN);
    }

    public enum Direction {
        UP, DOWN, LEFT, RIGHT;
        public final String atlasKey;

        Direction() {
            atlasKey = name().toLowerCase();
        }
    }


}
