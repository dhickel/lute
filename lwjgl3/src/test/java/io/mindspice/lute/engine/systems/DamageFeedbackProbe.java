package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;

/** Access to map-owned combat feedback for the desktop integration tests. */
public final class DamageFeedbackProbe {
    private DamageFeedbackProbe() { }

    public static void hit(Entity victim, float base, Facing.Direction facing) {
        DamageFeedback.land(victim, Health.MAPPER.get(victim), base, facing);
    }

    public static int count(Entity victim) {
        var feedback = DamageFeedback.MAPPER.get(victim);
        return feedback == null ? 0 : feedback.numbers.size();
    }
}
