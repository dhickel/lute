package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.components.Facing;

public class PhysicDebugRenderSystem extends EntitySystem implements Disposable {
    private final World physicWorld;
    private final Box2DDebugRenderer debugRenderer;
    private final Camera camera;

    public PhysicDebugRenderSystem(World physicWorld, Camera camera) {
        this.debugRenderer = new VisionDebugRenderer();
        this.physicWorld = physicWorld;
        this.camera = camera;
    }

    @Override
    public void update(float deltaTime) {
        debugRenderer.render(physicWorld, camera.combined);
    }

    @Override
    public void dispose() {
        debugRenderer.dispose();
    }

    private static final class VisionDebugRenderer extends Box2DDebugRenderer {
        private final FixtureOutlines outlines = new FixtureOutlines() {
            @Override void line(float x1, float y1, float x2, float y2) { renderer.line(x1, y1, x2, y2); }
            @Override void circle(float x, float y, float radius) { renderer.circle(x, y, radius, 32); }
        };

        @Override
        protected void renderBody(Body body) {
            if (body.getUserData() instanceof Entity entity && AwarenessSystem.usesForwardVision(entity)) {
                for (var fixture : body.getFixtureList()) {
                    if (fixture.getUserData() == AwarenessSystem.SENSOR_TAG) {
                        drawForwardVision(body, entity);
                        return;
                    }
                }
            }
            super.renderBody(body);
        }

        private void drawForwardVision(Body body, Entity entity) {
            renderer.setColor(!body.isActive() ? SHAPE_NOT_ACTIVE : body.isAwake() ? SHAPE_AWAKE : SHAPE_NOT_AWAKE);
            for (var fixture : body.getFixtureList()) {
                if (fixture.getUserData() != AwarenessSystem.SENSOR_TAG) {
                    outlines.drawFixture(fixture);
                    continue;
                }
                var circle = (CircleShape) fixture.getShape();
                var center = body.getWorldPoint(circle.getPosition());
                float start = switch (Facing.MAPPER.get(entity).getDirection()) {
                    case RIGHT -> -90f;
                    case UP -> 0f;
                    case LEFT -> 90f;
                    case DOWN -> 180f;
                };
                renderer.arc(center.x, center.y, circle.getRadius(), start, 180f, 16);
            }
        }
    }
}

