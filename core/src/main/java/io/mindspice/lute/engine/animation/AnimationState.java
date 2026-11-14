package io.mindspice.lute.engine.animation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.graphics.g2d.Animation;
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.AnimationPlayer;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Move;

public enum AnimationState implements State<Entity> {
    IDLE {
        @Override
        public void enter(Entity entity) {
            Animation2D.MAPPER.get(entity).setType(Animation2D.AnimationType.IDLE);
        }

        @Override
        public void update(Entity entity) {
            if (attacking(entity)) { changeState(entity, ATTACK); }
            else if (moving(entity)) { changeState(entity, WALK); }
        }
    },
    WALK {
        @Override
        public void enter(Entity entity) {
            Animation2D.MAPPER.get(entity).setType(Animation2D.AnimationType.WALK);
        }

        @Override
        public void update(Entity entity) {
            if (attacking(entity)) { changeState(entity, ATTACK); }
            else if (!moving(entity)) { changeState(entity, IDLE); }
        }
    },
    ATTACK {
        @Override
        public void enter(Entity entity) {
            var animation = Animation2D.MAPPER.get(entity);
            animation.setType(Animation2D.AnimationType.ATTACK);
            animation.setMode(Animation.PlayMode.NORMAL);
            animation.beginAttack(Combat.MAPPER.get(entity).getCurrentWeapon().attackSpeed());
        }

        @Override
        public void update(Entity entity) {
            if (!attacking(entity)) { changeState(entity, moving(entity) ? WALK : IDLE); }
        }

        @Override
        public void exit(Entity entity) {
            var animation = Animation2D.MAPPER.get(entity);
            animation.setMode(Animation.PlayMode.LOOP);
            animation.endAttack();
        }
    };

    private static boolean attacking(Entity entity) {
        var combat = Combat.MAPPER.get(entity);
        return combat != null && combat.isAttacking();
    }

    private static boolean moving(Entity entity) {
        var move = Move.MAPPER.get(entity);
        return move != null && !move.isRooted() && !move.getDirection().isZero();
    }

    private static void changeState(Entity entity, AnimationState state) {
        AnimationPlayer.MAPPER.get(entity).getAnimationFsm().changeState(state);
    }

    @Override public void exit(Entity entity) { }
    @Override public boolean onMessage(Entity entity, Telegram telegram) { return false; }
}
