package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.mindspice.lute.engine.components.Combat;

/** Runs before input so both attack slots see the cooldown expire on the same update. */
public class AttackCooldownSystem extends IteratingSystem {
    public AttackCooldownSystem() {
        super(Family.all(Combat.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Combat.MAPPER.get(entity).tickCooldown(deltaTime);
    }
}
