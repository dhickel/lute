package io.mindspice.lute.engine.data.json;

import java.io.IOException;

/** Geometry is in pixels from the untrimmed sprite's lower left. Flight uses world units. */
public record ArrowData(String region, float speed, float arcHeight, float tipX, float tipY,
                        float headLength, float headWidth, float artDegrees) {
    public ArrowData {
        if (region == null || region.isBlank() || !positive(speed) || !positive(arcHeight)
            || !positive(headLength) || !positive(headWidth) || !Float.isFinite(artDegrees)
            || !Float.isFinite(tipX) || !Float.isFinite(tipY)) {
            throw new IllegalArgumentException("Invalid arrow flight or tip geometry");
        }
    }

    private static boolean positive(float n) { return n > 0 && Float.isFinite(n); }

    public static ArrowData loadDefault() {
        try (var input = ArrowData.class.getResourceAsStream("/arrows/default.json")) {
            if (input == null) { throw new IllegalStateException("Missing default arrow definition"); }
            return DataLoader.mapper.readValue(input, ArrowData.class);
        } catch (IOException e) { throw new IllegalStateException("Cannot load default arrow", e); }
    }
}
