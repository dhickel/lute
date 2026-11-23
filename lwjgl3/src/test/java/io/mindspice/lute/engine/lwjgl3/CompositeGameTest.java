package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.CharacterCompositor;
import io.mindspice.lute.engine.systems.RenderSystem;
import io.mindspice.lute.engine.systems.ControllerSystem;
import io.mindspice.lute.engine.systems.DamageFeedbackProbe;
import io.mindspice.lute.engine.systems.LoadoutSystem;
import io.mindspice.lute.engine.systems.EventSystem;
import io.mindspice.lute.engine.systems.TiledSystem;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CompositeGameTest {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static final Loadout.Equipment BASIC = new Loadout.Equipment("basic", "basic", "basic", "basic");
    private static boolean initialized;
    private static int checks;

    private CompositeGameTest() { }

    public static void run() {
        require(!initialized, "Real game test must run once per application");
        GAME.create();
        initialized = true;
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Engine engine = GAME.getEngine();
        require(engine.getSystem(EventSystem.class) != null, "GameScreen installs the genuine EventSystem");
        require(engine.getSystem(LoadoutSystem.class) != null, "GameScreen installs the loadout coordinator");
        require(engine.getSystem(TiledSystem.class).getCurrentMap() == null, "Map is not loaded before InitialLoading runs");
        require(engine.getEntitiesFor(Family.all(Player.class).get()).size() == 0, "Factory has not spawned the player early");

        var definitions = GAME.getGameData().weaponsData();
        int loadingUpdates = ManifestLoader.parseMapManifest("test_tiled").all().size()
            + CharacterAssets.all(definitions.atlas()).size() + 3;
        for (String id : definitions.weapons().keySet()) {
            loadingUpdates += WeaponAssets.of(id, definitions).all().size();
        }
        for (int i = 0; i < loadingUpdates && engine.getEntitiesFor(Family.all(Player.class).get()).size() == 0; i++) {
            GAME.render();
        }
        var players = engine.getEntitiesFor(Family.all(Player.class).get());
        require(players.size() == 1, "InitialLoading -> GameScreen -> map event -> factory creates exactly one player");
        require(engine.getSystem(TiledSystem.class).getCurrentMap() != null, "Real startup map is installed");
        Entity player = players.first();
        require(CompositeCharacter.MAPPER.has(player), "Factory marks the player for composition");
        Loadout loadout = Loadout.MAPPER.get(player);
        require(loadout.getEquipment().equals(BASIC), "Default four equipment pieces are basic");
        require(java.util.Objects.equals(GAME.getGameData().playerData().loadout().weapon(), loadout.getWeaponId()),
            "Player default weapon comes from authored data");
        engine.update(0f);
        screenshot("default-weapon");

        Map<Fixture, Short> originalGroups = new HashMap<>();
        for (Fixture fixture : Physic.MAPPER.get(player).getBody().getFixtureList()) {
            var filter = fixture.getFilterData();
            originalGroups.put(fixture, filter.groupIndex);
            // The damage probe must contact attack sensors, not push the player's feet.
            filter.groupIndex = -17;
            fixture.setFilterData(filter);
        }
        try (var layers = new LayerCheck()) {
            verifyOptionalArmor(player, layers);
            // Keep targets at their markers for deterministic weapon and pixel comparisons.
            engine.getSystem(io.mindspice.lute.engine.systems.AwarenessSystem.class).setProcessing(false);
            verifyMapActors(engine, layers);
            verifyMapActorDamage(player);
            for (String weapon : List.of("basic_sword", "basic_bow", "unarmed")) {
                require(loadouts().equipWeapon(player, weapon.equals("unarmed") ? null : weapon), "Switch idle weapon: " + weapon);
                require(loadouts().equip(player, BASIC), "Re-equip validated real equipment: " + weapon);
                require(weapon.equals(loadout.getWeapon().id()), "Loadout owns switched weapon");
                require((loadout.getWeaponId() == null) == weapon.equals("unarmed"), "Unarmed has no visible weapon");
                for (Facing.Direction direction : Facing.Direction.values()) {
                    int key = movementKey(direction);
                    Gdx.input.getInputProcessor().keyDown(key);
                    engine.update(0f);
                    require(Facing.MAPPER.get(player).getDirection() == direction, "Movement input selects facing " + direction);
                    verifyLoop(player, layers, Animation2D.AnimationType.WALK);
                    Gdx.input.getInputProcessor().keyUp(key);
                    engine.update(0f);
                    require(Move.MAPPER.get(player).getDirection().isZero(), "Movement release balances cached input");
                    verifyLoop(player, layers, Animation2D.AnimationType.IDLE);
                    attack(player, layers, Input.Keys.SPACE, Combat.AttackSlot.PRIMARY);
                    attack(player, layers, direction == Facing.Direction.LEFT ? Input.Keys.SHIFT_RIGHT : Input.Keys.SHIFT_LEFT,
                        Combat.AttackSlot.SECONDARY);
                }
            }
            layers.deathFrames(player);
            var atlas = GAME.getAssetService().get(TextureAtlas.class, CharacterAssets.WEAPONS.id());
            var arrow = atlas.findRegion(GAME.getGameData().projectilesData().ironArrow().atlasKey());
            require(arrow != null && layers.weaponPages.size() + 1 == atlas.getTextures().size,
                "All character weapon pages sampled, with a separate arrow page");
            require(layers.overhangPixels > 0, "Actual regenerated frames contain visible pixels outside the logical area");
            require(layers.helmetOcclusionActions.containsAll(Set.of("jab", "slash", "shot", "death")),
                "Real upward poses exercise opaque helmet/glove overlaps in every overlapping action");
            System.out.println("CompositeGameTest: " + checks + " checks; " + layers.frames
                + " actual-asset composition AND native-scale world-frame comparisons; " + layers.weaponPages.size() + " weapon pages sampled");
        } finally {
            GAME.getInputManager().releaseHeldInputs();
            engine.getSystem(ControllerSystem.class).update(0f);
            originalGroups.forEach((fixture, group) -> {
                var filter = fixture.getFilterData();
                filter.groupIndex = group;
                fixture.setFilterData(filter);
            });
        }
        require(loadouts().equipWeapon(player, "basic_sword"), "Restore sword after gameplay coverage");
        require(loadouts().equip(player, BASIC), "Restore basic equipment");
        engine.update(0f);
    }

    private static void verifyMapActors(Engine engine, LayerCheck layers) {
        var tiled = engine.getSystem(TiledSystem.class);
        var actors = engine.getEntitiesFor(Family.all(CompositeCharacter.class).exclude(Player.class).get());
        require(actors.size() == 2, "Actual startup map spawns exactly two non-player actors");
        for (var actor : actors) {
            var animation = Animation2D.MAPPER.get(actor);
            boolean enemy = animation.getAtlasId().equals(CharacterAssets.ENEMIES.id());
            require(enemy ? animation.getAtlasKey().equals("brainless_undead")
                : animation.getAtlasId().equals(CharacterAssets.CHARACTER.id())
                    && animation.getAtlasKey().equals("basic_npc"), "Known map actor base");
            var marker = tiled.getMapTriggers().points().stream()
                .filter(p -> p.name().equals(enemy ? "EnemySpawn" : "NpcSpawn")).findFirst().orElseThrow();
            var transform = Transform.MAPPER.get(actor);
            var center = transform.getPosition().cpy().mulAdd(transform.getSize(), .5f);
            require(center.epsilonEquals(marker.position().cpy().scl(GameConsts.UNIT_SCALE), .0001f)
                && transform.getSize().epsilonEquals(64 * GameConsts.UNIT_SCALE, 64 * GameConsts.UNIT_SCALE, .0001f),
                "Actor logical bounds centered on actual map marker");
            require(!Controller.MAPPER.has(actor) && actor.getComponent(CameraFollow.class) == null && Combat.MAPPER.has(actor),
                "Map actor can use combat without player control or camera ownership");
            verifyActorFixtures(actor, transform, marker.name());
            var loadout = Loadout.MAPPER.get(actor);
            require(loadout.getEquipment().equals(enemy ? new Loadout.Equipment(null, null, null, null)
                : new Loadout.Equipment(null, "basic", "basic", "basic")), "Only requested actor layers are equipped");
            require(java.util.Objects.equals(loadout.getWeaponId(), enemy ? "basic_sword" : null), "Actor demo weapon default");
            var swordOrNpc = layers.check(actor);
            if (enemy) {
                require(loadouts().equipWeapon(actor, "basic_bow"), "Enemy can equip bow while idle");
                require(layers.check(actor) != swordOrNpc, "Enemy bow and sword produce distinct rendered pixels");
                require(loadouts().equipWeapon(actor, "basic_sword"), "Restore enemy demo weapon");
            } else {
                verifyOptionalArmor(actor, layers);
            }
        }
        System.out.println("CompositeGameTest: real NPC and enemy marker placement, canvas, depth and weapon switch verified");
    }

    private static void verifyOptionalArmor(Entity actor, LayerCheck layers) {
        var loadout = Loadout.MAPPER.get(actor);
        var originalArmor = loadout.getEquipment();
        var originalWeapon = loadout.getWeaponId();
        require(loadouts().equipWeapon(actor, null), "Unequip weapon for armor-only comparison");
        try {
            require(loadouts().equip(actor, new Loadout.Equipment(null, null, null, null)), "All armor can be removed");
            long bare = layers.check(actor);
            for (var armor : List.of(new Loadout.Equipment("basic", null, null, null),
                    new Loadout.Equipment(null, "basic", null, null), new Loadout.Equipment(null, null, "basic", null),
                    new Loadout.Equipment(null, null, null, "basic"))) {
                require(loadouts().equip(actor, armor), "Each armor slot can be equipped independently: " + armor);
                require(layers.check(actor) != bare, "Equipped armor changes the rendered base: " + armor);
            }
        } finally {
            require(loadouts().equip(actor, originalArmor), "Restore original armor");
            require(loadouts().equipWeapon(actor, originalWeapon), "Restore original weapon");
        }
    }

    private static void verifyActorFixtures(Entity actor, Transform transform, String spawn) {
        require(Physic.MAPPER.has(actor), spawn + " has a physics body");
        var body = Physic.MAPPER.get(actor).getBody();
        require(body.getType() == BodyDef.BodyType.DynamicBody && body.getUserData() == actor
            && body.getPosition().epsilonEquals(transform.getPosition(), .0001f),
            spawn + " has a movable body aligned with the logical frame");
        var fixtures = body.getFixtureList();
        require(fixtures.size == 3, spawn + " has solid feet, hurtbox and awareness fixtures");
        Fixture feet = null;
        Fixture hurtbox = null;
        Fixture awareness = null;
        for (var fixture : fixtures) {
            if (!fixture.isSensor()) { feet = fixture; }
            else if (fixture.getUserData() == Hurtbox.INSTANCE) { hurtbox = fixture; }
            else if (fixture.getShape() instanceof CircleShape circle) {
                awareness = fixture;
                require(fixture.getFilterData().maskBits == 0
                    && circle.getPosition().epsilonEquals(new Vector2(32, 9).scl(GameConsts.UNIT_SCALE), .0001f),
                    spawn + " has a contact-free awareness circle at its physical feet");
            }
        }
        require(feet != null && hurtbox != null && awareness != null,
            spawn + " has typed feet, hurtbox and awareness fixtures");
        require(hurtbox.isSensor() && hurtbox.getUserData() == Hurtbox.INSTANCE,
            spawn + " has a separately tagged damage sensor");
        float unit = GameConsts.UNIT_SCALE;
        var feetPoint = body.getWorldPoint(new Vector2(32, 9).scl(unit)).cpy();
        var torsoPoint = body.getWorldPoint(new Vector2(32, 40).scl(unit)).cpy();
        require(feet.testPoint(feetPoint) && !feet.testPoint(torsoPoint) && hurtbox.testPoint(torsoPoint),
            spawn + " feet block while the torso is only part of the hurtbox");
        require(Health.MAPPER.has(actor) && Health.MAPPER.get(actor).getCurrent() == Health.MAPPER.get(actor).getMaximum(),
            spawn + " starts with full health");
    }

    private static void verifyMapActorDamage(Entity player) {
        var engine = GAME.getEngine();
        var actors = engine.getEntitiesFor(Family.all(CompositeCharacter.class).exclude(Player.class).get());
        var position = Physic.MAPPER.get(player).getBody().getPosition().cpy();
        var cameraPosition = GAME.getCamera().position.cpy();
        var facing = Facing.MAPPER.get(player).getDirection();
        var weaponId = Loadout.MAPPER.get(player).getWeaponId();
        try {
            for (var actor : actors) {
                String name = Animation2D.MAPPER.get(actor).getAtlasKey();
                var health = Health.MAPPER.get(actor);
                var origin = Physic.MAPPER.get(actor).getBody().getPosition().cpy();
                try {
                    for (String weapon : List.of("basic_sword", "basic_bow")) {
                        boolean bow = weapon.equals("basic_bow");
                        require(loadouts().equipWeapon(player, weapon), "Equip " + weapon + " for " + name);
                        placePlayer(player, origin.cpy().sub((bow ? 64 : 48) * GameConsts.UNIT_SCALE, 0));
                        health = new Health(health.getMaximum());
                        actor.add(health);
                        int before = health.getCurrent();
                        require(fireAtActor(player, bow, Facing.Direction.LEFT, actor) == 0 && health.getCurrent() == before,
                            weapon + " miss leaves " + name + " unharmed and makes no damage number");
                        int damage = Loadout.MAPPER.get(player).getWeapon().primary().damage();
                        require(fireAtActor(player, bow, Facing.Direction.RIGHT, actor) == 1
                                && oneHit(before, damage, health.getCurrent()),
                            weapon + " deals one full hit and shows one number for " + name + ": " + before + " -> " + health.getCurrent());
                    }
                } finally {
                    actor.add(new Health(health.getMaximum()));
                }
            }
        } finally {
            Gdx.input.getInputProcessor().keyUp(Input.Keys.SPACE);
            placePlayer(player, position);
            GAME.getCamera().position.set(cameraPosition);
            Facing.MAPPER.get(player).setDirection(facing);
            require(loadouts().equipWeapon(player, weaponId), "Restore player weapon after map actor attacks");
            engine.update(0);
        }
        System.out.println("CompositeGameTest: both spawned actors take exactly one sword and bow hit; misses do no damage");
    }

    private static void placePlayer(Entity player, Vector2 position) {
        var physics = Physic.MAPPER.get(player);
        physics.getBody().setTransform(position, 0);
        physics.getBody().setLinearVelocity(0, 0);
        physics.getPrevPos().set(position);
        Transform.MAPPER.get(player).getPosition().set(position);
    }

    private static int fireAtActor(Entity player, boolean bow, Facing.Direction direction, Entity target) {
        var engine = GAME.getEngine();
        var input = Gdx.input.getInputProcessor();
        var combat = Combat.MAPPER.get(player);
        long previousAttack = combat.getAttackId();
        Facing.MAPPER.get(player).setDirection(direction);
        input.keyDown(Input.Keys.SPACE);
        engine.update(0);
        if (bow) {
            var animation = Animation2D.MAPPER.get(player);
            require(BowAim.MAPPER.get(player).isAiming() && animation.isAttackReady()
                && animation.getAction().equals("shot") && animation.getFrameIndex() == 0,
                "Primary press installs shot_00 before counting draw frames");
            int aimKey = movementKey(direction);
            input.keyDown(aimKey);
            engine.update(1f / 6f); // Steer the crosshair one extra tile while holding the draw.
            input.keyUp(aimKey);
            for (int frame = 0; frame < 120 && animation.getFrameIndex() < BowAim.FULL_DRAW_FRAME; frame++) {
                engine.update(1f / 60f);
            }
            require(animation.getFrameIndex() == BowAim.FULL_DRAW_FRAME, "Bow reaches full draw");
        }
        input.keyUp(Input.Keys.SPACE);
        engine.update(0);
        // Let combat finish, sampling feedback before its short lifetime ends.
        int numbers = DamageFeedbackProbe.count(target);
        for (int frame = 0; frame < 120; frame++) {
            engine.update(1f / 60f);
            numbers = Math.max(numbers, DamageFeedbackProbe.count(target));
        }
        require(!combat.isAttacking() && combat.getAttackId() == previousAttack + 1,
            "One primary input completes exactly one attack");
        return numbers;
    }

    private static void verifyLoop(Entity player, LayerCheck layers, Animation2D.AnimationType type) {
        Animation2D animation = Animation2D.MAPPER.get(player);
        require(animation.getType() == type, "Real FSM enters " + type);
        int count = animation.getAnimation().getKeyFrames().length;
        var seen = new BitSet(count);
        float step = animation.getFrameDuration() / animation.getSpeed() * 0.5f;
        for (int tick = 0; tick <= count * 2 + 2 && seen.cardinality() < count; tick++) {
            int frame = animation.getFrameIndex();
            if (!seen.get(frame)) {
                layers.check(player);
                seen.set(frame);
            }
            GAME.getEngine().update(step);
            require(animation.getType() == type, "Loop action remains " + type);
        }
        require(seen.cardinality() == count, "Every authored " + type + " frame is rendered");
    }

    private static void attack(Entity player, LayerCheck layers, int key, Combat.AttackSlot slot) {
        Engine engine = GAME.getEngine();
        Combat combat = Combat.MAPPER.get(player);
        Loadout loadout = Loadout.MAPPER.get(player);
        Animation2D animation = Animation2D.MAPPER.get(player);
        var weapon = loadout.getWeapon();
        var expected = slot == Combat.AttackSlot.PRIMARY ? weapon.primary() : weapon.secondary();
        boolean shot = weapon.id().equals("basic_bow") && slot == Combat.AttackSlot.PRIMARY;
        require(expected.animation().action().equals(slot == Combat.AttackSlot.SECONDARY || weapon.id().equals("unarmed")
            ? "jab" : shot ? "shot" : "slash"), "Authored weapon/input action mapping");
        require((expected.collisionType() == CollisionType.PROJECTILE) == shot, "Only bow primary is projectile data");
        var body = Physic.MAPPER.get(player).getBody();
        World world = body.getWorld();
        Entity target = damageProbe(player, expected);
        int entities = engine.getEntities().size();
        int bodies = world.getBodyCount();
        int fixtures = fixtureCount(world);
        int playerHealth = Health.MAPPER.get(player).getCurrent();
        long previousAttack = combat.getAttackId();
        Facing.Direction direction = Facing.MAPPER.get(player).getDirection();
        var seen = new BitSet();
        try {
            if (shot) {
                Facing.MAPPER.get(player).setDirection(switch (direction) {
                    case UP -> Facing.Direction.DOWN; case DOWN -> Facing.Direction.UP;
                    case LEFT -> Facing.Direction.RIGHT; case RIGHT -> Facing.Direction.LEFT;
                });
            }
            Gdx.input.getInputProcessor().keyDown(key);
            engine.getSystem(ControllerSystem.class).update(0f);
            if (shot) {
                var aim = io.mindspice.lute.engine.components.BowAim.MAPPER.get(player);
                require(aim.isAiming() && !combat.hasAttackRequest(), "Bow press aims without attacking");
                engine.update(0);
                for (int frame = 0; frame <= 4; frame++) {
                    require(animation.getFrameIndex() == frame && animation.getFdirection() != direction,
                        "Draw in original facing through shot_04");
                    layers.check(player);
                    seen.set(frame);
                    engine.update(expected.animation().frameInterval() / expected.animation().speed());
                }
                require(animation.getFrameIndex() == 4, "Held composite stays fully drawn");
                var steer = switch (direction) {
                    case UP -> Vector2.Y; case DOWN -> new Vector2(0, -1);
                    case RIGHT -> Vector2.X; case LEFT -> new Vector2(-1, 0);
                };
                aim.steer(steer, 3f / 6f); // Cross the player and keep the target two tiles away.
                Physic.MAPPER.get(target).getBody().setTransform(aim.getTargetX(), aim.getTargetY(), 0);
                Gdx.input.getInputProcessor().keyUp(key);
                engine.getSystem(ControllerSystem.class).update(0f);
                require(!aim.isAiming(), "Bow release removes crosshair");
            } else { require(combat.hasAttackRequest(), "Actual input reaches controller request: " + key); }
            blockedLoadout(player, weapon);
            engine.update(0f);
            require(combat.isAttacking() && combat.getActiveAttack() == expected, "Combat snapshots selected attack");
            blockedLoadout(player, weapon);
            Gdx.input.getInputProcessor().keyUp(key);
            engine.update(0f);
            require(animation.isAttackReady() && animation.getAction().equals(expected.animation().action()),
                "Actual AnimationSystem starts authored attack action");
            int count = animation.getAnimation().getKeyFrames().length;
            if (shot) {
                require(animation.getFrameIndex() == 5 && animation.getFdirection() == direction,
                    "Release turns the composite and starts shot_05, not shot_00");
            }
            float step = Math.min(1f / 60f, expected.animation().frameInterval() / expected.animation().speed() * 0.5f);
            int limit = (int) Math.ceil(count * expected.animation().frameInterval() / expected.animation().speed() / step) + 3;
            boolean sensorSeen = false;
            for (int tick = 0; tick <= limit && combat.isAttacking(); tick++) {
                int frame = animation.getFrameIndex();
                if (!seen.get(frame)) {
                    layers.check(player);
                    seen.set(frame);
                    if (direction == Facing.Direction.RIGHT && slot == Combat.AttackSlot.PRIMARY && frame == count / 2) {
                        screenshot(weapon.id() + "-" + animation.getAction());
                    }
                }
                require(Facing.MAPPER.get(player).getDirection() == direction, "Attack preserves facing");
                require(combat.getAttackId() == previousAttack + 1, "Key release never retriggers attack");
                if (shot) {
                    require(combat.getSensor() == null, "Bow shot never owns a melee sensor");
                    require(engine.getEntities().size() == entities && world.getBodyCount() == bodies,
                        "Bow shot creates no projectile entity or body");
                    require(fixtureCount(world) == fixtures, "Bow shot creates no fixture on any body");
                    require(Health.MAPPER.get(player).getCurrent() == playerHealth, "Bow shot cannot hit its owner");
                } else {
                    boolean reached = animation.hasReachedFrame(expected.hitFrame());
                    require((combat.getSensor() != null) == reached, "Melee sensor follows authored hit frame");
                    sensorSeen |= combat.getSensor() != null;
                }
                engine.update(step);
            }
            require(!combat.isAttacking() && combat.getSensor() == null, "Animation completion cleans up attack/sensor");
            require(seen.cardinality() == count, "All frames played for " + weapon.id() + "/" + slot + "/" + direction);
            engine.update(0f);
            require(animation.getType() == Animation2D.AnimationType.IDLE, "Attack FSM returns to idle after release");
            require(combat.getAttackId() == previousAttack + 1 && !combat.hasAttackRequest(), "No queued attack from keyUp");
            if (shot) {
                int remaining = Health.MAPPER.get(target).getCurrent();
                boolean clear = direction == Facing.Direction.LEFT || direction == Facing.Direction.RIGHT;
                require(clear ? oneHit(1000, expected.damage(), remaining) : remaining == 1000,
                    "Probe takes one hit on clear paths, none behind test-map walls: " + direction);
            } else {
                require(sensorSeen, "Melee attack really creates a sensor, including bow jab");
                require(oneHit(1000, expected.damage(), Health.MAPPER.get(target).getCurrent()),
                    "Real physics contact deals exactly one authored melee hit: " + weapon.id() + "/" + slot + "/" + direction);
            }
            require(fixtureCount(world) == fixtures, "Completed attack restores world fixture count");
        } finally {
            Gdx.input.getInputProcessor().keyUp(key);
            engine.removeEntity(target);
        }
    }

    private static LoadoutSystem loadouts() {
        return GAME.getEngine().getSystem(LoadoutSystem.class);
    }

    private static void blockedLoadout(Entity player, CombatWeapon.Weapon weapon) {
        var loadout = Loadout.MAPPER.get(player);
        require(!loadouts().equip(player, BASIC), "Equipment change rejected while attack is queued/active");
        require(!loadouts().equipWeapon(player, "basic_sword") && !loadouts().equipWeapon(player, "basic_bow")
            && !loadouts().equipWeapon(player, null),
            "Sword, bow and unarmed changes rejected while attack is queued/active");
        require(loadout.getEquipment().equals(BASIC), "Rejected switch preserves equipment");
        require((loadout.getWeaponId() == null ? "unarmed" : loadout.getWeaponId()).equals(weapon.id()),
            "Rejected switch preserves weapon");
    }

    private static Entity damageProbe(Entity player, CombatWeapon.Attack attack) {
        Body owner = Physic.MAPPER.get(player).getBody();
        Vector2 center = new Vector2();
        Vector2[] vertices = attack.vertices(Transform.MAPPER.get(player).getSize().cpy().scl(0.5f),
            Facing.MAPPER.get(player).getDirection());
        for (Vector2 vertex : vertices) { center.add(vertex); }
        center.scl(1f / vertices.length).add(owner.getPosition());
        var definition = new BodyDef();
        definition.type = BodyDef.BodyType.StaticBody;
        definition.position.set(center);
        Body body = owner.getWorld().createBody(definition);
        var circle = new CircleShape();
        try {
            circle.setRadius(GameConsts.UNIT_SCALE);
            Fixture fixture = body.createFixture(circle, 0f);
            var filter = fixture.getFilterData();
            filter.groupIndex = -17;
            fixture.setFilterData(filter);
        } finally { circle.dispose(); }
        var target = new Entity().add(new Health(1000))
            .add(new Physic(body, center.cpy()))
            .add(new Transform(center.cpy(), 0, new Vector2(1, 1), new Vector2(1, 1), 0, 0));
        body.setUserData(target);
        GAME.getEngine().addEntity(target);
        return target;
    }

    private static int fixtureCount(World world) {
        var bodies = new Array<Body>();
        world.getBodies(bodies);
        int count = 0;
        for (Body body : bodies) { count += body.getFixtureList().size; }
        return count;
    }

    private static int movementKey(Facing.Direction direction) {
        return switch (direction) {
            case UP -> Input.Keys.W;
            case DOWN -> Input.Keys.S;
            case LEFT -> Input.Keys.A;
            case RIGHT -> Input.Keys.D;
        };
    }

    private static void screenshot(String name) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        GAME.getEngine().getSystem(io.mindspice.lute.engine.systems.RenderSystem.class).update(0f);
        var image = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) { colors.add(image.getPixel(x, y)); }
            }
            require(colors.size() > 32, "Actual game screenshot contains rendered map/character detail");
            PixmapIO.writePNG(Gdx.files.absolute("/tmp/lute-phase3-game-" + name + ".png"), image, -1, true);
        } finally { image.dispose(); }
    }

    public static void dispose() {
        if (!initialized) { return; }
        initialized = false;
        GAME.dispose();
    }

    private static boolean oneHit(int before, int base, int after) {
        return after >= Math.max(0, before - Math.round(1.1f * base)) && after <= Math.max(0, before - Math.round(.9f * base));
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class LayerCheck implements AutoCloseable {
        private final CharacterCompositor compositor = new CharacterCompositor(GAME.getAssetService());
        private final SpriteBatch batch = new SpriteBatch();
        private final FrameBuffer reference = new FrameBuffer(Pixmap.Format.RGBA8888, 128, 128, false);
        private final FrameBuffer actual = new FrameBuffer(Pixmap.Format.RGBA8888, 128, 128, false);
        private final Set<Texture> weaponPages = new HashSet<>();
        private final Engine worldEngine = new Engine();
        private final OrthographicCamera worldCamera = new OrthographicCamera();
        private final FitViewport worldViewport = new FitViewport(128 * GameConsts.UNIT_SCALE, 128 * GameConsts.UNIT_SCALE, worldCamera);
        private final SpriteBatch worldBatch = new SpriteBatch();
        private final RenderSystem worldRenderer = new RenderSystem(worldBatch, worldViewport, worldCamera,
            GAME.getAssetService(), GameConsts.DEBUG_LAYERS);
        private int overhangPixels;
        private final Set<String> helmetOcclusionActions = new HashSet<>();
        private final Pixmap showcase = new Pixmap(576, 1728, Pixmap.Format.RGBA8888);
        private int frames;

        LayerCheck() {
            worldViewport.update(128, 128, false);
            worldEngine.addSystem(worldRenderer);
            showcase.setColor(0x253039ff);
            showcase.fill();
            showcase.setBlending(Pixmap.Blending.None);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 128, 128));
            for (var definition : List.of(CharacterAssets.CHARACTER, CharacterAssets.ENEMIES, CharacterAssets.FEET,
                CharacterAssets.BODY, CharacterAssets.HEAD, CharacterAssets.HANDS, CharacterAssets.WEAPONS)) {
                var atlas = GAME.getAssetService().get(TextureAtlas.class, definition.id());
                require(atlas.getRegions().size > 0 && atlas.getTextures().size > 0, "Real atlas loaded: " + definition.id());
                for (var region : atlas.getRegions()) {
                    require(atlas.getTextures().contains(region.getTexture()), "Region resolves to owned atlas page");
                }
            }
            require(GAME.getAssetService().get(TextureAtlas.class, CharacterAssets.WEAPONS.id()).getTextures().size > 1,
                "Regenerated real common weapon atlas exercises multiple texture pages");
        }

        long check(Entity player) {
            Animation2D animation = Animation2D.MAPPER.get(player);
            boolean mapActor = !animation.getAtlasKey().equals("player");
            Loadout loadout = Loadout.MAPPER.get(player);
            var equipment = loadout.getEquipment();
            var weapon = loadout.getWeapon();
            String suffix = animation.getAction() + "_" + animation.getFdirection().atlasKey;
            int index = animation.getFrameIndex();
            var atlasIds = List.of(animation.getAtlasId(), CharacterAssets.FEET.id(), CharacterAssets.BODY.id(),
                CharacterAssets.HEAD.id(), CharacterAssets.HANDS.id(), CharacterAssets.WEAPONS.id());
            String[] prefixes = {animation.getAtlasKey(), equipment.feet(), equipment.body(), equipment.head(), equipment.hands(), weapon.atlasKey()};
            TextureRegion[] selected = new TextureRegion[6];
            for (int layer = 0; layer < 6; layer++) {
                if (prefixes[layer] == null || (layer == 5 && loadout.getWeaponId() == null)) { continue; }
                var atlas = GAME.getAssetService().get(TextureAtlas.class, atlasIds.get(layer));
                var sequence = atlas.findRegions(prefixes[layer].isEmpty() ? suffix : prefixes[layer] + "/" + suffix);
                require(sequence.size == animation.getAnimation().getKeyFrames().length, "Real layers share frame counts: " + suffix);
                selected[layer] = sequence.get(index);
                require(selected[layer].getRegionWidth() == 128 && selected[layer].getRegionHeight() == 128,
                    "Actual source canvas is 128px, not logical 64px");
                if (layer == 5) { weaponPages.add(selected[layer].getTexture()); }
            }
            var base = Graphic.MAPPER.get(player).getRegion();
            require(base.getTexture() == selected[0].getTexture() && base.getU() == selected[0].getU()
                && base.getV() == selected[0].getV() && base.getU2() == selected[0].getU2() && base.getV2() == selected[0].getV2(),
                "AnimationSystem Graphic agrees with shared action/facing/frame: " + suffix + "/" + index);
            var camera = GAME.getCamera();
            if (mapActor) {
                var transform = Transform.MAPPER.get(player);
                worldCamera.position.set(transform.getPosition().x + transform.getSize().x * .5f,
                    transform.getPosition().y + transform.getSize().y * .5f, 0);
                worldCamera.update();
                camera = worldCamera;
            }
            compositor.compose(List.of(player), camera);
            TextureRegion composed = compositor.region(player);
            require(composed != null, "Real player remains visible to compositor");
            require(composed.getRegionWidth() == 128 && composed.getRegionHeight() == 128,
                "Composition retains full source canvas without shrinking");
            reference.begin();
            ScreenUtils.clear(0f, 0f, 0f, 0f);
            batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.begin();
            int[] order = animation.getFdirection() == Facing.Direction.UP
                ? new int[]{5, 0, 1, 2, 4, 3} : new int[]{0, 1, 2, 4, 3, 5};
            for (int layer : order) {
                if (selected[layer] != null) { batch.draw(selected[layer], 0, 0); }
            }
            batch.end();
            Pixmap expected = Pixmap.createFromFrameBuffer(0, 0, 128, 128);
            reference.end();
            actual.begin();
            ScreenUtils.clear(0f, 0f, 0f, 0f);
            batch.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.begin();
            batch.draw(composed, 0, 0);
            batch.end();
            Pixmap rendered = Pixmap.createFromFrameBuffer(0, 0, 128, 128);
            actual.end();
            try {
                boolean visible = false;
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        int want = expected.getPixel(x, y), got = rendered.getPixel(x, y);
                        visible |= (want & 255) != 0;
                        for (int shift = 0; shift < 32; shift += 8) {
                            if (Math.abs((want >>> shift & 255) - (got >>> shift & 255)) > 2) {
                                throw new AssertionError("Actual asset layer mismatch: " + weapon.id() + "/" + suffix + "/" + index
                                    + " at " + x + "," + y + ": expected " + Integer.toHexString(want) + " got " + Integer.toHexString(got));
                            }
                        }
                    }
                }
                require(visible, "Real layer reference is not empty");
                if (!mapActor) { helmetOcclusion(animation, selected, rendered); }
                worldFrame(player, expected);
                if (mapActor) { actorDepth(player, expected); }
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        if ((x < 32 || x >= 96 || y < 32 || y >= 96) && (expected.getPixel(x, y) & 255) != 0) { overhangPixels++; }
                    }
                }
                if (index == 4 && !animation.getAction().equals("death")) {
                    int weaponRow = switch (weapon.id()) { case "basic_sword" -> 0; case "basic_bow" -> 4; default -> 8; };
                    int actionRow = switch (animation.getAction()) { case "idle" -> 0; case "walk" -> 1; case "jab" -> 3; default -> 2; };
                    showcase(rendered, animation.getFdirection().ordinal(), weaponRow + actionRow);
                    if (weapon.id().equals("unarmed") && actionRow == 3) { showcase(rendered, animation.getFdirection().ordinal(), weaponRow + 2); }
                }
                frames++;
                var crc = new java.util.zip.CRC32();
                crc.update(rendered.getPixels());
                return crc.getValue();
            } finally {
                rendered.dispose();
                expected.dispose();
                GAME.getViewport().apply();
            }
        }

        private void helmetOcclusion(Animation2D animation, TextureRegion[] selected, Pixmap rendered) {
            if (animation.getFdirection() != Facing.Direction.UP || selected[3] == null || selected[4] == null) { return; }
            var helmet = layerPixels(selected[3]);
            var gloves = layerPixels(selected[4]);
            try {
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        int expected = helmet.getPixel(x, y);
                        if ((expected & 255) != 255 || (gloves.getPixel(x, y) & 255) == 0) { continue; }
                        helmetOcclusionActions.add(animation.getAction());
                        int actual = rendered.getPixel(x, y);
                        for (int shift = 0; shift < 32; shift += 8) {
                            require(Math.abs((expected >>> shift & 255) - (actual >>> shift & 255)) <= 2,
                                "Opaque upward helmet must cover gloves: " + animation.getAction() + "/"
                                    + animation.getFrameIndex() + " at " + x + "," + y);
                        }
                    }
                }
            } finally { helmet.dispose(); gloves.dispose(); }
        }

        private Pixmap layerPixels(TextureRegion region) {
            reference.begin();
            try {
                ScreenUtils.clear(0f, 0f, 0f, 0f);
                batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                    GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
                batch.begin();
                batch.draw(region, 0, 0);
                batch.end();
                return Pixmap.createFromFrameBuffer(0, 0, 128, 128);
            } finally {
                if (batch.isDrawing()) { batch.end(); }
                reference.end();
            }
        }

        private void worldFrame(Entity player, Pixmap expected) {
            var transform = Transform.MAPPER.get(player);
            worldCamera.position.set(transform.getPosition().x + transform.getSize().x * .5f,
                transform.getPosition().y + transform.getSize().y * .5f, 0);
            worldCamera.update();
            worldEngine.removeAllEntities();
            worldEngine.addEntity(new Entity().add(transform).add(Graphic.MAPPER.get(player))
                .add(CompositeCharacter.MAPPER.get(player)).add(Animation2D.MAPPER.get(player))
                .add(Loadout.MAPPER.get(player)));
            actual.begin();
            ScreenUtils.clear(0f, 0f, 0f, 0f);
            final Pixmap image;
            try {
                worldEngine.update(0);
                image = Pixmap.createFromFrameBuffer(0, 0, 128, 128);
            } finally { actual.end(); }
            try {
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        int want = expected.getPixel(x, y), got = image.getPixel(x, y);
                        for (int shift = 0; shift < 32; shift += 8) {
                            if (Math.abs((want >>> shift & 255) - (got >>> shift & 255)) > 2) {
                                throw new AssertionError("Native-scale world canvas differs at " + x + "," + y
                                    + ": expected " + Integer.toHexString(want) + " got " + Integer.toHexString(got));
                            }
                        }
                    }
                }
                require(true, "Production RenderSystem renders the full 128px source at native pixel scale about the logical center");
                var animation = Animation2D.MAPPER.get(player);
                if (animation.getAction().equals("idle") && animation.getFdirection() == Facing.Direction.RIGHT
                    && animation.getFrameIndex() == 0 && "basic_sword".equals(Loadout.MAPPER.get(player).getWeaponId())) {
                    PixmapIO.writePNG(Gdx.files.absolute("/tmp/lute-canvas-real-native.png"), image, -1, true);
                    image.setColor(0x66ccffff);
                    image.drawRectangle(32, 32, 64, 64);
                    var data = GAME.getGameData().playerData();
                    outline(image, data.collision().bounds(), 0x00ff00ff);
                    outline(image, data.hitboxBounds(), 0xff4444ff);
                    PixmapIO.writePNG(Gdx.files.absolute("/tmp/lute-canvas-bounds-review.png"), image, -1, true);
                }
            } finally { image.dispose(); }
        }

        private void actorDepth(Entity actor, Pixmap expected) {
            var transform = Transform.MAPPER.get(actor);
            var pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            int markerColor = 0xff00ffff;
            pixel.drawPixel(0, 0, markerColor);
            var texture = new Texture(pixel);
            pixel.dispose();
            try {
                for (int depth : new int[]{1, 3}) {
                    var position = transform.getPosition().cpy().mulAdd(transform.getSize(), -.5f);
                    var marker = new Entity().add(new Graphic(new TextureRegion(texture), com.badlogic.gdx.graphics.Color.WHITE.cpy()))
                        .add(new Transform(position, depth, transform.getSize().cpy().scl(2), new Vector2(1, 1), 0, 0));
                    worldEngine.addEntity(marker);
                    actual.begin();
                    Pixmap image;
                    try {
                        ScreenUtils.clear(0, 0, 0, 0);
                        worldEngine.update(0);
                        image = Pixmap.createFromFrameBuffer(0, 0, 128, 128);
                    } finally {
                        actual.end();
                        worldEngine.removeEntity(marker);
                    }
                    try {
                        boolean witnessed = false;
                        for (int y = 0; y < 128; y++) {
                            for (int x = 0; x < 128; x++) {
                                int actorPixel = expected.getPixel(x, y);
                                if ((actorPixel & 255) != 255 || actorPixel == markerColor) { continue; }
                                int got = image.getPixel(x, y);
                                int want = depth == 1 ? actorPixel : markerColor;
                                for (int shift = 0; shift < 32; shift += 8) {
                                    if (Math.abs((want >>> shift & 255) - (got >>> shift & 255)) > 2) {
                                        throw new AssertionError("World object depth did not bracket map actor at " + x + "," + y);
                                    }
                                }
                                witnessed = true;
                            }
                        }
                        require(witnessed, "Opaque actor pixels exercise real depth ordering");
                    } finally { image.dispose(); }
                }
            } finally { texture.dispose(); }
        }

        private static void outline(Pixmap image, io.mindspice.lute.engine.data.json.CharacterBounds bounds, int color) {
            image.setColor(color);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4;
                float cx = 32 + (bounds.x1() + bounds.x2()) * .5f, cy = 32 + (bounds.y1() + bounds.y2()) * .5f;
                float rx = (bounds.x2() - bounds.x1()) * .5f, ry = (bounds.y2() - bounds.y1()) * .5f;
                image.drawLine(Math.round(cx + rx * (float) Math.cos(a)), Math.round(cy + ry * (float) Math.sin(a)),
                    Math.round(cx + rx * (float) Math.cos(b)), Math.round(cy + ry * (float) Math.sin(b)));
            }
        }

        void deathFrames(Entity player) {
            var atlas = GAME.getAssetService().get(TextureAtlas.class, CharacterAssets.CHARACTER.id());
            var loadout = Loadout.MAPPER.get(player);
            for (String weapon : List.of("basic_sword", "basic_bow", "unarmed")) {
                require(loadouts().equipWeapon(player, weapon.equals("unarmed") ? null : weapon), "Death pose selects existing weapon layers");
                for (var direction : Facing.Direction.values()) {
                    var sequence = atlas.findRegions("player/death_" + direction.atlasKey);
                    require(sequence.size == 8, "Every death facing retains all authored frames");
                    for (int frame = 0; frame < sequence.size; frame++) {
                        var animation = new Animation2D(CharacterAssets.CHARACTER.id(), "player", Animation2D.AnimationType.ATTACK,
                            Animation.PlayMode.LOOP, 1, .125f);
                        animation.configureAttack(new Animation2D.Attack("death", 1, .125f, 0, ""));
                        animation.setAnimation(new Animation<>(.125f, sequence, Animation.PlayMode.LOOP), direction);
                        animation.incAndGetStateTime(frame * .125f);
                        var pose = new Entity().add(animation).add(loadout)
                            .add(Transform.MAPPER.get(player)).add(CompositeCharacter.MAPPER.get(player))
                            .add(new Graphic(sequence.get(frame), Graphic.MAPPER.get(player).getColor()));
                        check(pose);
                    }
                }
            }
        }

        private void showcase(Pixmap image, int column, int row) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int pixel = image.getPixel(x, y), alpha = pixel & 255;
                    int r = Math.min(255, (pixel >>> 24) + Math.round(37 * (1 - alpha / 255f)));
                    int g = Math.min(255, (pixel >>> 16 & 255) + Math.round(48 * (1 - alpha / 255f)));
                    int b = Math.min(255, (pixel >>> 8 & 255) + Math.round(57 * (1 - alpha / 255f)));
                    showcase.drawPixel(column * 144 + x + 8, row * 144 + 135 - y, r << 24 | g << 16 | b << 8 | 255);
                }
            }
        }

        @Override public void close() {
            var enlarged = new Pixmap(showcase.getWidth() * 2, showcase.getHeight() * 2, Pixmap.Format.RGBA8888);
            enlarged.setFilter(Pixmap.Filter.NearestNeighbour);
            enlarged.drawPixmap(showcase, 0, 0, showcase.getWidth(), showcase.getHeight(), 0, 0, enlarged.getWidth(), enlarged.getHeight());
            PixmapIO.writePNG(Gdx.files.absolute("/tmp/lute-phase3-characters.png"), enlarged);
            enlarged.dispose();
            showcase.dispose();
            worldEngine.removeAllEntities();
            worldRenderer.dispose();
            worldBatch.dispose();
            compositor.dispose();
            reference.dispose();
            actual.dispose();
            batch.dispose();
        }
    }
}
