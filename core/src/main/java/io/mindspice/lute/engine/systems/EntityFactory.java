package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
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
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.data.json.WeaponsData;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.properties.PhysicObjectProps;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;
import io.mindspice.lute.engine.tiled.TiledPhysics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityFactory extends EntitySystem {
    private final World world;
    private final AssetService assetService;
    private final InputManager inputManager;

    public EntityFactory(World world, AssetService assetService, InputManager inputManager) {
        this.world = world;
        this.assetService = assetService;
        this.inputManager = inputManager;
    }

    public Entity getPlayerEntity(Vector2 pixelPosition, MapAssets mapAssets) {
        var facing = Facing.Direction.RIGHT;
        var data = GameCore.INSTANCE.getGameData();
        var playerAnimation = data.playerData().animation();
        var atlas = CharacterAssets.CHARACTER;
        var animation = createAnimation(atlas, playerAnimation.atlasKey(),
            Animation2D.AnimationType.IDLE, playerAnimation.speed(), playerAnimation.frameInterval(), facing);
        var region = animation.getAnimation().getKeyFrame(0f);
        var transform = createTransform(pixelPosition, region);
        var player = data.playerData();
        var size = transform.getSize();
        var weapons = createWeapons(data.weaponsData(), animation, new Vector2(size.x * 0.5f, size.y * 0.5f));
        String weaponId = player.loadout().weapon() == null ? "unarmed" : player.loadout().weapon();
        var weapon = weapons.get(weaponId);
        if (weapon == null) { throw new IllegalArgumentException("Unknown weapon: " + weaponId); }
        var combat = new Combat(weapon);
        var equipment = new Loadout.Equipment(player.loadout().head(), player.loadout().body(),
            player.loadout().hands(), player.loadout().feet());
        var loadout = new Loadout(equipment, combat, weapons, this::validateEquipment);

        Entity entity = getEngine().createEntity();
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        entity.add(new Player());
        entity.add(new CompositeCharacter());
        entity.add(transform);
        entity.add(new Facing(facing));
        entity.add(new Move(player.moveSpeed()));
        entity.add(new Health(player.maxHealth()));
        entity.add(combat);
        entity.add(loadout);
        entity.add(animation);
        entity.add(new AnimationPlayer(entity));
        entity.add(createPhysics(transform.getPosition(), entity));
        entity.add(new CameraFollow());
        entity.add(Controller.forInputManager(inputManager));
        return entity;
    }

    private Map<String, CombatWeapon.Weapon> createWeapons(WeaponsData definitions, Animation2D animation, Vector2 anchor) {
        var weapons = new HashMap<String, CombatWeapon.Weapon>();
        var requiredFrames = new HashMap<String, Long>();
        requiredFrames.put("idle", 1L);
        requiredFrames.put("walk", 1L);
        for (var entry : definitions.weapons().entrySet()) {
            String id = entry.getKey();
            var definition = entry.getValue();
            var sounds = WeaponAssets.of(id, definitions);
            var primary = createAttack(id, definition.attackStats().primary(), sounds.primary(), anchor, requiredFrames);
            var secondary = createAttack(id, definition.attackStats().secondary(), sounds.secondary(), anchor, requiredFrames);
            if (!id.equals("unarmed")) {
                validateLayer(CharacterAssets.WEAPONS, definition.atlasKey(),
                    Set.copyOf(List.of("idle", "walk", primary.animation().action(), secondary.animation().action())));
            }
            weapons.put(id, new CombatWeapon.Weapon(id, definition.atlasKey(), primary, secondary));
        }
        animation.setRequiredAttackFrames(requiredFrames);
        AnimationSystem.validateAttackFrames(animation, assetService.get(TextureAtlas.class, animation.getAtlasId()));
        return Map.copyOf(weapons);
    }

    private CombatWeapon.Attack createAttack(String id, WeaponsData.WeaponData.AttackData data, WeaponAssets.AttackSounds sounds,
                                             Vector2 anchor, Map<String, Long> requiredFrames) {
        var settings = data.animation();
        var animation = new Animation2D.Attack(settings.atlasKey(), settings.speed(), settings.frameInterval(),
            settings.soundFrame(), sounds.attack().id());
        var bounds = data.bounds();
        var attack = new CombatWeapon.Attack(id, data.stats().damage(), data.stats().hitFrame(), animation,
            sounds.hit().id(), bounds.type(), new CombatWeapon.Bounds(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2()),
            data.collisionType());
        attack.validateGeometry(anchor);
        long minimum = (long) Math.max(attack.hitFrame(), animation.soundFrame()) + 1;
        requiredFrames.merge(animation.action(), minimum, Math::max);
        return attack;
    }

    private void validateEquipment(Loadout.Equipment equipment) {
        if (equipment == null) { throw new IllegalArgumentException("Equipment is required"); }
        var actions = Set.of("idle", "walk", "slash", "jab", "shot");
        validateLayer(CharacterAssets.HEAD, equipment.head(), actions);
        validateLayer(CharacterAssets.BODY, equipment.body(), actions);
        validateLayer(CharacterAssets.HANDS, equipment.hands(), actions);
        validateLayer(CharacterAssets.FEET, equipment.feet(), actions);
    }

    private void validateLayer(Asset.Atlas asset, String prefix, Set<String> actions) {
        if (prefix == null || prefix.isBlank()) { throw new IllegalArgumentException("Equipment region prefix is required"); }
        var base = assetService.get(TextureAtlas.class, CharacterAssets.CHARACTER.id());
        var atlas = assetService.get(TextureAtlas.class, asset.id());
        for (String action : actions) {
            for (var direction : Facing.Direction.values()) {
                String suffix = action + "_" + direction.atlasKey;
                var reference = base.findRegions(suffix);
                var frames = atlas.findRegions(prefix + "/" + suffix);
                if (reference.isEmpty() || frames.size != reference.size) {
                    throw new IllegalArgumentException("Layer frame count must match character: " + asset.id() + "/" + prefix + "/" + suffix);
                }
                for (var frame : frames) {
                    if (frame.getRegionWidth() != 64 || frame.getRegionHeight() != 64) {
                        throw new IllegalArgumentException("Layer frames must be 64x64: " + asset.id() + "/" + prefix + "/" + suffix);
                    }
                }
            }
        }
    }

    public Entity getPhysicObjectEntity(PhysicObjectProps props, TiledMapTileMapObject object, MapAssets mapAssets) {
        var atlas = mapAssets.atlasAsset();
        String assetKey = props.atlasKey();
        var region = assetService.get(TextureAtlas.class, atlas.id()).findRegion(assetKey);
        if (region == null) {
            throw new GdxRuntimeException("No atlas region found for key: " + assetKey);
        }
        return createPhysicObject(object, region);
    }

    public Entity getAnimatedPhysicObjectEntity(AnimatedPhysicObjectProps props, TiledMapTileMapObject object, MapAssets mapAssets) {
        var atlas = mapAssets.atlasAsset();
        var facing = Facing.Direction.DOWN;
        var settings = props.animation();
        var animation = createAnimation(atlas, settings.atlasKey(), Animation2D.AnimationType.DEFAULT,
            settings.speed(), settings.frameInterval(), facing);

        Entity entity = createPhysicObject(object, animation.getAnimation().getKeyFrame(0f));
        entity.add(new Facing(facing));
        entity.add(animation);
        return entity;
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

    private Animation2D createAnimation(Asset.Atlas atlas, String atlasKey, Animation2D.AnimationType animType,
                                      float speed, float frameInterval, Facing.Direction facing) {
        String animationKey;
        if (animType == Animation2D.AnimationType.DEFAULT) {
            animationKey = atlasKey;
        } else {
            animationKey = (atlasKey.isEmpty() ? "" : atlasKey + "/") + animType.atlasKey + "_" + facing.atlasKey;
        }
        var regions = assetService.get(TextureAtlas.class, atlas.id()).findRegions(animationKey);
        if (regions.isEmpty()) {
            throw new GdxRuntimeException("No atlas regions found for key: " + animationKey
                + " (atlas: " + atlas.path() + ")");
        }
        Animation<TextureRegion> animation = new Animation<>(frameInterval, regions, Animation.PlayMode.LOOP);
        var anim2d = new Animation2D(atlas.id(), atlasKey, animType, Animation.PlayMode.LOOP, speed, frameInterval);
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
