package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.*;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.singletons.GameConsts;

public class TriggerDebugRenderSystem extends EntitySystem implements Disposable {
    private final World world;
    private final Camera camera;
    private final ShapeRenderer renderer;
    private final Array<Body> bodies = new Array<>();
    private final Vector2 a = new Vector2();
    private final Vector2 b = new Vector2();

    public TriggerDebugRenderSystem(World world, Camera camera) {
        this(world, camera, new ShapeRenderer());
    }

    TriggerDebugRenderSystem(World world, Camera camera, ShapeRenderer renderer) {
        this.world = world;
        this.camera = camera;
        this.renderer = renderer;
    }

    @Override
    public void update(float deltaTime) {
        renderer.setProjectionMatrix(camera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Line);
        renderer.setColor(Color.CYAN);
        world.getBodies(bodies);
        for (var body : bodies) {
            for (var fixture : body.getFixtureList()) {
                if (fixture.isSensor()) { drawSensor(fixture); }
            }
        }
        renderer.setColor(Color.MAGENTA);
        var tiled = getEngine().getSystem(TiledSystem.class);
        var map = tiled == null ? null : tiled.getCurrentMap();
        if (map != null) { drawTriggerLayers(map.getLayers(), false); }
        renderer.end();
    }

    private void drawSensor(Fixture fixture) {
        var body = fixture.getBody();
        switch (fixture.getShape()) {
            case CircleShape circle -> {
                a.set(body.getWorldPoint(circle.getPosition()));
                renderer.circle(a.x, a.y, circle.getRadius(), 32);
            }
            case PolygonShape polygon -> {
                for (int i = 0; i < polygon.getVertexCount(); i++) {
                    polygon.getVertex(i, a);
                    polygon.getVertex((i + 1) % polygon.getVertexCount(), b);
                    drawBodyLine(body);
                }
            }
            case ChainShape chain -> {
                for (int i = 1; i < chain.getVertexCount(); i++) {
                    chain.getVertex(i - 1, a);
                    chain.getVertex(i, b);
                    drawBodyLine(body);
                }
            }
            case EdgeShape edge -> {
                edge.getVertex1(a);
                edge.getVertex2(b);
                drawBodyLine(body);
            }
            default -> { }
        }
    }

    private void drawBodyLine(Body body) {
        // Box2D reuses the returned vector, so copy each endpoint before requesting the next.
        a.set(body.getWorldPoint(a));
        b.set(body.getWorldPoint(b));
        renderer.line(a, b);
    }

    private void drawTriggerLayers(MapLayers layers, boolean inTriggers) {
        for (var layer : layers) {
            boolean triggers = inTriggers || "Triggers".equals(layer.getName());
            if (triggers) {
                for (var object : layer.getObjects()) {
                    // The configured TMX loader leaves offsets in Tiled's y-down pixel coordinates.
                    drawTrigger(object, layer.getRenderOffsetX(), -layer.getRenderOffsetY());
                }
            }
            if (layer instanceof MapGroupLayer group) { drawTriggerLayers(group.getLayers(), triggers); }
        }
    }

    private void drawTrigger(MapObject object, float offsetX, float offsetY) {
        float scale = GameConsts.UNIT_SCALE;
        // TMX rotations are clockwise; loaded geometry is y-up, in pixels.
        float rotation = -object.getProperties().get("rotation", 0f, Number.class).floatValue();
        switch (object) {
            case PointMapObject point -> {
                var position = point.getPoint();
                float x = (position.x + offsetX) * scale;
                float y = (position.y + offsetY) * scale;
                float radius = 4f * scale;
                renderer.line(x - radius, y, x + radius, y);
                renderer.line(x, y - radius, x, y + radius);
            }
            case RectangleMapObject rectangle -> {
                var r = rectangle.getRectangle();
                drawMapRectangle(r.x, r.y, r.width, r.height, r.x, r.y + r.height, rotation, offsetX, offsetY);
            }
            case EllipseMapObject ellipse -> {
                var e = ellipse.getEllipse();
                float centerX = e.x + e.width * 0.5f;
                float centerY = e.y + e.height * 0.5f;
                float previousX = centerX + e.width * 0.5f;
                float previousY = centerY;
                for (int i = 1; i <= 32; i++) {
                    float angle = i * MathUtils.PI2 / 32f;
                    float x = centerX + MathUtils.cos(angle) * e.width * 0.5f;
                    float y = centerY + MathUtils.sin(angle) * e.height * 0.5f;
                    drawMapLine(previousX, previousY, x, y, e.x, e.y + e.height, rotation, offsetX, offsetY);
                    previousX = x;
                    previousY = y;
                }
            }
            case PolygonMapObject objectPolygon -> {
                var polygon = objectPolygon.getPolygon();
                drawMapLines(polygon.getTransformedVertices(), true, polygon.getX(), polygon.getY(),
                    rotation, offsetX, offsetY);
            }
            case PolylineMapObject objectPolyline -> {
                var polyline = objectPolyline.getPolyline();
                drawMapLines(polyline.getTransformedVertices(), false, polyline.getX(), polyline.getY(),
                    rotation, offsetX, offsetY);
            }
            case TiledMapTileMapObject tile -> {
                // GID flips only change UVs, not the rectangular footprint.
                float originX = tile.getX() + tile.getOriginX();
                float originY = tile.getY() + tile.getOriginY();
                drawMapRectangle(originX - tile.getOriginX() * tile.getScaleX(),
                    originY - tile.getOriginY() * tile.getScaleY(),
                    tile.getTextureRegion().getRegionWidth() * tile.getScaleX(),
                    tile.getTextureRegion().getRegionHeight() * tile.getScaleY(),
                    originX, originY, -tile.getRotation(), offsetX, offsetY);
            }
            case TextMapObject text -> {
                var r = text.getRectangle();
                drawMapRectangle(r.x, r.y, r.width, r.height, r.x, r.y + r.height, rotation, offsetX, offsetY);
            }
            default -> { }
        }
    }

    private void drawMapRectangle(float x, float y, float width, float height,
                                  float originX, float originY, float rotation, float offsetX, float offsetY) {
        drawMapLine(x, y, x + width, y, originX, originY, rotation, offsetX, offsetY);
        drawMapLine(x + width, y, x + width, y + height, originX, originY, rotation, offsetX, offsetY);
        drawMapLine(x + width, y + height, x, y + height, originX, originY, rotation, offsetX, offsetY);
        drawMapLine(x, y + height, x, y, originX, originY, rotation, offsetX, offsetY);
    }

    private void drawMapLines(float[] vertices, boolean closed, float originX, float originY,
                              float rotation, float offsetX, float offsetY) {
        int count = vertices.length / 2;
        int segments = closed ? count : count - 1;
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % count;
            drawMapLine(vertices[i * 2], vertices[i * 2 + 1], vertices[next * 2], vertices[next * 2 + 1],
                originX, originY, rotation, offsetX, offsetY);
        }
    }

    private void drawMapLine(float x1, float y1, float x2, float y2,
                             float originX, float originY, float rotation, float offsetX, float offsetY) {
        a.set(x1 - originX, y1 - originY).rotateDeg(rotation).add(originX + offsetX, originY + offsetY)
            .scl(GameConsts.UNIT_SCALE);
        b.set(x2 - originX, y2 - originY).rotateDeg(rotation).add(originX + offsetX, originY + offsetY)
            .scl(GameConsts.UNIT_SCALE);
        renderer.line(a, b);
    }

    @Override
    public void dispose() {
        renderer.dispose();
    }
}
