package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.AtlasAsset;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.input.InputManager;

public class TiledAshleyConfigurator {
    private final Engine engine;
    private final AssetService assetService;
    private final InputManager inputManager;

    public TiledAshleyConfigurator(Engine engine, AssetService assetService, InputManager inputManager) {
        this.engine = engine;
        this.assetService = assetService;
        this.inputManager = inputManager;
    }

    public void onLoadObject(TiledMapTileMapObject tileMapObj) {
        Entity entity = engine.createEntity();
        TiledMapTile tile = tileMapObj.getTile();
        TextureRegion texRegion = getTextureRegion(tile);

        entity.add(new Graphic(texRegion, Color.WHITE.cpy()));

        Transform objTransform = Transform.forTiledObject(tile, texRegion, tileMapObj);
        entity.add(objTransform);

        addEntityController(tileMapObj, entity);
        addEntityMove(tile, entity);
        addEntityAnimation(tile, entity);

        entity.add(new Facing(Facing.Direction.DOWN));
        entity.add(new AnimationPlayer(entity));



        engine.addEntity(entity);
    }

    private TextureRegion getTextureRegion(TiledMapTile tile) {
        String atlasAsset = tile.getProperties().get("atlasAsset", AtlasAsset.OBJECTS.name(), String.class);

        // Illegal Argument check, should be improved
        AtlasAsset asset = AtlasAsset.valueOf(atlasAsset);

        TextureAtlas texAtlas = assetService.get(asset);
        FileTextureData texData = (FileTextureData) tile.getTextureRegion().getTexture().getTextureData();
        String atlasKey = texData.getFileHandle().nameWithoutExtension();

        TextureAtlas.AtlasRegion region = texAtlas.findRegion(atlasKey);
        if (region != null) {
            Gdx.app.debug("TiledAshleyConfigurator.getTextureRegion", "Loaded AtlasRegion for  key: " + atlasKey);
            return region;
        }

        // Fallback if atlas lookup fails
        Gdx.app.error("TiledAshleyConfigurator.Error", "Failed to find AtlasRegion for key: " + atlasKey);
        return tile.getTextureRegion();
    }

    private void addEntityController(TiledMapTileMapObject mapObj, Entity entity) {
        boolean controller = mapObj.getProperties().get("controller", false, Boolean.class);
        if (!controller) { return; }

        Gdx.app.debug("TiledAshleyConfigurator.addEntityController", "Added entity controller");
        Controller inputController = Controller.forInputManager(inputManager);
        entity.add(inputController);

    }

    private void addEntityMove(TiledMapTile tile, Entity entity) {
        float speed = tile.getProperties().get("speed", 0f, Float.class);
        if (speed == 0.0) { return; }

        Gdx.app.debug("TiledAshleyConfigurator.addMoveController", "Added Move COntroller");
        entity.add(new Move(speed));
    }

    private void addEntityAnimation(TiledMapTile tile, Entity entity) {
        String animStr = tile.getProperties().get("animation", "", String.class);
        if (animStr.isBlank()) { return; }

        Animation2D.AnimationType animationType = Animation2D.AnimationType.valueOf(animStr);
        String atlasAssetStr = tile.getProperties().get("atlasAsset", "", String.class);
        AtlasAsset asset = AtlasAsset.valueOf(atlasAssetStr);

        TextureAtlas texAtlas = assetService.get(asset);
        FileTextureData texData = (FileTextureData) tile.getTextureRegion().getTexture().getTextureData();
        String atlasKey = texData.getFileHandle().nameWithoutExtension();
        float speed = tile.getProperties().get("animationSpeed", 0f, Float.class);
        entity.add(new Animation2D(asset, atlasKey, animationType, Animation.PlayMode.LOOP, speed));
    }


}
