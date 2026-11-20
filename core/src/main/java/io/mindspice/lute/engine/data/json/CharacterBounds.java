package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Axis-aligned bounds in bottom-left-origin local pixels. */
public record CharacterBounds(
    @JsonProperty(value = "x1", required = true) float x1,
    @JsonProperty(value = "y1", required = true) float y1,
    @JsonProperty(value = "x2", required = true) float x2,
    @JsonProperty(value = "y2", required = true) float y2
) {
    public CharacterBounds {
        if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)
            || x1 < 0 || y1 < 0 || x2 <= x1 || y2 <= y1) {
            throw new IllegalArgumentException("Bounds must be finite, nonnegative and have positive area");
        }
    }
}
