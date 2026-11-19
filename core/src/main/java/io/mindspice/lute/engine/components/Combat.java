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
    private CombatWeapon.Attack activeAttack;
    private boolean attacking;
    private final Map<AttackKey, Float> cooldowns = new HashMap<>();
    private Fixture sensor;
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
    public CombatWeapon.Attack getActiveAttack() { return activeAttack; }

    public void requestAttack(AttackSlot slot) {
        if (attackRequested == null) { attackRequested = slot; }
    }

    public AttackSlot consumeAttackRequest() {
        var requested = attackRequested;
        attackRequested = null;
        return requested;
    }

    public void beginAttack(AttackSlot slot, CombatWeapon.Attack attack) {
        activeAttack = attack;
        cooldowns.put(new AttackKey(attack.weaponId(), slot), attack.cooldownSeconds());
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
