package io.mindspice.lute.engine.systems;

import io.mindspice.lute.engine.data.json.AttackDamage.Scalar;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;

/** Access to map-owned combat feedback for the desktop integration tests. */
public final class DamageFeedbackProbe {
    private DamageFeedbackProbe() { }

    public static void hit(Entity victim, int base, Facing.Direction facing) {
        HealthFeedback.land(victim, Health.MAPPER.get(victim), new Scalar(base), 1, facing);
    }

    public static boolean heal(Entity player, int amount) {
        return HealthFeedback.healPlayer(player, amount);
    }

    public static int count(Entity victim) {
        var feedback = HealthFeedback.MAPPER.get(victim);
        return feedback == null ? 0 : feedback.numbers.size();
    }
}
