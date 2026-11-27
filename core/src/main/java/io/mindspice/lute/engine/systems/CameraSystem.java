package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.components.CameraFollow;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.singletons.GameConsts;

public class CameraSystem extends IteratingSystem {
    private final Camera camera;
    private float mapW;
    private float mapH;
    private final Vector2 targetPos = new Vector2();

    public CameraSystem(Camera camera) {
        super(Family.all(CameraFollow.class, Transform.class).get());
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        calcTargetPosition(Transform.MAPPER.get(entity));

        float smoothVal = GameConsts.CAMERA_SMOOTH_FACTOR * deltaTime;
        float smoothedX = MathUtils.lerp(camera.position.x, targetPos.x, smoothVal);
        float smoothedY = MathUtils.lerp(camera.position.y, targetPos.y, smoothVal);
        // An enlarged viewport can leave the previous camera position outside the new bounds.
        camera.position.set(boundAxis(smoothedX, camera.viewportWidth, mapW),
            boundAxis(smoothedY, camera.viewportHeight, mapH), camera.position.z);
    }

    private void calcTargetPosition(Transform transform) {
        targetPos.set(boundAxis(transform.centerX(), camera.viewportWidth, mapW),
            boundAxis(transform.centerY(), camera.viewportHeight, mapH));
    }

    private static float boundAxis(float target, float viewportSize, float mapSize) {
        if (mapSize <= viewportSize) { return mapSize * 0.5f; }
        float halfView = viewportSize * 0.5f;
        return MathUtils.clamp(target, halfView, mapSize - halfView);
    }

    void setMap(TiledMap map, Entity player) {
        int width = map.getProperties().get("width", 0, Integer.class);
        int tileWidth = map.getProperties().get("tilewidth", 0, Integer.class);
        int height = map.getProperties().get("height", 0, Integer.class);
        int tileHeight = map.getProperties().get("tileheight", 0, Integer.class);

        mapW = (float) width * tileWidth * GameConsts.UNIT_SCALE;
        mapH = (float) height * tileHeight * GameConsts.UNIT_SCALE;
        if (player != null) {
            calcTargetPosition(Transform.MAPPER.get(player));
            camera.position.set(targetPos.x, targetPos.y, camera.position.z);
            camera.update();
        }
    }
}
