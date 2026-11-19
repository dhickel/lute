package io.mindspice.lute.engine.ai.decisiongraph;


import com.badlogic.ashley.core.Entity;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface Decision {

    boolean eval(Entity entity, FocusState state);



    default Decision pctChance(int chancePct) {
        return   (_, _) -> {
            int chance = ThreadLocalRandom.current().nextInt(0, 100);
            return (chance < chancePct);
        };
    }
}
