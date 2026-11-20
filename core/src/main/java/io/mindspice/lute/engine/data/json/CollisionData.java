package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Rotation is counterclockwise in degrees around the center of the unrotated bounds. */
public record CollisionData(
    @JsonProperty(required = true) Shape shape,
    @JsonProperty(required = true) CharacterBounds bounds,
    @JsonProperty(required = true) Float rotation
) {
    public enum Shape { ELLIPSE, RECT, CIRCLE, TRIANGLE }

    public CollisionData {
        if (shape == null || bounds == null || rotation == null || !Float.isFinite(rotation)) {
            throw new IllegalArgumentException("Collision requires a shape, bounds, and finite rotation");
        }
        if (shape == Shape.CIRCLE && bounds.x2() - bounds.x1() != bounds.y2() - bounds.y1()) {
            throw new IllegalArgumentException("CIRCLE requires equal bounds width and height");
        }
    }

    public static CollisionData ellipse(CharacterBounds bounds) {
        return new CollisionData(Shape.ELLIPSE, bounds, 0f);
    }
}
