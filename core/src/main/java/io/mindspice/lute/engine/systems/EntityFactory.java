package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.properties.PlayerCharProps;

public class EntityFactory extends EntitySystem {
    private final World world;
    private final AssetService assetService;
    private final InputManager inputManager;

    public EntityFactory(World world, AssetService assetService, InputManager inputManager) {
        this.world = world;
        this.assetService = assetService;
        this.inputManager = inputManager;
    }

    public Entity getPlayerEntity(PlayerCharProps props, Vector2 pixelPosition, MapAssets mapAssets) {
        if (props.keyframes() <= 0) {
            throw new GdxRuntimeException("Player keyFrames must be positive: " + props.keyframes());
        }
        var atlasAsset = mapAssets.atlasAssets().get(props.atlasAsset());
        if (atlasAsset == null) {
            throw new GdxRuntimeException("Unknown player atlas asset ID: " + props.atlasAsset());
        }
        var type = Animation2D.AnimationType.valueOf(props.animation());
        var facing = Facing.Direction.DOWN;
        String animationKey = type == Animation2D.AnimationType.DEFAULT
            ? props.atlasKey()
            : props.atlasKey() + "/" + type.atlasKey + "_" + facing.atlasKey;
        var regions = assetService.get(atlasAsset).findRegions(animationKey);
        if (regions.isEmpty()) {
            throw new GdxRuntimeException("No player atlas regions found for key: " + animationKey);
        }
        Animation<TextureRegion> animation = new Animation<>(1f / props.keyframes(), regions, Animation.PlayMode.LOOP);
        var anim2d = new Animation2D(atlasAsset, props.atlasKey(), type,
            Animation.PlayMode.LOOP, props.animationSpeed(), props.keyframes());
        anim2d.setAnimation(animation, facing);
        var region = animation.getKeyFrame(0f);
        var position = pixelPosition.cpy().scl(GameConsts.UNIT_SCALE);
        var size = new Vector2(region.getRegionWidth(), region.getRegionHeight()).scl(GameConsts.UNIT_SCALE);

        Entity entity = getEngine().createEntity();
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        entity.add(new Transform(position, 2, size, new Vector2(1f, 1f), 0f, 0f));
        entity.add(new Facing(facing));
        entity.add(new Move(4f));
        entity.add(anim2d);
        entity.add(new AnimationPlayer(entity));
        entity.add(new Physic(createPlayerBody(position, entity), position.cpy()));
        entity.add(new CameraFollow());
        entity.add(Controller.forInputManager(inputManager));
        return entity;
    }

    private Body createPlayerBody(Vector2 position, Entity entity) {
        var bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(position);
        bodyDef.fixedRotation = true;
        Body body = world.createBody(bodyDef);
        body.setUserData(entity);

        // Feet ellipse in the 64px sprite, measured from the bottom-left origin.
        float centerX = 31f * GameConsts.UNIT_SCALE;
        float centerY = 21.4375f * GameConsts.UNIT_SCALE;
        float radiusX = 10.875f * GameConsts.UNIT_SCALE;
        float radiusY = 4.4375f * GameConsts.UNIT_SCALE;
        var vertices = new Vector2[8];
        for (int i = 0; i < vertices.length; i++) {
            float angle = i * MathUtils.PI2 / vertices.length;
            vertices[i] = new Vector2(centerX + radiusX * MathUtils.cos(angle),
                centerY + radiusY * MathUtils.sin(angle));
        }
        var shape = new PolygonShape();
        try {
            shape.set(vertices);
            body.createFixture(shape, 0f).setFriction(0f);
        } finally {
            shape.dispose();
        }
        return body;
    }
}
