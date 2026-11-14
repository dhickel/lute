package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Fixture;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

public class Combat implements Component {
    public static final ComponentMapper<Combat> MAPPER = ComponentMapper.getFor(Combat.class);
    private final CombatWeapon.Weapon currentWeapon;
    private boolean attackRequested;
    private boolean attacking;
    private double attackElapsed;
    private Fixture sensor;
    private long attackId;
    private final Set<Entity> hitTargets = Collections.newSetFromMap(new IdentityHashMap<>());

    public Combat(CombatWeapon.Weapon currentWeapon) {
        this.currentWeapon = Objects.requireNonNull(currentWeapon, "currentWeapon");
    }

    public CombatWeapon.Weapon getCurrentWeapon() { return currentWeapon; }
    public boolean isAttacking() { return attacking; }
    public Fixture getSensor() { return sensor; }
    public long getAttackId() { return attackId; }

    public void requestAttack() {
        if (!isAttacking()) { attackRequested = true; }
    }

    public boolean consumeAttackRequest() {
        boolean requested = attackRequested;
        attackRequested = false;
        return requested;
    }

    public void beginAttack(Fixture sensor) {
        this.sensor = Objects.requireNonNull(sensor, "sensor");
        attacking = true;
        attackElapsed = 0;
        attackId++;
        hitTargets.clear();
    }

    public boolean advanceAttack(float deltaTime) {
        attackElapsed += deltaTime;
        return attackElapsed >= currentWeapon.attackDuration();
    }

    public void endAttack() {
        attacking = false;
        attackElapsed = 0;
        sensor = null;
        attackRequested = false;
        hitTargets.clear();
    }

    public boolean markHit(Entity target) { return hitTargets.add(target); }
}
