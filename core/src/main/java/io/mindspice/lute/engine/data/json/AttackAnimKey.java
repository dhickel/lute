package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

public enum AttackAnimKey {
    SLASH("slash"), JAB("jab"), SHOT("shot"), LUNGE("slash"), TWIRL("jab");

    private final String atlasKey;

    AttackAnimKey(String atlasKey) { this.atlasKey = atlasKey; }

    public String atlasKey() { return atlasKey; }

    @JsonValue
    public String dataKey() { return name().toLowerCase(Locale.ROOT); }

    @JsonCreator
    public static AttackAnimKey fromJson(JsonNode value) {
        if (value.isTextual()) {
            for (var key : values()) {
                if (key.dataKey().equals(value.stringValue())) { return key; }
            }
        }
        throw new IllegalArgumentException("Unknown attack anim_key: " + value);
    }
}
