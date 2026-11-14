package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Transform;

public class PhysicMoveSystem extends IteratingSystem {
    private final Vector2 normalizedDir = new Vector2();

    public PhysicMoveSystem() {
        super(Family.all(Physic.class, Move.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Move move = Move.MAPPER.get(entity);
        Body body = Physic.MAPPER.get(entity).getBody();
        var combat = Combat.MAPPER.get(entity);
        if (move.isRooted() || move.getDirection().isZero() || (combat != null && combat.isAttacking())) {
            body.setLinearVelocity(Vector2.Zero);
            return;
        }

        normalizedDir.set(move.getDirection()).nor();
        body.setLinearVelocity( //TODO use force/impulse
            move.getMaxSpeed() * normalizedDir.x,
            move.getMaxSpeed() * normalizedDir.y
        );
    }
}
