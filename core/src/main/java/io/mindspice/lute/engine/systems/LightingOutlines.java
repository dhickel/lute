package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import io.mindspice.lute.engine.components.Emissive;
import io.mindspice.lute.engine.components.Transform;

/** Diagnostic strokes use the world batch so they share its camera, target and clipping. */
final class LightingOutlines extends FixtureOutlines {
    private final Batch batch;
    private final TextureRegion white;
    private final Array<Body> bodies = new Array<>();
    private double viewX, viewY, viewRadius, pixelSize;
    private float stroke;

    LightingOutlines(Batch batch, TextureRegion white) {
        this.batch = batch;
        this.white = white;
    }

    void draw(World world, Iterable<Entity> emitters, double seconds, OrthographicCamera camera, int viewportHeight) {
        // Wider than the existing single-pixel physics lines drawn later, without relying on GL wide-line support.
        pixelSize = (double)camera.zoom * camera.viewportHeight / viewportHeight;
        stroke = (float)(3 * pixelSize);
        viewX = camera.position.x;
        viewY = camera.position.y;
        viewRadius = .5 * camera.zoom * Math.hypot(camera.viewportWidth, camera.viewportHeight) + stroke;
        batch.setColor(1f, .375f, .125f, 1f);
        world.getBodies(bodies);
        try {
            for (var body : bodies) {
                if (!body.isActive()) { continue; }
                for (var fixture : body.getFixtureList()) {
                    if (!fixture.isSensor()) { drawFixture(fixture); }
                }
            }
        } finally { bodies.clear(); } // Do not retain bodies across map retirement.

        batch.setColor(.5f, 1f, .125f, 1f);
        for (var entity : emitters) {
            var light = Emissive.MAPPER.get(entity);
            var transform = Transform.MAPPER.get(entity);
            circle(transform.centerX(), transform.centerY(), light.radius(light.wave(seconds)));
        }
    }

    @Override void circle(float x, float y, float radius) {
        double dx = viewX - x, dy = viewY - y;
        double distance = Math.hypot(dx, dy);
        double gap = distanceToRim(x, y, radius, distance);
        if (Math.abs(gap) > viewRadius || radius == 0) { return; }

        // Seen from the circle center, the padded viewport fits inside this cone.
        // If the center is inside the view disk, the whole circumference may be visible.
        double halfAngle = distance <= viewRadius ? Math.PI : Math.asin(viewRadius / distance);
        // Chord error <= radius * step² / 8. Also keep long strokes short enough for Batch's float rotation.
        double step = Math.min(Math.PI / 12, Math.sqrt(2 * pixelSize / radius));
        step = Math.min(step, 1024 * pixelSize / radius);
        int segments = Math.max(1, (int)Math.ceil(2 * halfAngle / step));

        // Build vertices relative to the rim nearest the camera, not the distant circle center.
        double nx = distance == 0 ? 1 : dx / distance, ny = distance == 0 ? 0 : dy / distance;
        double rimX = viewX - nx * gap, rimY = viewY - ny * gap;
        float prevX = 0, prevY = 0;
        for (int i = 0; i <= segments; i++) {
            double angle = halfAngle * (2d * i / segments - 1);
            double tangent = radius * Math.sin(angle);
            double sinHalf = Math.sin(angle / 2);
            double inward = 2d * radius * sinHalf * sinHalf; // radius * (1 - cos(angle)), even for tiny angles
            float px = (float)(rimX - nx * inward - ny * tangent);
            float py = (float)(rimY - ny * inward + nx * tangent);
            if (i != 0) { line(prevX, prevY, px, py); }
            prevX = px; prevY = py;
        }
    }

    private double distanceToRim(double x, double y, double radius, double distance) {
        double gap = distance - radius;
        double rounding = 4 * Math.ulp(distance);
        if (rounding < pixelSize / 4 || Math.abs(gap) > viewRadius + rounding) { return gap; }
        // At extreme radii, even a double can lose the entire viewport offset in distance - radius.
        // Use (distance² - radius²) / (distance + radius), cancelling the float center/radius
        // squares (exact in double) before adding the much smaller camera terms.
        double xx = x * x, yy = y * y;
        double atOrigin = (Math.max(xx, yy) - radius * radius) + Math.min(xx, yy);
        double atView = atOrigin + viewX * (viewX - 2 * x) + viewY * (viewY - 2 * y);
        return atView / (distance + radius);
    }

    @Override void line(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float)Math.hypot(dx, dy);
        batch.draw(white, x1, y1 - stroke / 2, 0, stroke / 2, length, stroke, 1, 1, MathUtils.atan2Deg(dy, dx));
    }
}
