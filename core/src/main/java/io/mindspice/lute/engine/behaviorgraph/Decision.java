package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface Decision<C> {
    boolean eval(Entity self, C context);

    static <C> Decision<C> pctChance(int chancePct) {
        return (_, _) -> ThreadLocalRandom.current().nextInt(100) < chancePct;
    }
}
