package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.mindspice.lute.engine.animation.AnimationState;
import io.mindspice.lute.engine.components.AnimationPlayer;

public class AnimationPlayerSystem extends IteratingSystem {

    public AnimationPlayerSystem() {
        super(Family.all(AnimationPlayer.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AnimationPlayer.MAPPER.get(entity).getAnimationFsm().update();
    }
}
