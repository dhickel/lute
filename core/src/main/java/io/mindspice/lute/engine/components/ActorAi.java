package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

public final class ActorAi implements Component {
    public static final ComponentMapper<ActorAi> MAPPER = ComponentMapper.getFor(ActorAi.class);

    private static final float INVESTIGATE_SECONDS = 2f;
    private static final float ARRIVAL_PIXELS = 8f;

    public enum Mode { WANDER, ENGAGE, INVESTIGATE }

    private final String behaviorGraphKey;
    private Mode mode = Mode.WANDER;
    private final Vector2 lastSeenFeet = new Vector2();
    private float investigateTimeLeft;
    private Entity retaliateAgainst;
    private Entity opportunityTarget;
    private CombatWeapon.Weapon opportunityWeapon;
    private Combat.AttackSlot opportunitySlot;
    private float decisionTimeLeft;
    private float recoveryLeft;
    // Start with a wait so the authored spawn facing is visible.
    private float wanderTimeLeft = wanderPhaseSeconds();

    private final float detectionRadius;
    private final int infightChance;

    public ActorAi(String behaviorGraphKey, float detectionRadius, int infightChance) {
        this.behaviorGraphKey = behaviorGraphKey;
        this.detectionRadius = detectionRadius;
        this.infightChance = infightChance;
    }

    public float detectionRadius() { return detectionRadius; }
    public int infightChance() { return infightChance; }

    public String behaviorGraphKey() { return behaviorGraphKey; }
    public Mode mode() { return mode; }

    /** Start the clock only on entry: time before eligibility does not count. */
    public boolean decisionDue(Entity target, CombatWeapon.Weapon weapon, Combat.AttackSlot slot, float delta,
                               DoubleSupplier delaySeconds) {
        if (target == null) { clearOpportunity(); return false; }
        if (target != opportunityTarget || weapon != opportunityWeapon || slot != opportunitySlot) {
            opportunityTarget = target;
            opportunityWeapon = weapon;
            opportunitySlot = slot;
            decisionTimeLeft = (float) delaySeconds.getAsDouble();
        } else {
            decisionTimeLeft -= delta;
        }
        return decisionTimeLeft <= 0f;
    }

    public void clearOpportunity() {
        opportunityTarget = null;
        opportunityWeapon = null;
        opportunitySlot = null;
    }

    public void recover(float seconds) { recoveryLeft = seconds; }
    public void tickRecovery(float delta) { recoveryLeft = Math.max(0f, recoveryLeft - delta); }
    public boolean recovering() { return recoveryLeft > 0f; }

    public Entity retaliationTarget() { return retaliateAgainst; }

    public void retaliateAgainst(Entity shooter) { retaliateAgainst = shooter; }

    /** Update the mode from live sight and return a steering offset, if any. */
    public Vector2 updateTarget(Vector2 feet, Vector2 visibleFeet, float deltaTime) {
        if (visibleFeet != null) {
            lastSeenFeet.set(visibleFeet);
            mode = Mode.ENGAGE;
            return visibleFeet.cpy().sub(feet);
        }
        clearOpportunity();
        if (mode == Mode.WANDER) { return null; }
        if (mode == Mode.ENGAGE) {
            mode = Mode.INVESTIGATE;
            investigateTimeLeft = INVESTIGATE_SECONDS;
        } else {
            investigateTimeLeft -= deltaTime;
        }
        float arrival = ARRIVAL_PIXELS * GameConsts.UNIT_SCALE;
        if (investigateTimeLeft <= 0f || lastSeenFeet.dst2(feet) <= arrival * arrival) {
            forgetTarget();
            resetWander(0f);
            return null;
        }
        return lastSeenFeet.cpy().sub(feet);
    }

    public void forgetTarget() {
        clearOpportunity();
        mode = Mode.WANDER;
        retaliateAgainst = null;
        lastSeenFeet.setZero();
        investigateTimeLeft = 0f;
    }

    public boolean advanceWander(float deltaTime) {
        wanderTimeLeft -= deltaTime;
        return wanderTimeLeft <= 0f;
    }

    public void resetWander(float duration) { wanderTimeLeft = duration; }

    public static float wanderPhaseSeconds() { return ThreadLocalRandom.current().nextFloat(1f, 3f); }
}
