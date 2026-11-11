package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.AtlasAsset;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;

public class TiledAshleyConfigurator {
    private final Engine engine;
    private final AssetService assetService;
    private final InputManager inputManager;
    private final World physicWorld;

    public TiledAshleyConfigurator(Engine engine,
                                   AssetService assetService,
                                   InputManager inputManager,
                                   World physicWorld
    ) {
        this.engine = engine;
        this.assetService = assetService;
        this.inputManager = inputManager;
        this.physicWorld = physicWorld;
    }

    public void onLoadObject(TiledMapTileMapObject tileMapObj) {
        Entity entity = engine.createEntity();
        TiledMapTile tile = tileMapObj.getTile();
        TextureRegion texRegion = getTextureRegion(tile);

        entity.add(new Graphic(texRegion, Color.WHITE.cpy()));

        Transform objTransform = Transform.forTiledObject(tile, texRegion, tileMapObj);
        entity.add(objTransform);


        // TODO, improve assignment not every entity needs move, or physics
        addEntityController(tileMapObj, entity);
        addEntityMove(tile, entity);

        boolean isPhysics = tile.getProperties().get("isPhysics", false, Boolean.class);
        if (isPhysics) {
            BodyDef.BodyType bodyType = getPhysicBodyType(tile);
            addEntityPhysic(tile.getObjects(), bodyType, Vector2.Zero, entity);

        }

        boolean hasCamera = tile.getProperties().get("hasCamera", false, Boolean.class);
        if (hasCamera) {
            // addEntityCameraFollow(tileMapObj, entity)
            Gdx.app.debug("TiledAshleyConfigurator", "Added Camera to entity");
            entity.add(new CameraFollow());
        }

        entity.add(new Facing(Facing.Direction.DOWN));

        boolean isAnimated = tile.getProperties().get("isAnimated", false, Boolean.class);
        if (isAnimated) {
            addEntityAnimation(tile, entity);
            entity.add(new AnimationPlayer(entity));
        }

        engine.addEntity(entity);
    }

    private void addEntityCameraFollow(TiledMapTileMapObject tileMapObj, Entity entity) {

    }

    private BodyDef.BodyType getPhysicBodyType(TiledMapTile tile) {
        String bodyType = tile.getProperties().get("physicBodyType", "NULL", String.class);

        return switch (bodyType) {
            case "static" -> {
                yield BodyDef.BodyType.StaticBody;
            }
            case "dynamic" -> BodyDef.BodyType.DynamicBody;
            default -> throw new GdxRuntimeException("Failed to map bodyType for value: " + bodyType);
        };
    }

    private void addEntityPhysic(MapObjects mapObjs, BodyDef.BodyType bodyType, Vector2 relativeTo, Entity entity) {
        if (mapObjs.getCount() == 0) { return; }

        var transform = Transform.MAPPER.get(entity);
        Body body = createBody(
            mapObjs,
            transform.getPosition(),
            transform.getScaling(),
            bodyType,
            relativeTo,
            entity
        );

        entity.add(new Physic(body, transform.getPosition().cpy()));
    }


    public void onLoadTile(TiledMapTile tile, float x, float y) {
        createBody(
            tile.getObjects(),
            new Vector2(x, y),
            GameConsts.PHYSIC_SCALING,
            BodyDef.BodyType.StaticBody,
            Vector2.Zero,
            "environment"
        );
    }

    private Body createBody(MapObjects mapObjs,
                            Vector2 pos,
                            Vector2 scaling,
                            BodyDef.BodyType bodyType,
                            Vector2 relativeTo,
                            Object userData
    ) {
        var bodyDef = new BodyDef();
        bodyDef.type = bodyType;
        bodyDef.position.set(pos);
        bodyDef.fixedRotation = true;

        Body body = physicWorld.createBody(bodyDef);
        body.setUserData(userData);

        for (var mapObj : mapObjs) {
            FixtureDef fixDef = TiledPhysics.fixtureDefOf(mapObj, scaling, relativeTo);
            Fixture fixture = body.createFixture(fixDef);
            fixture.setUserData(mapObj.getName());
            fixDef.shape.dispose();

        }
        return body;
    }

    private TextureRegion getTextureRegion(TiledMapTile tile) {
        String atlasAsset = tile.getProperties().get("atlasAsset", AtlasAsset.OBJECTS.name(), String.class);
        String atlasKey = tile.getProperties().get("atlasKey", "NULL", String.class);


        // Illegal Argument check, should be improved
        AtlasAsset asset = AtlasAsset.valueOf(atlasAsset);

        TextureAtlas texAtlas = assetService.get(asset);
        // FileTextureData texData = (FileTextureData) tile.getTextureRegion().getTexture().getTextureData();
        //String atlasKey = texData.getFileHandle().nameWithoutExtension();

        TextureAtlas.AtlasRegion region = texAtlas.findRegion(atlasKey);
        if (region != null) {
            Gdx.app.debug("TiledAshleyConfigurator.getTextureRegion", "Loaded AtlasRegion for key: " + atlasKey);
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
        String animStr = tile.getProperties().get("animation", "DEFAULT", String.class);

        Animation2D.AnimationType animationType = Animation2D.AnimationType.valueOf(animStr);
        String atlasAssetStr = tile.getProperties().get("atlasAsset", "", String.class);
        String atlasKey = tile.getProperties().get("atlasKey", "NULL", String.class);
        int keyFrames = tile.getProperties().get("keyFrames", 1, Integer.class); // Todo add checks for these

        AtlasAsset asset = AtlasAsset.valueOf(atlasAssetStr);

        //       TextureAtlas texAtlas = assetService.get(asset);
//        FileTextureData texData = (FileTextureData) tile.getTextureRegion().getTexture().getTextureData();
//        String atlasKey = texData.getFileHandle().nameWithoutExtension();
        float speed = tile.getProperties().get("animationSpeed", 0f, Float.class);
        entity.add(new Animation2D(asset, atlasKey, animationType, Animation.PlayMode.LOOP, speed, keyFrames));
    }


}
