package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;

public class ControllerSystem extends IteratingSystem implements EventEmitter {
    public ControllerSystem() {
        super(Family.all(Controller.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var controller = Controller.MAPPER.get(entity);
        var listener = controller.getInputListener();
        var aim = BowAim.MAPPER.get(entity);
        var move = Move.MAPPER.get(entity);
        var loadout = Loadout.MAPPER.get(entity);
        var health = Health.MAPPER.get(entity);
        boolean alive = health == null || health.isAlive();
        if (aim != null && (!alive || loadout == null || aim.getWeapon() != loadout.getWeapon())) { aim.cancel(); }
        if (listener.consumeInvalidation()) {
            if (aim != null) { aim.cancel(); }
            listener.getQueue().consumePreviousContext((action, state) -> {
                if (action == InputAction.ATTACK_PRIMARY) {
                    if (state == 0 || state == 1) { controller.primaryEdge(state == 1); }
                } else { movement(controller, action, state); }
            });
            if (listener.consumeDetach()) { controller.resetInput(); }
        }
        var combat = Combat.MAPPER.get(entity);
        listener.consume((action, state) -> {
            if (state != 0 && state != 1) { return; }
            boolean pressed = state == 1;
            var weapon = loadout == null ? null : loadout.getWeapon();
            if (action == InputAction.ATTACK_PRIMARY) {
                boolean edge = controller.primaryEdge(pressed);
                if (aim != null && weapon != null && weapon.primary().collisionType() == CollisionType.PROJECTILE) {
                    if (!edge || !alive) { return; }
                    if (pressed) {
                        getEngine().getSystem(CombatSystem.class).drawBow(entity, aim, weapon);
                    } else if (aim.isAiming()) {
                        getEngine().getSystem(CombatSystem.class).launch(entity, aim);
                    }
                } else if (pressed) { requestAttack(combat, weapon, Combat.AttackSlot.PRIMARY); }
            } else if (action == InputAction.ATTACK_SECONDARY) {
                if (pressed && (aim == null || !aim.isAiming())) {
                    requestAttack(combat, weapon, Combat.AttackSlot.SECONDARY);
                }
            } else if (action == InputAction.CYCLE_WEAPON) {
                if (pressed && alive && weapon != null && (aim == null || !aim.isAiming())) {
                    var cycle = controller.getWeaponCycle();
                    if (!cycle.isEmpty()) {
                        int next = (cycle.indexOf(weapon.id()) + 1) % cycle.size();
                        getEngine().getSystem(LoadoutSystem.class).equipWeapon(entity, cycle.get(next));
                    }
                }
            } else { movement(controller, action, state); }
        });
        if (aim != null && aim.isAiming()) {
            aim.steer(controller.getHeldDirection(), deltaTime);
            if (move != null) { move.getDirection().setZero(); }
        } else if (move != null) { move.getDirection().set(controller.getHeldDirection()); }
    }

    private void requestAttack(Combat combat, CombatWeapon.Weapon weapon, Combat.AttackSlot slot) {
        if (combat != null && (weapon == null || !combat.isCoolingDown(weapon.id(), slot))) {
            combat.requestAttack(slot);
        }
    }

    private void movement(Controller controller, InputAction action, int state) {
        if (state != 0 && state != 1) { return; }
        int step = state == 1 ? 1 : -1;
        var direction = controller.getHeldDirection();
        switch (action) {
            case MOVE_UP -> direction.y += step;
            case MOVE_DOWN -> direction.y -= step;
            case MOVE_LEFT -> direction.x -= step;
            case MOVE_RIGHT -> direction.x += step;
        }
    }
}
