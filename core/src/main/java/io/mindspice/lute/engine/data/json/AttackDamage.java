package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.JsonNode;

import java.util.Random;

/** Immutable damage definition retained by a committed attack or arrow until impact. */
public sealed interface AttackDamage {
    int roll(Random random, float scale);

    record Scalar(@JsonValue int base) implements AttackDamage {
        public Scalar {
            if (base <= 0) { throw new IllegalArgumentException("Weapon damage must be positive"); }
        }

        public int roll(Random random, float scale) {
            return Math.round(base * scale * (.9f + (1.1f - .9f) * random.nextFloat()));
        }
    }

    record Range(int min, int max) implements AttackDamage {
        public Range {
            if (min <= 0 || max < min) { throw new IllegalArgumentException("Weapon damage requires 0 < min <= max"); }
        }

        public int roll(Random random, float scale) {
            int fullDamage = min + random.nextInt(max - min + 1);
            return (int) Math.round(fullDamage * (double) scale);
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static AttackDamage parse(JsonNode node) {
        if (!node.isObject()) { return new Scalar(integer(node)); }
        if (node.size() != 2) { throw new IllegalArgumentException("Expected weapon damage {min, max}"); }
        return new Range(integer(node.required("min")), integer(node.required("max")));
    }

    private static int integer(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException("Expected whole-number weapon damage within int range");
        }
        return node.intValue();
    }
}
