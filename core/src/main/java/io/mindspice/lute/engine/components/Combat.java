package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Fixture;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class Combat implements Component {
    public static final ComponentMapper<Combat> MAPPER = ComponentMapper.getFor(Combat.class);
    private AttackSlot attackRequested;
    private Entity requestedTarget;
    private boolean deferredRequest;
    private CombatWeapon.Attack activeAttack;
    private Facing.Direction attackFacing;
    private boolean attacking;
    private final Map<AttackKey, Float> cooldowns = new HashMap<>();
    private AttackKey activeKey;
    private Fixture sensor;
    private long sensorCreatedAtStep;
    private long attackId;
    private final Set<Entity> hitTargets = Collections.newSetFromMap(new IdentityHashMap<>());

    public boolean isAttacking() { return attacking; }
    public Fixture getSensor() { return sensor; }
    public long getAttackId() { return attackId; }

    public enum AttackSlot { PRIMARY, SECONDARY }
    private record AttackKey(String weaponId, AttackSlot slot) { }

    public boolean hasAttackRequest() { return attackRequested != null; }
    public boolean isCoolingDown(String weaponId, AttackSlot slot) {
        return cooldowns.containsKey(new AttackKey(weaponId, slot));
    }

    public void tickCooldown(float deltaTime) {
        var entries = cooldowns.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            float remaining = entry.getValue() - deltaTime;
            if (remaining <= 0f) { entries.remove(); }
            else { entry.setValue(remaining); }
        }
    }
    public Facing.Direction getAttackFacing() { return attackFacing; }
    public void setAttackFacing(Facing.Direction facing) { attackFacing = facing; }
    public CombatWeapon.Attack getActiveAttack() { return activeAttack; }

    public void requestAttack(AttackSlot slot) { requestAttack(slot, null); }

    public void requestAttack(AttackSlot slot, Entity target) {
        if (attackRequested != null) { return; }
        attackRequested = slot;
        requestedTarget = target;
    }

    public void deferRequest() { deferredRequest = hasAttackRequest(); }
    public boolean hasDeferredRequest() { return deferredRequest; }
    public AttackSlot requestedSlot() { return attackRequested; }
    public Entity requestedTarget() { return requestedTarget; }

    public AttackSlot consumeAttackRequest() {
        var requested = attackRequested;
        attackRequested = null;
        deferredRequest = false;
        requestedTarget = null;
        return requested;
    }

    public void beginAttack(AttackSlot slot, CombatWeapon.Attack attack, Facing.Direction facing) {
        activeAttack = attack;
        attackFacing = facing;
        activeKey = new AttackKey(attack.weaponId(), slot);
        cooldowns.put(activeKey, attack.cooldownSeconds());
        attacking = true;
        attackId++;
        hitTargets.clear();
    }

    public boolean activeAttackCoolingDown() { return cooldowns.containsKey(activeKey); }
    public void setSensor(Fixture sensor, long step) {
        this.sensor = sensor;
        sensorCreatedAtStep = step;
    }
    public boolean sensorHasStepped(long step) { return sensor != null && step > sensorCreatedAtStep; }

    public void endAttack() {
        attacking = false;
        sensor = null;
        activeKey = null;
        activeAttack = null;
        attackFacing = null;
        hitTargets.clear();
    }

    public boolean markHit(Entity target) { return hitTargets.add(target); }
}
