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
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.singletons.GameConsts;

public class CameraSystem extends IteratingSystem implements EventListener {
    private final Camera camera;
    private float mapW;
    private float mapH;
    private Vector2 targetPos;

    public CameraSystem(Camera camera) {
        super(Family.all(CameraFollow.class, Transform.class).get());
        this.camera = camera;
        this.targetPos = new Vector2();

        eventSubscriber.accept(Event.Map.Loaded.class, (event) -> this.setMap(event.map()));
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Transform transform = Transform.MAPPER.get(entity);

        calcTargetPosition(transform.getPosition());

        float smoothVal = GameConsts.CAMERA_SMOOTH_FACTOR * deltaTime;
        float smoothedX = MathUtils.lerp(camera.position.x, targetPos.x, smoothVal);
        float smoothedY = MathUtils.lerp(camera.position.y, targetPos.y, smoothVal);
        camera.position.set(smoothedX, smoothedY, camera.position.z);

    }

    private void calcTargetPosition(Vector2 entityPos) {
        float tX = entityPos.x + 0.5f;
        float tY = entityPos.y + 0.5f; // Offset down 1 tile, TODO later properly center using player tile height
        float camHalfW = camera.viewportWidth * 0.5f;
        float camHalfH = camera.viewportHeight * 0.5f;

        if (mapW > camHalfW) {
            float min = Math.min(camHalfW, mapW - camHalfW);
            float max = Math.max(camHalfW, mapW - camHalfW);
            tX = MathUtils.clamp(tX, min, max);
        }

        if (mapH > camHalfH) {
            float min = Math.min(camHalfH, mapH - camHalfH);
            float max = Math.max(camHalfH, mapH - camHalfH);
            tY = MathUtils.clamp(tY, min, max);
        }

        this.targetPos.set(tX, tY);
    }

    public void setMap(TiledMap map) {
        int width = map.getProperties().get("width", 0, Integer.class);
        int tileWidth = map.getProperties().get("tilewidth", 0, Integer.class);
        int height = map.getProperties().get("height", 0, Integer.class);
        int tileHeight = map.getProperties().get("tileheight", 0, Integer.class);

        mapW = width * tileWidth * GameConsts.UNIT_SCALE;
        mapH = height * tileHeight * GameConsts.UNIT_SCALE;

        // TODO this gets the first we may later want to calc which camera to switch to
        Entity camEnt = getEntities().first();
        if (camEnt == null) { return; }

        processEntity(camEnt, 0f);


    }
}
