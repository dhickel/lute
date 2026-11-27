package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.components.CameraFollow;
import io.mindspice.lute.engine.components.CompositeCharacter;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.singletons.GameConsts;

public class CameraSystemTest {
    public static void main(String[] args) {
        GdxNativesLoader.load();
        followsAuthoredBounds();
        preservesPlayerFramingAndSmoothing();
        centersAndClampsAfterViewportResize();
        System.out.println("CameraSystemTest passed (3 cases)");
    }

    private static void followsAuthoredBounds() {
        var scene = new Scene(80, 48, 40, 30, 12, 8);
        scene.expect(11.25f, 10.75f, "Snap to a non-default logical center, not the padded canvas");
        scene.origin.set(15, 13);
        scene.engine.update(1f / GameConsts.CAMERA_SMOOTH_FACTOR);
        scene.expect(16.25f, 13.75f, "Follow the same logical center during updates");
    }

    private static void preservesPlayerFramingAndSmoothing() {
        var scene = new Scene(64, 64, 40, 30, 12, 8);
        scene.expect(11, 11, "The 64px player is centered on its logical bounds (previously 10.5, 10.5)");
        scene.origin.set(0, 40);
        scene.engine.update(0.5f / GameConsts.CAMERA_SMOOTH_FACTOR);
        scene.expect(8.5f, 18.5f, "Keep smoothing toward the bounded target");
        scene.engine.update(1f / GameConsts.CAMERA_SMOOTH_FACTOR);
        scene.expect(6, 26, "Keep existing left and top edge framing");
        scene.origin.set(40, 0);
        scene.engine.update(1f / GameConsts.CAMERA_SMOOTH_FACTOR);
        scene.expect(34, 4, "Keep existing right and bottom edge framing");
    }

    private static void centersAndClampsAfterViewportResize() {
        var scene = new Scene(64, 64, 9, 30, 12, 8);
        scene.expect(4.5f, 11, "Center the undersized width at map load without centering the larger height");
        scene.origin.set(0, 0);
        scene.engine.update(1f / GameConsts.CAMERA_SMOOTH_FACTOR);
        scene.expect(4.5f, 4, "Movement cannot displace the undersized axis");

        scene.camera.viewportWidth = 6;
        scene.camera.viewportHeight = 20;
        scene.engine.update(0f);
        scene.expect(4.5f, 10, "Resize immediately puts the camera inside the new bounds even at zero delta");
        scene.engine.update(1f / GameConsts.CAMERA_SMOOTH_FACTOR);
        scene.expect(3, 10, "Resume follow when an axis becomes larger than the viewport");

        scene.camera.viewportWidth = 9;
        scene.camera.viewportHeight = 64;
        scene.engine.update(0f);
        scene.expect(4.5f, 15, "Center an exact-fit axis and an axis smaller than half the viewport");
    }

    private static class Scene {
        final Engine engine = new Engine();
        final OrthographicCamera camera;
        final Vector2 origin = new Vector2(10, 10);

        Scene(int logicalWidth, int logicalHeight, int mapWidth, int mapHeight, float viewWidth, float viewHeight) {
            camera = new OrthographicCamera(viewWidth, viewHeight);
            camera.position.z = 5;
            var system = new CameraSystem(camera);
            engine.addSystem(system);
            var size = new Vector2(logicalWidth, logicalHeight).scl(GameConsts.UNIT_SCALE);
            var player = new Entity()
                .add(new CameraFollow())
                .add(new Transform(origin, 0, size, new Vector2(1, 1), 0, 0))
                .add(new CompositeCharacter(128, 128));
            engine.addEntity(player);
            var map = new TiledMap();
            var props = map.getProperties();
            props.put("width", mapWidth);
            props.put("height", mapHeight);
            props.put("tilewidth", 32);
            props.put("tileheight", 32);
            system.setMap(map, player);
        }

        void expect(float x, float y, String message) {
            if (!camera.position.epsilonEquals(x, y, 5, 0.00001f)) {
                throw new AssertionError(message + ": expected (" + x + ", " + y + ", 5), got " + camera.position);
            }
        }
    }
}
