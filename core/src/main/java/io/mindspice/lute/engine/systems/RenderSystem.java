package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.SortedIteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.components.CompositeCharacter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
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
import java.util.function.Consumer;

public class RenderSystem extends SortedIteratingSystem implements Disposable, EventListener {
    private final Batch batch;
    private final CharacterCompositor compositor;
    private final ShaderProgram worldShader;
    private final Viewport viewport;
    private final OrthographicCamera camera;
    private final List<MapLayer> fgLayers;
    private final List<MapLayer> bgLayers;

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final Consumer<Event.Map.Changed> mapChanged = event -> setMap(event.map());
    private boolean disposed;

    public RenderSystem(Batch batch, Viewport viewport, OrthographicCamera camera, AssetService assets) {
        super(
            Family.all(Transform.class, Graphic.class).get(),
            Comparator.comparing(Transform.MAPPER::get)
        );
        this.batch = batch;
        this.compositor = new CharacterCompositor(assets);
        var state = new RenderState();
        try { this.worldShader = SpriteBatch.createDefaultShader(); }
        finally { state.restore(); }
        this.viewport = viewport;
        this.camera = camera;

        this.fgLayers = new ArrayList<>(4);
        this.bgLayers = new ArrayList<>(4);

        this.mapRenderer = new OrthogonalTiledMapRenderer(null, GameConsts.UNIT_SCALE, this.batch);

        eventSubscriber.accept(Event.Map.Changed.class, mapChanged);
    }

    @Override
    public void update(float deltaTime) {
        if (disposed) { throw new IllegalStateException("Render system is disposed"); }
        if (batch.isDrawing()) { throw new IllegalStateException("Render system requires an idle batch"); }
        AnimatedTiledMapTile.updateAnimationBaseTime();
        var state = new RenderState();
        float packedColor = batch.getPackedColor();
        var color = batch.getColor().cpy();
        var projection = new Matrix4(batch.getProjectionMatrix());
        var transform = new Matrix4(batch.getTransformMatrix());
        var shader = batch.getShader();
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcA = batch.getBlendSrcFuncAlpha(), dstA = batch.getBlendDstFuncAlpha();
        boolean blending = batch.isBlendingEnabled();
        try {
            this.viewport.apply();
            RenderState.prepare();
            compositor.compose(getEntities(), camera);
            batch.setTransformMatrix(new Matrix4());
            batch.setShader(worldShader);
            batch.enableBlending();
            straightBlend();
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            batch.setColor(Color.WHITE);
            mapRenderer.setView(camera);
            bgLayers.forEach(mapRenderer::renderMapLayer);
            forceSort();
            super.update(deltaTime);
            straightBlend();
            batch.setColor(Color.WHITE);
            fgLayers.forEach(mapRenderer::renderMapLayer);
        } finally {
            if (batch.isDrawing()) { batch.end(); }
            batch.setPackedColor(packedColor);
            batch.getColor().set(color);
            batch.setProjectionMatrix(projection);
            batch.setTransformMatrix(transform);
            batch.setShader(shader);
            batch.setBlendFunctionSeparate(src, dst, srcA, dstA);
            if (blending) { batch.enableBlending(); }
            else { batch.disableBlending(); }
            state.restore();
        }
    }


    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Transform transform = Transform.MAPPER.get(entity);
        Graphic graphic = Graphic.MAPPER.get(entity);
        boolean composite = CompositeCharacter.MAPPER.has(entity);
        var region = composite ? compositor.region(entity) : graphic.getRegion();
        if (region == null) { return; }

        var pos = transform.getPosition();
        var scale = transform.getScaling();
        var size = transform.getSize();

        var color = graphic.getColor();
        if (composite) {
            batch.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(color.r * color.a, color.g * color.a, color.b * color.a, color.a);
        } else {
            straightBlend();
            batch.setColor(color);
        }
        float width = composite ? region.getRegionWidth() * GameConsts.UNIT_SCALE : size.x;
        float height = composite ? region.getRegionHeight() * GameConsts.UNIT_SCALE : size.y;
        this.batch.draw(
            region,
            pos.x + size.x * scale.x * 0.5f - width * 0.5f,
            pos.y + size.y * scale.y * 0.5f - height * 0.5f,
            width * 0.5f, height * 0.5f,
            width, height,
            scale.x, scale.y,
            transform.getRotationDeg()
        );
    }


    private void straightBlend() {
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
            GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
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
        if (disposed) { return; }
        disposed = true;
        eventUnSubscriber.accept(Event.Map.Changed.class, mapChanged);
        var state = new RenderState();
        try {
            compositor.dispose();
            mapRenderer.dispose();
            state.releasedShader(worldShader.getHandle());
            worldShader.dispose();
        } finally { state.restore(); }
    }


}
