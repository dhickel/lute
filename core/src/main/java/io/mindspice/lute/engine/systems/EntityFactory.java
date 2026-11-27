package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.ObjectAssets;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.data.json.CollisionData;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.data.json.MapActorData;
import io.mindspice.lute.engine.data.json.ObjectData;
import io.mindspice.lute.engine.data.json.WeaponsData;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.properties.PhysicObjectProps;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;
import io.mindspice.lute.engine.tiled.TiledPhysics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityFactory extends EntitySystem {
    private static final Set<String> CHARACTER_ACTIONS = Set.of("idle", "walk", "death", "slash", "jab", "shot");
    private static final String PLAYER_PREFIX = "player";
    private final World world;
    private final AssetService assetService;

    public EntityFactory(World world, AssetService assetService) {
        this.world = world;
        this.assetService = assetService;
    }

    public Entity getPlayerEntity(Vector2 pixelPosition, PlayerState state, Facing.Direction spawnFacing) {
        var facing = state == null ? spawnFacing : state.facing().getDirection();
        var data = GameCore.INSTANCE.getGameData();
        var playerAnimation = data.playerData().animation();
        var atlas = CharacterAssets.CHARACTER;
        // The authored player animation key remains empty for the action name. The shared
        // character atlas needs the actor folder prefix separately.
        var animation = createAnimation(atlas, PLAYER_PREFIX,
            Animation2D.AnimationType.IDLE, playerAnimation.speed(), playerAnimation.frameInterval(), facing);
        var region = animation.getAnimation().getKeyFrame(0f);
        var player = data.playerData();
        var logical = player.logicalSize();
        if (logical.width() > region.getRegionWidth() || logical.height() > region.getRegionHeight()) {
            throw new IllegalArgumentException("Logical character frame must fit its source canvas");
        }
        var transform = characterTransform(pixelPosition, logical.width(), logical.height());
        var size = transform.getSize();
        var weapons = createWeapons(data.weaponsData(), animation, new Vector2(size.x * 0.5f, size.y * 0.5f));
        if (state == null) {
            var equipment = new Loadout.Equipment(player.loadout().head(), player.loadout().body(),
                player.loadout().hands(), player.loadout().feet());
            var loadout = new Loadout(equipment, player.loadout().weapon(), weapons, selection -> validateEquipment(
                atlas.id(), PLAYER_PREFIX, selection));
            state = new PlayerState(new Health(player.maxHealth()), loadout, new Facing(facing));
        }

        Entity entity = getEngine().createEntity();
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        entity.add(new Player());
        entity.add(new CompositeCharacter(region.getRegionWidth(), region.getRegionHeight()));
        entity.add(transform);
        state.attachTo(entity);
        entity.add(new Move(player.moveSpeed()));
        entity.add(new Combat());
        entity.add(animation);
        entity.add(new AnimationPlayer(entity));
        entity.add(createCharacterPhysics(transform.getPosition(), entity, BodyDef.BodyType.DynamicBody,
            player.collision(), player.hitboxBounds(), Actor.Role.PLAYER, 0f));
        entity.add(new CameraFollow());
        entity.add(new Controller(player.loadout().weaponCycle()));
        return entity;
    }

    /** Stage the actual enemy before replacing the live map. This checks its art, AI, weapons and
     * native geometry, and measures its solid footprint relative to a logical center at (0,0). */
    Rectangle enemySpawnFootprint(String key, Facing.Direction facing) {
        var staged = getMapActor(Vector2.Zero, Actor.Role.ENEMY, key, facing);
        var body = Physic.MAPPER.get(staged).getBody();
        try {
            for (var fixture : body.getFixtureList()) {
                if (fixture.isSensor()) { continue; }
                var shape = fixture.getShape();
                float radius = shape.getRadius(); // Circle radius or the native polygon skin.
                if (shape instanceof CircleShape circle) {
                    var c = body.getWorldPoint(circle.getPosition());
                    return new Rectangle(c.x - radius, c.y - radius, 2 * radius, 2 * radius);
                }
                var polygon = (PolygonShape) shape;
                var vertex = new Vector2();
                polygon.getVertex(0, vertex);
                var p = body.getWorldPoint(vertex);
                var bounds = new Rectangle(p.x, p.y, 0, 0);
                for (int i = 1; i < polygon.getVertexCount(); i++) {
                    polygon.getVertex(i, vertex);
                    bounds.merge(body.getWorldPoint(vertex));
                }
                return bounds.set(bounds.x - radius, bounds.y - radius, bounds.width + 2 * radius, bounds.height + 2 * radius);
            }
            throw new IllegalStateException("Enemy has no solid feet: " + key);
        } finally { world.destroyBody(body); } // Never registered with Ashley or stepped.
    }

    public Entity getMapActor(Vector2 pixelPosition, Actor.Role role, String spawnKey, Facing.Direction facing) {
        var data = GameCore.INSTANCE.getGameData();
        MapActorData definition = switch (role) {
            case NPC -> data.actorsData().npc().get(spawnKey);
            case ENEMY -> data.actorsData().enemies().get(spawnKey);
            case PLAYER -> throw new IllegalArgumentException("Expected an NPC or enemy");
        };
        if (definition == null) { throw new IllegalArgumentException("Unknown " + role + " spawnKey '" + spawnKey + "'"); }
        boolean enemy = role == Actor.Role.ENEMY;
        var atlas = enemy ? CharacterAssets.ENEMIES : CharacterAssets.CHARACTER;
        var settings = definition.animation();
        var prefix = settings.atlasKey();
        var animation = createAnimation(atlas, prefix, Animation2D.AnimationType.IDLE, settings.speed(), settings.frameInterval(), facing);
        var logical = definition.logicalSize();
        var region = animation.getAnimation().getKeyFrame(0f);
        if (logical.width() > region.getRegionWidth() || logical.height() > region.getRegionHeight()) {
            throw new IllegalArgumentException("Logical character frame must fit its source canvas: " + spawnKey);
        }
        var transform = characterTransform(pixelPosition, logical.width(), logical.height());
        var base = assetService.get(TextureAtlas.class, atlas.id());
        for (String action : CHARACTER_ACTIONS) {
            for (var direction : Facing.Direction.values()) {
                String key = animation.regionKey(action, direction);
                var frames = base.findRegions(key);
                if (frames.size != 8) { throw new IllegalArgumentException("Expected eight actor frames: " + atlas.id() + "/" + key); }
                for (int i = 0; i < frames.size; i++) {
                    var frame = frames.get(i);
                    if (frame.index != i) { throw new IllegalArgumentException("Actor frame index mismatch: " + key); }
                    AnimationSystem.validateCanvas(frame, 128, 128, key);
                }
            }
        }
        var weapons = createWeapons(data.weaponsData(), animation, transform.getSize().cpy().scl(.5f));
        var equipment = switch (definition) {
            case MapActorData.Npc npc -> {
                var items = npc.loadout();
                yield new Loadout.Equipment(items.head(), items.body(), items.hands(), items.feet());
            }
            case MapActorData.Enemy ignored -> new Loadout.Equipment(null, null, null, null);
        };
        var loadout = new Loadout(equipment, definition.weapon(), weapons, selection -> {
            if (enemy) {
                if (!equipment.equals(selection)) { throw new IllegalArgumentException("Baked enemies cannot equip armor layers"); }
            } else {
                validateEquipment(atlas.id(), prefix, selection);
            }
        });
        Entity entity = getEngine().createEntity();
        entity.add(transform).add(new Facing(facing)).add(animation).add(loadout)
            .add(new Graphic(region, Color.WHITE.cpy()))
            .add(new CompositeCharacter(region.getRegionWidth(), region.getRegionHeight()));
        entity.add(new Health(definition.maxHealth()));
        float detectionRadius = definition.detectionRadius();
        int infightChance = definition instanceof MapActorData.Enemy dataEnemy ? dataEnemy.infightChance() : 0;
        entity.add(new ActorAi(definition.behaviorGraph(), detectionRadius * GameConsts.UNIT_SCALE, infightChance));
        if (definition instanceof MapActorData.Enemy enemyData
            && enemyData.detectionBounds().shape() == MapActorData.EnemyDetectionBounds.Shape.FULL_CIRCLE) {
            entity.add(new FullCircleVision());
        }
        if (BehaviorGraphs.ARCHER.equals(definition.behaviorGraph())) {
            entity.add(new BowAim(transform.getSize().x, transform.getSize().y));
        }
        entity.add(new Move(definition.moveSpeed()));
        entity.add(new Combat());
        entity.add(new AnimationPlayer(entity));
        entity.add(createCharacterPhysics(transform.getPosition(), entity, BodyDef.BodyType.DynamicBody,
            definition.collision(), definition.hitboxBounds(), role, detectionRadius));
        return entity;
    }

    private static Transform characterTransform(Vector2 pixelPosition, float width, float height) {
        var size = new Vector2(width, height).scl(GameConsts.UNIT_SCALE);
        var origin = pixelPosition.cpy().scl(GameConsts.UNIT_SCALE).mulAdd(size, -.5f);
        return new Transform(origin, 2, size, new Vector2(1, 1), 0, 0);
    }

    private Map<String, CombatWeapon.Weapon> createWeapons(WeaponsData definitions, Animation2D animation, Vector2 anchor) {
        var weapons = new HashMap<String, CombatWeapon.Weapon>();
        var requiredFrames = new HashMap<String, Long>();
        requiredFrames.put("idle", 1L);
        requiredFrames.put("walk", 1L);
        requiredFrames.put("death", 1L);
        for (var entry : definitions.weapons().entrySet()) {
            String id = entry.getKey();
            var definition = entry.getValue();
            var sounds = WeaponAssets.of(id, definitions);
            var primary = createAttack(id, definition.attackStats().primary(), sounds.primary(), anchor, requiredFrames);
            var secondary = createAttack(id, definition.attackStats().secondary(), sounds.secondary(), anchor, requiredFrames);
            if (!id.equals("unarmed")) {
                validateLayer(animation.getAtlasId(), animation.getAtlasKey(), CharacterAssets.WEAPONS, definition.atlasKey(),
                    Set.copyOf(List.of("idle", "walk", "death", primary.animation().action(), secondary.animation().action())));
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
        var animation = new Animation2D.Attack(settings.animKey().atlasKey(), settings.speed(), settings.frameInterval(),
            settings.soundFrame(), sounds.attack().id());
        var bounds = data.bounds();
        var attack = new CombatWeapon.Attack(id, data.stats().damage(), data.stats().hitFrame(),
            data.stats().cooldownSeconds(), settings.animKey(), animation,
            sounds.hit().id(), bounds.type(), new CombatWeapon.Bounds(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(),
                bounds.offsetUp(), bounds.offsetDown(), bounds.offsetLeft(), bounds.offsetRight()),
            data.collisionType());
        attack.validateGeometry(anchor);
        long minimum = (long) Math.max(attack.hitFrame(), animation.soundFrame()) + 1;
        if (attack.collisionType() == WeaponsData.WeaponData.CollisionType.PROJECTILE) {
            minimum = Math.max(minimum, BowAim.FULL_DRAW_FRAME + 2L);
        }
        requiredFrames.merge(animation.action(), minimum, Math::max);
        return attack;
    }

    private void validateEquipment(String baseId, String basePrefix, Loadout.Equipment equipment) {
        if (equipment == null) { throw new IllegalArgumentException("Equipment selection is required"); }
        if (equipment.head() != null) {
            validateLayer(baseId, basePrefix, CharacterAssets.HEAD, equipment.head(), CHARACTER_ACTIONS);
        }
        if (equipment.body() != null) {
            validateLayer(baseId, basePrefix, CharacterAssets.BODY, equipment.body(), CHARACTER_ACTIONS);
        }
        if (equipment.hands() != null) {
            validateLayer(baseId, basePrefix, CharacterAssets.HANDS, equipment.hands(), CHARACTER_ACTIONS);
        }
        if (equipment.feet() != null) {
            validateLayer(baseId, basePrefix, CharacterAssets.FEET, equipment.feet(), CHARACTER_ACTIONS);
        }
    }

    private void validateLayer(String baseId, String basePrefix, Asset.Atlas asset, String prefix, Set<String> actions) {
        if (prefix == null || prefix.isBlank()) { throw new IllegalArgumentException("Equipment region prefix is required"); }
        var base = assetService.get(TextureAtlas.class, baseId);
        var atlas = assetService.get(TextureAtlas.class, asset.id());
        for (String action : actions) {
            for (var direction : Facing.Direction.values()) {
                String suffix = action + "_" + direction.atlasKey;
                var reference = base.findRegions(basePrefix.isEmpty() ? suffix : basePrefix + "/" + suffix);
                var frames = atlas.findRegions(prefix + "/" + suffix);
                if (reference.isEmpty() || frames.size != reference.size) {
                    throw new IllegalArgumentException("Layer frame count must match character: " + asset.id() + "/" + prefix + "/" + suffix);
                }
                for (int i = 0; i < frames.size; i++) {
                    AnimationSystem.validateCanvas(frames.get(i), reference.get(i).getRegionWidth(),
                        reference.get(i).getRegionHeight(), asset.id() + "/" + prefix + "/" + suffix);
                }
            }
        }
    }

    public Entity getSpawnedObject(Vector2 pixelPosition, String spawnKey, Facing.Direction facing) {
        return buildSpawnedObject(pixelPosition, spawnKey, facing, null);
    }

    void validatePickupSpawn(String spawnKey, ObjectData.Kind expectedKind) {
        var staged = buildSpawnedObject(Vector2.Zero, spawnKey, Facing.Direction.DOWN, expectedKind);
        world.destroyBody(Physic.MAPPER.get(staged).getBody()); // Never registered or stepped.
    }

    private Entity buildSpawnedObject(Vector2 pixelPosition, String spawnKey, Facing.Direction facing,
                                      ObjectData.Kind expectedKind) {
        var definition = GameCore.INSTANCE.getGameData().objectsData().get(spawnKey);
        if (definition == null) { throw new IllegalArgumentException("Unknown object spawnKey: " + spawnKey); }
        if (expectedKind != null && definition.pickup().map(ObjectData.Pickup::type).orElse(null) != expectedKind) {
            throw new IllegalArgumentException("Expected " + expectedKind + " pickup for " + spawnKey);
        }
        var atlas = ObjectAssets.OBJECTS;
        Animation2D animation = null;
        TextureRegion region;
        if (definition.animation().isEmpty()) {
            String regionKey = definition.atlasKey().orElse(spawnKey);
            region = assetService.get(TextureAtlas.class, atlas.id()).findRegion(regionKey, -1);
            if (region == null) {
                throw new IllegalArgumentException("No static object atlas region '" + regionKey + "' for " + spawnKey);
            }
        } else {
            var settings = definition.animation().orElseThrow();
            animation = createAnimation(atlas, settings.atlasKey(), Animation2D.AnimationType.DEFAULT,
                settings.speed(), settings.frameInterval(), facing);
            var frames = animation.getAnimation().getKeyFrames();
            region = frames[0];
            for (var frame : frames) {
                if (frame.getRegionWidth() != region.getRegionWidth() || frame.getRegionHeight() != region.getRegionHeight()) {
                    throw new IllegalArgumentException("Object animation frame sizes differ for " + spawnKey);
                }
            }
        }

        float rotation = switch (facing) {
            case DOWN -> 0f;
            case RIGHT -> 90f;
            case UP -> 180f;
            case LEFT -> -90f;
        };
        float width = region.getRegionWidth();
        float height = region.getRegionHeight();
        float unit = GameConsts.UNIT_SCALE;
        var size = new Vector2(width, height).scl(unit);
        var center = pixelPosition.cpy().scl(unit);
        var position = center.cpy().mulAdd(size, -.5f);
        var entity = getEngine().createEntity();
        entity.add(new Transform(position, 2, size, new Vector2(1, 1), rotation, 0f));
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        if (animation != null) {
            entity.add(new Facing(facing));
            entity.add(animation);
        }
        // Convert and validate the light's world radius before allocating a staged native body.
        definition.emissive().ifPresent(light -> entity.add(new Emissive(light, unit)));
        definition.pickup().ifPresent(rule -> {
            String payload = null;
            if (rule.type() == ObjectData.Kind.WEAPON_BAG) {
                var eligible = GameCore.INSTANCE.getGameData().weaponsData().weapons().keySet().stream()
                    .filter(key -> !key.equals("basic_bow") && !key.equals("basic_arrow")).toList();
                if (eligible.isEmpty()) { throw new IllegalArgumentException("No eligible loaded weapon for " + spawnKey); }
                if (expectedKind == null) {
                    payload = eligible.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(eligible.size()));
                }
            }
            if (expectedKind == null) { entity.add(new Pickup(rule, payload)); }
        });
        if (definition.collision().isPresent() || definition.pickup().isPresent()) {
            var collision = definition.collision().orElseGet(() -> CollisionData.ellipse(new CharacterBounds(0, 0, width, height)));
            var bounds = collision.bounds();
            if (bounds.x2() > width || bounds.y2() > height) {
                throw new IllegalArgumentException("Object collision bounds exceed the sprite canvas for " + spawnKey);
            }
            var bodyDef = new BodyDef();
            bodyDef.type = BodyDef.BodyType.StaticBody;
            bodyDef.position.set(center);
            bodyDef.angle = (float) Math.toRadians(rotation);
            var body = world.createBody(bodyDef);
            try {
                CharacterFixtures.create(body, collision, definition.pickup().isPresent(), width * .5f, height * .5f);
                body.setUserData(entity);
                entity.add(new Physic(body, position.cpy()));
            } catch (RuntimeException | Error failure) {
                world.destroyBody(body);
                throw failure;
            }
        }
        return entity;
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

    private Physic createCharacterPhysics(Vector2 position, Entity entity, BodyDef.BodyType bodyType,
                                          CollisionData collision, CharacterBounds hurtbox, Actor.Role role,
                                          float awarenessRadiusPixels) {
        var bodyDef = new BodyDef();
        bodyDef.type = bodyType;
        bodyDef.position.set(position);
        bodyDef.fixedRotation = true;
        Body body = world.createBody(bodyDef);
        body.setUserData(entity);

        try {
            CharacterFixtures.create(body, collision, false, 0f, 0f);
            var feet = collision.bounds();
            CharacterFixtures.ellipse(body, hurtbox, true).setUserData(Hurtbox.INSTANCE);
            float unit = GameConsts.UNIT_SCALE;
            var actor = new Actor(role, (feet.x1() + feet.x2()) * .5f * unit,
                (feet.y1() + feet.y2()) * .5f * unit);
            if (role != Actor.Role.PLAYER) {
                var circle = new CircleShape();
                try {
                    circle.setPosition(new Vector2(actor.feetX(), actor.feetY()));
                    float radius = awarenessRadiusPixels * unit;
                    if (!Float.isFinite(radius) || radius <= 0f) {
                        throw new IllegalArgumentException("Awareness radius is not representable in world units");
                    }
                    circle.setRadius(radius);
                    var fixture = new FixtureDef();
                    fixture.shape = circle;
                    fixture.isSensor = true;
                    fixture.filter.maskBits = 0; // Query-only: no contacts with gameplay fixtures.
                    body.createFixture(fixture).setUserData(AwarenessSystem.SENSOR_TAG);
                } finally {
                    circle.dispose();
                }
            }
            entity.add(actor);
            return new Physic(body, position.cpy());
        } catch (RuntimeException | Error failure) {
            world.destroyBody(body);
            throw failure;
        }
    }
}
