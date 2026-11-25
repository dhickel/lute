package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.gameplay.HoardRun;

final class GameplayHud {
    private static final int BAR_HEIGHT = 28;
    private static final int LABEL_GAP = 16;
    private static final int MIN_LABEL_GAP = 8;
    private static final int SIDE_INSET = 4;

    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();
    private final Vector2 point = new Vector2();

    private final TextureRegion white;
    private final GlyphLayout measure = new GlyphLayout();

    GameplayHud(TextureRegion white) {
        this.white = white;
    }

    void draw(Batch batch, Engine engine, Viewport viewport, Health player, int minute, HoardRun run) {
        batch.setProjectionMatrix(projection.setToOrtho2D(0, 0, viewport.getScreenWidth(), viewport.getScreenHeight()));
        for (var entity : engine.getEntitiesFor(HealthFeedback.FAMILY)) {
            for (var number : HealthFeedback.MAPPER.get(entity).numbers) {
                viewport.project(point.set(number.x, number.y));
                // Projection includes the letterbox offset; this batch draws inside the viewport.
                float x = point.x - viewport.getScreenX();
                float t = number.age / HealthFeedback.DURATION;
                float scale = number.isHeal() ? 2 : 1;
                float y = point.y - viewport.getScreenY() + font.getLineHeight() * scale;
                y += 30 * t;
                if (number.isHeal()) {
                    font.getData().setScale(scale);
                    try {
                        drawText(batch, "+" + number.amount + " HP", x, y, Align.center, 0, 1, 0, 1 - t);
                    } finally {
                        font.getData().setScale(1);
                    }
                } else {
                    // Rise even on a southbound hit; its outward component reduces the rise.
                    switch (number.facing) {
                        case LEFT -> x -= 35 * t;
                        case RIGHT -> x += 35 * t;
                        case UP -> y += 12 * t;
                        case DOWN -> y -= 12 * t;
                    }
                    drawHealthText(batch, Integer.toString(number.amount), x, y, Align.center, number.remaining, 1 - t);
                }
            }
        }
        drawBar(batch, viewport.getScreenWidth(), viewport.getScreenHeight(), player, minute, run);
    }

    private void drawBar(Batch batch, int width, int top, Health player, int minute, HoardRun run) {
        int bottom = top - BAR_HEIGHT;
        batch.setColor(.10f, .10f, .10f, 1);
        batch.draw(white, 0, bottom, width, BAR_HEIGHT);
        batch.setColor(.14f, .14f, .14f, 1);
        batch.draw(white, 0, bottom + 15, width, 13);

        String time = statusText(minute, run);
        measure.setText(font, time);
        float timeWidth = measure.width;
        String hp = player == null ? null : healthText(player);
        float hpWidth = 0;
        if (hp != null) {
            measure.setText(font, hp);
            hpWidth = measure.width;
        }

        // Keep native-size glyphs if a tighter gap is enough; otherwise shrink the entire group, not just one label.
        float available = width - 2 * Math.min(SIDE_INSET, width / 4f);
        float gap = hp == null ? 0 : Math.clamp(available - hpWidth - timeWidth, MIN_LABEL_GAP, LABEL_GAP);
        float groupWidth = hpWidth + gap + timeWidth;
        float scale = Math.min(1, available / groupWidth);
        float x = (width - groupWidth * scale) / 2;
        if (scale < 1) {
            font.getData().setScale(scale);
            font.setUseIntegerPositions(false);
        } else {
            x = Math.round(x);
        }
        try {
            float y = Math.round(bottom + (BAR_HEIGHT + font.getCapHeight()) / 2);
            if (hp != null) {
                drawHealthText(batch, hp, x, y, Align.left, player.remainingRatio(), 1);
                x += (hpWidth + gap) * scale;
            }
            batch.setColor(Color.WHITE);
            drawText(batch, time, x, y, Align.left, .61f, .79f, .86f, 1);
        } finally {
            if (scale < 1) {
                font.getData().setScale(1);
                font.setUseIntegerPositions(true);
            }
        }
    }

    static String statusText(int minute, HoardRun run) {
        if (run == null) { return clockText(minute); }
        if (run.isGameOver()) {
            return "Game Over   Score " + run.score() + "   Round " + run.round() + "   Alive " + run.aliveSeconds() + "s";
        }
        int left = run.remainingSeconds();
        return "Round " + run.round()
            + "   " + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60
            + " left   Kills " + run.kills() + "   Score " + run.score();
    }

    static String healthText(Health health) {
        return "HP " + health.getCurrent() + "/" + health.getMaximum();
    }

    static String clockText(int minute) {
        int hour = minute / 60, minutes = minute % 60;
        return (hour < 10 ? "0" : "") + hour + ":" + (minutes < 10 ? "0" : "") + minutes;
    }

    private void drawHealthText(Batch batch, String text, float x, float y, int align, float ratio, float alpha) {
        drawText(batch, text, x, y, align, Math.min(1, 2 - 2 * ratio), Math.min(1, 2 * ratio), 0, alpha);
    }

    private void drawText(Batch batch, String text, float x, float y, int align, float r, float g, float b, float alpha) {
        font.setColor(0, 0, 0, alpha);
        font.draw(batch, text, x + 1, y - 1, 0, align, false);
        font.setColor(r, g, b, alpha);
        font.draw(batch, text, x, y, 0, align, false);
    }

    void dispose(RenderState state) {
        state.releasedTexture(font.getRegion().getTexture().getTextureObjectHandle());
        font.dispose();
    }
}
