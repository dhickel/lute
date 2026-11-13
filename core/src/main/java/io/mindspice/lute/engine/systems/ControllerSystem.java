package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.mindspice.lute.engine.components.Controller;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;

public class ControllerSystem extends IteratingSystem implements EventEmitter {

    public ControllerSystem() {
        super(Family.all(Controller.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Controller controller = Controller.MAPPER.get(entity);
        if (!controller.getInputListener().hasInputs()) { return; }

        controller.getInputListener().consume((action, state) -> {
            if (state != 0 && state != 1) { return; }
            float direction = state == 1 ? 1f : -1f;
            switch (action) {
                case MOVE_UP -> moveEntity(entity, 0f, direction, state == 1);
                case MOVE_DOWN -> moveEntity(entity, 0f, -direction, state == 1);
                case MOVE_LEFT -> moveEntity(entity, -direction, 0f, state == 1);
                case MOVE_RIGHT -> moveEntity(entity, direction, 0f, state == 1);
            }
        });

    }

    private void moveEntity(Entity entity, float dirX, float dirY, boolean pressed) {
        Move move = Move.MAPPER.get(entity);
        if (move == null) { return; }

        move.getDirection().x += dirX;
        move.getDirection().y += dirY;
        if (pressed) { eventEmitter.accept(new Event.Player.Moved()); }

    }
}
