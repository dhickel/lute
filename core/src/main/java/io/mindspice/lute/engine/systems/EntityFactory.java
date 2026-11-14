package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.CombatWeapon;
import java.util.Objects;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.properties.PlayerCharProps;
import io.mindspice.lute.engine.tiled.properties.PhysicObjectProps;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;
import io.mindspice.lute.engine.tiled.TiledPhysics;

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
        var facing = Facing.Direction.RIGHT;
        var animation = createPlayerAnimation(props, mapAssets, facing);
        var region = animation.getAnimation().getKeyFrame(0f);
        var transform = createTransform(pixelPosition, region);
        var data = GameCore.INSTANCE.getGameData();
        var player = data.playerData();
        if (!Float.isFinite(player.moveSpeed()) || player.moveSpeed() <= 0f) {
            throw new IllegalArgumentException("Player moveSpeed must be finite and positive");
        }
        var weaponData = Objects.requireNonNull(data.weaponsData().weapons().get(player.equippedWeapon()),
            "Unknown equipped weapon: " + player.equippedWeapon());
        var bounds = Objects.requireNonNull(weaponData.bounds(), "weapon bounds");
        var weapon = new CombatWeapon.Weapon(player.equippedWeapon(), weaponData.damage(), weaponData.attackSpeed(),
            weaponData.attackDuration(), weaponData.boundsType(), new CombatWeapon.Bounds(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2()));

        Entity entity = getEngine().createEntity();
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        entity.add(transform);
        entity.add(new Facing(facing));
        entity.add(new Move(player.moveSpeed()));
        entity.add(new Health(player.maxHealth()));
        entity.add(new Combat(weapon));
        entity.add(animation);
        entity.add(new AnimationPlayer(entity));
        entity.add(createPhysics(transform.getPosition(), entity));
        entity.add(new CameraFollow());
        entity.add(Controller.forInputManager(inputManager));
        return entity;
    }

    public Entity getPhysicObjectEntity(PhysicObjectProps props, TiledMapTileMapObject object, MapAssets mapAssets) {
        var atlas = resolveAtlas(props.atlasAsset(), mapAssets);
        String assetKey = props.assetKey();
        var region = assetService.get(atlas).findRegion(assetKey);
        if (region == null) {
            throw new GdxRuntimeException("No atlas region found for key: " + assetKey);
        }
        return createPhysicObject(object, region);
    }

    public Entity getAnimatedPhysicObjectEntity(AnimatedPhysicObjectProps props, TiledMapTileMapObject object, MapAssets mapAssets) {
        var atlas = resolveAtlas(props.atlasAsset(), mapAssets);
        var type = Animation2D.AnimationType.valueOf(props.animation());
        var facing = Facing.Direction.DOWN;
        var animation = createAnimation(atlas, props.atlasKey(), type,
            props.animationSpeed(), props.keyFrames(), facing);

        Entity entity = createPhysicObject(object, animation.getAnimation().getKeyFrame(0f));
        entity.add(new Facing(facing));
        entity.add(animation);
        return entity;
    }

    private Asset.Atlas resolveAtlas(String atlasId, MapAssets mapAssets) {
        var atlas = mapAssets.atlasAssets().get(atlasId);
        if (atlas == null) {
            throw new GdxRuntimeException("Unknown atlas asset ID: " + atlasId);
        }
        return atlas;
    }

    private Entity createPhysicObject(TiledMapTileMapObject object, TextureRegion region) {
        var collisionObjects = object.getTile().getObjects();
        if (collisionObjects.getCount() == 0) {
            throw new GdxRuntimeException("Physics object has no collision shapes: " + object.getName());
        }
        var transform = Transform.forTiledObject(object.getTile(), region, object);
        Entity entity = getEngine().createEntity();
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        entity.add(transform);
        entity.add(createObjectPhysics(object, transform, entity));
        return entity;
    }

    private Physic createObjectPhysics(TiledMapTileMapObject object, Transform transform, Entity entity) {
        var bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(transform.getPosition());
        bodyDef.fixedRotation = true;
        Body body = world.createBody(bodyDef);
        body.setUserData(entity);

        try {
            for (var collision : object.getTile().getObjects()) {
                var fixtureDef = TiledPhysics.fixtureDefOf(collision, transform.getScaling(), Vector2.Zero);
                try {
                    body.createFixture(fixtureDef).setUserData(collision.getName());
                } finally {
                    fixtureDef.shape.dispose();
                }
            }
        } catch (RuntimeException | Error failure) {
            world.destroyBody(body);
            throw failure;
        }
        return new Physic(body, transform.getPosition().cpy());
    }

    private Animation2D createPlayerAnimation(PlayerCharProps props, MapAssets mapAssets, Facing.Direction facing) {
        var atlas = resolveAtlas(props.atlasAsset(), mapAssets);
        var type = Animation2D.AnimationType.valueOf(props.animation());
        return createAnimation(atlas, props.atlasKey(), type, props.animationSpeed(), props.keyFrames(), facing);
    }

    private Animation2D createAnimation(Asset.Atlas atlas, String atlasKey, Animation2D.AnimationType animType,
                                      float speed, int keyFrames, Facing.Direction facing) {
        String animationKey;
        if (animType == Animation2D.AnimationType.DEFAULT) {
            animationKey = atlasKey;
        } else {
            animationKey = atlasKey + "/" + animType.atlasKey + "_" + facing.atlasKey;
        }
        if (keyFrames <= 0) {
            throw new GdxRuntimeException("Animation keyFrames must be positive: " + keyFrames
                + " (key: " + animationKey + ", atlas: " + atlas.descriptor().fileName + ")");
        }
        var regions = assetService.get(atlas).findRegions(animationKey);
        if (regions.isEmpty()) {
            throw new GdxRuntimeException("No atlas regions found for key: " + animationKey
                + " (atlas: " + atlas.descriptor().fileName + ")");
        }
        Animation<TextureRegion> animation = new Animation<>(1f / keyFrames, regions, Animation.PlayMode.LOOP);
        var anim2d = new Animation2D(atlas, atlasKey, animType, Animation.PlayMode.LOOP, speed, keyFrames);
        anim2d.setAnimation(animation, facing);
        return anim2d;
    }

    private Transform createTransform(Vector2 pixelPosition, TextureRegion region) {
        var position = pixelPosition.cpy().scl(GameConsts.UNIT_SCALE);
        var size = new Vector2(region.getRegionWidth(), region.getRegionHeight()).scl(GameConsts.UNIT_SCALE);
        position.sub(size.x * 0.5f, size.y * 0.5f);
        return new Transform(position, 2, size, new Vector2(1f, 1f), 0f, 0f);
    }

    private Physic createPhysics(Vector2 position, Entity entity) {
        return new Physic(createPlayerBody(position, entity), position.cpy());
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
