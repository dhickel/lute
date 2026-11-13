package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Fixture;
import io.mindspice.lute.engine.assets.CombatData;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

public class Combat implements Component {
    public static final ComponentMapper<Combat> MAPPER = ComponentMapper.getFor(Combat.class);
    private final CombatData.Weapon currentWeapon;
    private boolean attackRequested;
    private Fixture sensor;
    private long attackId;
    private float previousAnimationSpeed;
    private final Set<Entity> hitTargets = Collections.newSetFromMap(new IdentityHashMap<>());

    public Combat(CombatData.Weapon currentWeapon) {
        this.currentWeapon = Objects.requireNonNull(currentWeapon, "currentWeapon");
    }

    public CombatData.Weapon getCurrentWeapon() { return currentWeapon; }
    public boolean isAttacking() { return sensor != null; }
    public Fixture getSensor() { return sensor; }
    public long getAttackId() { return attackId; }
    public float getPreviousAnimationSpeed() { return previousAnimationSpeed; }

    public void requestAttack() {
        if (!isAttacking()) { attackRequested = true; }
    }

    public boolean consumeAttackRequest() {
        boolean requested = attackRequested;
        attackRequested = false;
        return requested;
    }

    public void beginAttack(Fixture sensor, float previousAnimationSpeed) {
        this.sensor = Objects.requireNonNull(sensor, "sensor");
        this.previousAnimationSpeed = previousAnimationSpeed;
        attackId++;
        hitTargets.clear();
    }

    public void endAttack() {
        sensor = null;
        attackRequested = false;
        hitTargets.clear();
    }

    public boolean markHit(Entity target) { return hitTargets.add(target); }
}
