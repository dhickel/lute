package io.mindspice.lute.engine.animation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.AnimationPlayer;
import io.mindspice.lute.engine.components.Move;

public enum AnimationState implements State<Entity> {
    IDLE {
        @Override
        public void enter(Entity entity) {
            Animation2D.MAPPER.get(entity).setType(Animation2D.AnimationType.IDLE);
        }

        @Override
        public void update(Entity entity) {
            Move move = Move.MAPPER.get(entity);
            if (move != null && !move.isRooted() && !move.getDirection().isZero()) {
                AnimationPlayer.MAPPER.get(entity).getAnimationFsm().changeState(WALK);
            }
        }

        @Override
        public void exit(Entity entity) {

        }

        @Override
        public boolean onMessage(Entity entity, Telegram telegram) {
            return false;
        }
    },
    WALK {
        @Override
        public void enter(Entity entity) {
            Animation2D.MAPPER.get(entity).setType(Animation2D.AnimationType.WALK);

        }

        @Override
        public void update(Entity entity) {
            Move move = Move.MAPPER.get(entity);
            if(move == null || move.getDirection().isZero() || move.isRooted()) {
                AnimationPlayer.MAPPER.get(entity).getAnimationFsm().changeState(IDLE);
            }

        }

        @Override
        public void exit(Entity entity) {

        }

        @Override
        public boolean onMessage(Entity entity, Telegram telegram) {
            return false;
        }
    }
}
