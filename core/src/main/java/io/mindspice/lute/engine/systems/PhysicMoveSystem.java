package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Controller;

public class PhysicMoveSystem extends IteratingSystem {
    private final Vector2 normalizedDir = new Vector2();

    public PhysicMoveSystem() {
        super(Family.all(Physic.class, Move.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        applyMovement(entity, 1f);
    }

    void applyMovement(Entity entity, float speedMultiplier) {
        Move move = Move.MAPPER.get(entity);
        Body body = Physic.MAPPER.get(entity).getBody();
        if (move.isRooted() || move.getDirection().isZero()
            || (!Controller.MAPPER.has(entity) && BehaviorGraphs.attackLocksMovement(entity))) {
            body.setLinearVelocity(Vector2.Zero);
            return;
        }

        normalizedDir.set(move.getDirection()).nor();
        float speed = move.getMaxSpeed() * speedMultiplier;
        body.setLinearVelocity(speed * normalizedDir.x, speed * normalizedDir.y);
    }
}
