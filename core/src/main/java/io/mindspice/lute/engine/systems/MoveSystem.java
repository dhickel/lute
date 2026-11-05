package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.components.Transform;

public class MoveSystem extends IteratingSystem {
    private final Vector2 normalizedDir = new Vector2();

    public MoveSystem() {
        super(Family.all(Move.class, Transform.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Move move = Move.MAPPER.get(entity);
        if (move.isRooted() || move.getDirection().isZero()) { return; }

        normalizedDir.set(move.getDirection()).nor();
        Transform transform = Transform.MAPPER.get(entity);
        Vector2 position = transform.getPosition();
        position.set(
            position.x + move.getMaxSpeed() * normalizedDir.x * deltaTime,
            position.y + move.getMaxSpeed() * normalizedDir.y * deltaTime
        );
    }
}
