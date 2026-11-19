package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Fixture;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class Combat implements Component {
    public static final ComponentMapper<Combat> MAPPER = ComponentMapper.getFor(Combat.class);
    private AttackSlot attackRequested;
    private CombatWeapon.Attack activeAttack;
    private boolean attacking;
    private float cooldownRemaining;
    private Fixture sensor;
    private long attackId;
    private final Set<Entity> hitTargets = Collections.newSetFromMap(new IdentityHashMap<>());

    public boolean isAttacking() { return attacking; }
    public Fixture getSensor() { return sensor; }
    public long getAttackId() { return attackId; }

    public enum AttackSlot { PRIMARY, SECONDARY }

    public boolean hasAttackRequest() { return attackRequested != null; }
    public boolean isCoolingDown() { return cooldownRemaining > 0f; }

    public void tickCooldown(float deltaTime) {
        cooldownRemaining = Math.max(0f, cooldownRemaining - deltaTime);
    }
    public CombatWeapon.Attack getActiveAttack() { return activeAttack; }

    public void requestAttack(AttackSlot slot) {
        if (!isCoolingDown() && attackRequested == null) { attackRequested = slot; }
    }

    public AttackSlot consumeAttackRequest() {
        var requested = attackRequested;
        attackRequested = null;
        return requested;
    }

    public void beginAttack(CombatWeapon.Attack attack) {
        activeAttack = attack;
        cooldownRemaining = attack.cooldownSeconds();
        attacking = true;
        attackId++;
        hitTargets.clear();
    }

    public void setSensor(Fixture sensor) { this.sensor = sensor; }

    public void endAttack() {
        attacking = false;
        sensor = null;
        attackRequested = null;
        activeAttack = null;
        hitTargets.clear();
    }

    public boolean markHit(Entity target) { return hitTargets.add(target); }
}
