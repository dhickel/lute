package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Move;

public class FacingSystem extends IteratingSystem {
    public FacingSystem() {
        super(Family.all(Facing.class, Move.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var combat = Combat.MAPPER.get(entity);
        if (combat != null && combat.isAttacking()) { return; }
        Move move = Move.MAPPER.get(entity);
        Vector2 moveDir = move.getDirection();
        if (moveDir.isZero()) { return; }

        Facing facing = Facing.MAPPER.get(entity);
        switch(moveDir) {
            case Vector2 v when v.y > 0f -> facing.setDirection(Facing.Direction.UP);
            case Vector2 v when v.y < 0f -> facing.setDirection(Facing.Direction.DOWN);
            case Vector2 v when v.x > 0f -> facing.setDirection(Facing.Direction.RIGHT);
            case Vector2 v when v.x < 0f -> facing.setDirection(Facing.Direction.LEFT);
            default -> Gdx.app.error("FacingSystem.ProcessEntity", "Invalid Move Direction");
        }
    }
}
