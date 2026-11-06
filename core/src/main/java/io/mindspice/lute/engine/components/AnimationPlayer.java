package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import io.mindspice.lute.engine.animation.AnimationState;

public class AnimationPlayer implements Component {
    public static final ComponentMapper<AnimationPlayer> MAPPER = ComponentMapper.getFor(AnimationPlayer.class);

    private final DefaultStateMachine<Entity, AnimationState> animationFsm;


    public AnimationPlayer(Entity owner) {
        this.animationFsm = new DefaultStateMachine<Entity, AnimationState>(owner, AnimationState.IDLE);
    }

    public DefaultStateMachine<Entity, AnimationState> getAnimationFsm() {
        return animationFsm;
    }
}
