package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.mindspice.lute.engine.components.Health;

final class GameplayHud {
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();
    private final Vector2 point = new Vector2();

    void draw(Batch batch, Engine engine, Viewport viewport, Health player, int minute) {
        batch.setProjectionMatrix(projection.setToOrtho2D(0, 0, viewport.getScreenWidth(), viewport.getScreenHeight()));
        for (var victim : engine.getEntitiesFor(DamageFeedback.FAMILY)) {
            for (var hit : DamageFeedback.MAPPER.get(victim).numbers) {
                viewport.project(point.set(hit.x, hit.y));
                // Projection includes the letterbox offset; this batch draws inside the viewport.
                float x = point.x - viewport.getScreenX();
                float y = point.y - viewport.getScreenY() + font.getLineHeight();
                float t = hit.age / DamageFeedback.DURATION;
                // Rise even on a southbound hit; its outward component reduces the rise.
                y += 30 * t;
                switch (hit.facing) {
                    case LEFT -> x -= 35 * t;
                    case RIGHT -> x += 35 * t;
                    case UP -> y += 12 * t;
                    case DOWN -> y -= 12 * t;
                }
                drawText(batch, amount(hit.amount), x, y, Align.center,
                    Math.min(1, 2 - 2 * hit.remaining), Math.min(1, 2 * hit.remaining), 0, 1 - t);
            }
        }
        drawText(batch, label(player, minute), 10, viewport.getScreenHeight() - 8, Align.left, 1, 1, 1, 1);
    }

    static String label(Health health, int minute) {
        int hour = minute / 60, minutes = minute % 60;
        String time = (hour < 10 ? "0" : "") + hour + ":" + (minutes < 10 ? "0" : "") + minutes;
        return health == null ? time : "HP " + amount(health.getCurrent()) + "/" + amount(health.getMaximum()) + "    " + time;
    }

    private static String amount(float value) {
        double rounded = Math.round(value * 10d) / 10d;
        if (rounded == 0 && value > 0) { return "<0.1"; }
        return rounded == (long) rounded ? Long.toString((long) rounded) : Double.toString(rounded);
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
