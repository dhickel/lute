package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.SortedIteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.mindspice.lute.engine.components.Graphic;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RenderSystem extends SortedIteratingSystem implements Disposable, EventListener {
    private final Batch batch;
    private final Viewport viewport;
    private final OrthographicCamera camera;
    private final List<MapLayer> fgLayers;
    private final List<MapLayer> bgLayers;

    private final OrthogonalTiledMapRenderer mapRenderer;

    public RenderSystem(Batch batch, Viewport viewport, OrthographicCamera camera) {
        super(
            Family.all(Transform.class, Graphic.class).get(),
            Comparator.comparing(Transform.MAPPER::get)
        );
        this.batch = batch;
        this.viewport = viewport;
        this.camera = camera;

        this.fgLayers = new ArrayList<>(4);
        this.bgLayers = new ArrayList<>(4);

        this.mapRenderer = new OrthogonalTiledMapRenderer(null, GameConsts.UNIT_SCALE, this.batch);

        eventSubscriber.accept(Event.Map.Loaded.class, (event) -> this.setMap(event.map()));
    }

    @Override
    public void update(float deltaTime) {
        AnimatedTiledMapTile.updateAnimationBaseTime();
        this.viewport.apply();

        batch.begin();
        this.batch.setColor(Color.WHITE);
        this.mapRenderer.setView(this.camera);
        bgLayers.forEach(mapRenderer::renderMapLayer);


        forceSort();
        // Processes Entity
        super.update(deltaTime);

        this.batch.setColor(Color.WHITE);
        fgLayers.forEach(mapRenderer::renderMapLayer);

        batch.end();
    }


    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Transform transform = Transform.MAPPER.get(entity);
        Graphic graphic = Graphic.MAPPER.get(entity);
        if (graphic.getRegion() == null) { return; }

        var pos = transform.getPosition();
        var scale = transform.getScaling();
        var size = transform.getSize();

        this.batch.setColor(graphic.getColor());
        this.batch.draw(
            graphic.getRegion(),
            pos.x - (1f - scale.x) * size.x * 0.5f,
            pos.y - (1f - scale.y) * size.y * 0.5f,
            size.x * 0.5f, size.y * 0.5f,
            size.x, size.y,
            scale.x, scale.y,
            transform.getRotationDeg()
        );
    }


    public void setMap(TiledMap map) {
        mapRenderer.setMap(map);
        cleanMapLayers();
        loadMapLayers(map);
    }

    public void cleanMapLayers() {
        fgLayers.clear();
        bgLayers.clear();
    }

    public void loadMapLayers(TiledMap map) {
        boolean isBgLayer = true;
        for (var layer : map.getLayers()) {
            if (layer.isVisible()) {
                if ("Objects".equals(layer.getName())) {
                    Gdx.app.debug("RenderSystem.loadMapLayers", "Detected Object Layer");
                    isBgLayer = false;
                    continue;
                }

                if (layer.getClass().equals(MapLayer.class)) {
                    Gdx.app.debug("RenderSystem.loadMapLayers", "Detected Map Layer: " + layer.getName());
                    continue;
                }

                if (isBgLayer) {
                    Gdx.app.debug("RenderSystem.loadMapLayers", "Loaded background Layer: " + layer.getName());
                    bgLayers.add(layer);
                } else {
                    Gdx.app.debug("RenderSystem.loadMapLayers", "Loaded foreground Layer: " + layer.getName());
                    fgLayers.add(layer);
                }
            }
        }
    }


    @Override
    public void dispose() {

    }


}
