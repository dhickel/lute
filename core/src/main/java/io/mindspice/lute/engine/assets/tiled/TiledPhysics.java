package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.singletons.GameConsts;

public class TiledPhysics {


    public static FixtureDef fixtureDefOf(MapObject mapObject, Vector2 scaling, Vector2 relativeTo) {
        return switch (mapObject) {
            case RectangleMapObject rmo -> rectangleFixtureDef(rmo, scaling, relativeTo);
            case EllipseMapObject emo -> ellipseFixtureDef(emo, scaling, relativeTo);
            case PolygonMapObject pmo ->{
                Polygon polygon = pmo.getPolygon();
                float offsetX = polygon.getX() * GameConsts.UNIT_SCALE;
                float offsetY = polygon.getY() * GameConsts.UNIT_SCALE;
                yield polygonFixtureDef(pmo, polygon.getVertices(), offsetX, offsetY, scaling, relativeTo);
            }
            case PolylineMapObject plmo -> {
                Polyline polyline = plmo.getPolyline();
                float offsetX = polyline.getX() * GameConsts.UNIT_SCALE;
                float offsetY = polyline.getY() * GameConsts.UNIT_SCALE;
                yield polygonFixtureDef(plmo, polyline.getVertices(), offsetX, offsetY, scaling, relativeTo);
            }
            default -> throw new GdxRuntimeException("Unsupported MapObject: " + mapObject);
        };
    }
    // Box is centered around body position in Box2D, but we want to have it aligned in a way
    // that the body position is the bottom left corner of the box.
    // That's why we use a 'boxOffset' below.
    private static FixtureDef rectangleFixtureDef(RectangleMapObject mapObject, Vector2 scaling, Vector2 relativeTo) {
        Rectangle rectangle = mapObject.getRectangle();
        float rectX = rectangle.x;
        float rectY = rectangle.y;
        float rectW = rectangle.width;
        float rectH = rectangle.height;

        float boxX = rectX * GameConsts.UNIT_SCALE * scaling.x - relativeTo.x;
        float boxY = rectY * GameConsts.UNIT_SCALE * scaling.y - relativeTo.y;
        float boxW = rectW * GameConsts.UNIT_SCALE * scaling.x * 0.5f;
        float boxH = rectH * GameConsts.UNIT_SCALE * scaling.y * 0.5f;

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(boxW, boxH, new Vector2(boxX + boxW, boxY + boxH), 0f);
        return fixtureDefOfMapObjectAndShape(mapObject, shape);
    }

    private static FixtureDef ellipseFixtureDef(EllipseMapObject mapObject, Vector2 scaling, Vector2 relativeTo) {
        Ellipse ellipse = mapObject.getEllipse();
        float x = ellipse.x;
        float y = ellipse.y;
        float w = ellipse.width;
        float h = ellipse.height;

        float ellipseX = x * GameConsts.UNIT_SCALE * scaling.x - relativeTo.x;
        float ellipseY = y * GameConsts.UNIT_SCALE * scaling.y - relativeTo.y;
        float ellipseW = w * GameConsts.UNIT_SCALE * scaling.x * 0.5f;
        float ellipseH = h * GameConsts.UNIT_SCALE * scaling.y * 0.5f;

        if (MathUtils.isEqual(ellipseW, ellipseH, 0.1f)) {
            // width and height are equal -> return a circle shape
            CircleShape shape = new CircleShape();
            shape.setPosition(new Vector2(ellipseX + ellipseW, ellipseY + ellipseH));
            shape.setRadius(ellipseW);
            return fixtureDefOfMapObjectAndShape(mapObject, shape);
        }

        // width and height are not equal -> return an ellipse shape (=polygon with 'numVertices' vertices)
        // PolygonShape only supports 8 vertices
        // ChainShape supports more but does not properly collide in some scenarios
        final int numVertices = 8;
        float angleStep = MathUtils.PI2 / numVertices;
        Vector2[] vertices = new Vector2[numVertices];

        for (int vertexIdx = 0; vertexIdx < numVertices; vertexIdx++) {
            float angle = vertexIdx * angleStep;
            float offsetX = ellipseW * MathUtils.cos(angle);
            float offsetY = ellipseH * MathUtils.sin(angle);
            vertices[vertexIdx] = new Vector2(ellipseX + ellipseW + offsetX, ellipseY + ellipseH + offsetY);
        }

        PolygonShape shape = new PolygonShape();
        shape.set(vertices);
        return fixtureDefOfMapObjectAndShape(mapObject, shape);
    }

    private static FixtureDef polygonFixtureDef(
        MapObject mapObject, // Could be PolygonMapObject or PolylineMapObject
        float[] polyVertices,
        float offsetX,
        float offsetY,
        Vector2 scaling,
        Vector2 relativeTo
    ) {
        offsetX = (offsetX * scaling.x) - relativeTo.x;
        offsetY = (offsetY * scaling.y) - relativeTo.y;
        float[] vertices = new float[polyVertices.length];
        for (int vertexIdx = 0; vertexIdx < polyVertices.length; vertexIdx += 2) {
            // x-coordinate
            vertices[vertexIdx] = offsetX + polyVertices[vertexIdx] * GameConsts.UNIT_SCALE * scaling.x;
            // y-coordinate
            vertices[vertexIdx + 1] = offsetY + polyVertices[vertexIdx + 1] * GameConsts.UNIT_SCALE * scaling.y;
        }

        ChainShape shape = new ChainShape();
        if (mapObject instanceof PolygonMapObject) {
            shape.createLoop(vertices);
        } else { // PolylineMapObject
            shape.createChain(vertices);
        }
        return fixtureDefOfMapObjectAndShape(mapObject, shape);
    }

    private static FixtureDef fixtureDefOfMapObjectAndShape(MapObject mapObject, Shape shape) {
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = mapObject.getProperties().get("friction", 0f, Float.class);
        fixtureDef.restitution = mapObject.getProperties().get("restitution", 0f, Float.class);
        fixtureDef.density = mapObject.getProperties().get("density", 0f, Float.class);
        fixtureDef.isSensor = mapObject.getProperties().get("sensor", false, Boolean.class);
        return fixtureDef;
    }
}
