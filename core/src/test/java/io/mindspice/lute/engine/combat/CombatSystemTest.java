package io.mindspice.lute.engine.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.animation.AnimationState;
import io.mindspice.lute.engine.assets.*;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.DataLoader;
import io.mindspice.lute.engine.data.json.AnimationData;
import io.mindspice.lute.engine.data.json.GameData;
import io.mindspice.lute.engine.data.json.WeaponsData;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.input.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.*;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CombatSystemTest {
    private static final float STEP = 1f / 60f;
    private static int checks;

    public static void main(String[] args) throws Exception {
        GdxNativesLoader.load();
        Box2D.init();
        Gdx.app = (com.badlogic.gdx.Application) Proxy.newProxyInstance(com.badlogic.gdx.Application.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.Application.class}, (proxy, method, values) -> null);
        animationWithoutCombat();
        animationIntervals();
        frameTiming();
        speedAndSkippedFrames();
        movementAndInput();
        contacts();
        cleanup();
        geometryAndHealth();
        factoryIntegration();
        atlasReload();
        System.out.println("CombatSystemTest: " + checks + " checks passed (native Box2D; no graphics context)");
    }

    private static void animationWithoutCombat() {
        var engine = new Engine();
        engine.addSystem(new FakeAnimationSystem());
        var entity = new Entity()
            .add(new Animation2D("OBJECTS", "object", Animation2D.AnimationType.ATTACK, Animation.PlayMode.LOOP, 1, .125f))
            .add(new Facing(Facing.Direction.DOWN)).add(new Graphic(new TextureRegion(), Color.WHITE));
        engine.addEntity(entity);
        engine.update(0); engine.update(.25f);
        check(Animation2D.MAPPER.get(entity).getAnimation() != null, "Animated objects do not require combat or sound metadata");
    }

    private static void animationIntervals() {
        var animation = new Animation2D("OBJECTS", "player", Animation2D.AnimationType.IDLE,
            Animation.PlayMode.LOOP, 1, .125f);
        animation.configureAttack(new Animation2D.Attack(2, .3f, 1, "sound"));
        animation.beginAttack();
        animation.setType(Animation2D.AnimationType.ATTACK);
        animation.setAnimation(new Animation<>(animation.getFrameDuration(), new TextureRegion(), new TextureRegion()),
            Facing.Direction.DOWN);
        animation.incAndGetStateTime(.149f);
        check(!animation.hasReachedFrame(1), "Weapon interval and speed delay the attack frame");
        animation.incAndGetStateTime(.002f);
        check(animation.hasReachedFrame(1), "Weapon interval and speed determine attack frame timing");
        animation.endAttack();
        near(.125f, animation.getFrameDuration(), "Player interval restored after attack");
    }

    private static void frameTiming() {
        try (var game = new Harness(1, 2, 3)) {
            var target = game.target(2, false);
            game.attack();
            check(game.combat().isAttacking() && game.combat().getSensor() == null, "Attack accepted before hit frame");
            game.update(.249f);
            check(game.combat().getSensor() == null, "No hitbox before frame 2");
            game.step(); game.combatSystem.update(0);
            near(100, Health.MAPPER.get(target).getCurrent(), "No early damage");
            game.update(.001f);
            check(game.combat().getSensor() != null, "Frame 2 activates hitbox at .25 seconds");
            game.events.update(0);
            check(game.sounds.isEmpty(), "No attack sound before frame 3");
            game.update(.125f);
            game.events.update(0);
            check(game.sounds.equals(List.of("weapon/test/attack")), "Frame 3 emits resolved attack sound once");
            game.update(0); game.events.update(0);
            check(game.sounds.size() == 1, "Same frame cannot repeat sound");
            game.update(.125f);
            check(!game.combat().isAttacking() && game.combat().getSensor() == null, "Animation completion removes hitbox");
            game.update(0);
            check(game.fsm().getCurrentState() == AnimationState.IDLE, "Animation returns to idle");
        }
        try (var game = new Harness(1, 0, 0)) {
            game.attack(); game.events.update(0);
            check(game.combat().getSensor() != null, "Frame 0 hitbox is immediate at animation start");
            check(game.sounds.size() == 1, "Frame 0 sound is immediate");
            game.update(0); game.events.update(0);
            check(game.sounds.size() == 1, "Zero delta does not repeat frame 0");
        }
    }

    private static void speedAndSkippedFrames() {
        for (float speed : new float[]{.5f, 1, 2}) {
            try (var game = new Harness(speed, 2, 1)) {
                game.animation().setSpeed(.75f);
                game.attack();
                near(speed, game.animation().getSpeed(), "Weapon speed applied");
                game.update(.25f / speed);
                check(game.combat().getSensor() != null, "Hit frame follows scaled animation clock");
                game.events.update(0);
                check(game.sounds.size() == 1, "Skipped sound frame is still emitted");
                game.update(.25f / speed);
                check(!game.combat().isAttacking(), "Duration is derived from animation and speed");
                game.update(0);
                near(.75f, game.animation().getSpeed(), "Previous playback speed restored");
            }
        }
        try (var game = new Harness(1, 2, 3)) {
            game.attack();
            game.update(1);
            game.events.update(0);
            check(!game.combat().isAttacking() && game.combat().getSensor() == null, "Large delta cannot leave stale hitbox");
            check(game.sounds.size() == 1, "Large delta crossing entire animation emits sound once");
        }
        try (var game = new Harness(1000, 0, 0)) {
            var target = game.target(1, false);
            game.attack();
            game.update(.0005f);
            check(!game.combat().isAttacking(), "Short animation expires without forcing a physics tick");
            game.step(); game.combatSystem.update(0);
            near(100, Health.MAPPER.get(target).getCurrent(), "Expired substep attack cannot hit later");
        }
    }

    private static void movementAndInput() {
        try (var game = new Harness(1, 0, 0)) {
            game.input(InputAction.MOVE_RIGHT, 1); game.update(0);
            check(game.fsm().getCurrentState() == AnimationState.WALK, "Movement enters walk");
            game.attack();
            long id = game.combat().getAttackId();
            game.input(InputAction.MOVE_RIGHT, 0); game.input(InputAction.MOVE_UP, 1);
            game.input(InputAction.ATTACK, 0); game.update(.125f);
            game.input(InputAction.ATTACK, 1); game.update(.125f);
            check(game.combat().getAttackId() == id, "Busy press does not restart attack");
            check(Facing.MAPPER.get(game.player).getDirection() == Facing.Direction.RIGHT, "Facing locked during attack");
            check(game.body().getLinearVelocity().isZero(), "Movement locked during attack");
            game.finish();
            check(game.fsm().getCurrentState() == AnimationState.WALK, "Held movement resumes walk");
            near(4, game.body().getLinearVelocity().y, "Held up movement resumes");
            game.input(InputAction.MOVE_UP, 0); game.update(0);
            check(game.fsm().getCurrentState() == AnimationState.IDLE, "Release returns to idle");
            game.attack();
            check(game.combat().getAttackId() == id + 1, "New press starts a new attack");
        }
    }

    private static void contacts() {
        for (var facing : Facing.Direction.values()) {
            try (var game = new Harness(1, 0, 0)) {
                Facing.MAPPER.get(game.player).setDirection(facing);
                var target = game.target(2, false);
                var center = switch (facing) {
                    case RIGHT -> new Vector2(2.125f, 1);
                    case UP -> new Vector2(1, 2.125f);
                    case LEFT -> new Vector2(-.125f, 1);
                    case DOWN -> new Vector2(1, -.125f);
                };
                Physic.MAPPER.get(target).getBody().setTransform(center, 0);
                game.body().setAwake(false);
                game.attack(); game.step();
                near(100, Health.MAPPER.get(target).getCurrent(), "Contact callback only queues damage");
                game.combatSystem.update(0);
                near(90, Health.MAPPER.get(target).getCurrent(), "Sleeping owner hits beyond sprite in " + facing);
                game.step(); game.combatSystem.update(0);
                near(90, Health.MAPPER.get(target).getCurrent(), "Multiple fixtures damage once per attack");
                game.finish(); game.attack(); game.step(); game.combatSystem.update(0);
                near(80, Health.MAPPER.get(target).getCurrent(), "New attack can hit same target");
            }
        }
        try (var game = new Harness(1, 0, 0)) {
            var sensor = game.target(1, true);
            var dead = game.target(1, false);
            Health.MAPPER.get(dead).damage(100);
            var scenery = game.target(1, false); scenery.remove(Health.class);
            game.attack(); game.step(); game.combatSystem.update(0);
            near(100, Health.MAPPER.get(sensor).getCurrent(), "Sensor target excluded");
            near(0, Health.MAPPER.get(dead).getCurrent(), "Dead target excluded");
            near(100, Health.MAPPER.get(game.player).getCurrent(), "Owner excluded");
        }
    }

    private static void cleanup() {
        for (int hitFrame : new int[]{0, 2}) {
            for (String removal : new String[]{"death", "entity", "combat", "animation", "system", "target"}) {
                try (var game = new Harness(1, hitFrame, 0)) {
                    var target = game.target(1, false);
                    var combat = game.combat();
                    game.attack(); game.step();
                    switch (removal) {
                        case "death" -> Health.MAPPER.get(game.player).damage(100);
                        case "entity" -> game.engine.removeEntity(game.player);
                        case "combat" -> game.player.remove(Combat.class);
                        case "animation" -> game.player.remove(Animation2D.class);
                        case "system" -> game.engine.removeSystem(game.combatSystem);
                        case "target" -> game.engine.removeEntity(target);
                    }
                    game.update(0);
                    near(100, Health.MAPPER.get(target).getCurrent(), "Stale damage discarded after " + removal);
                    if (!removal.equals("target")) {
                        check(!combat.isAttacking() && combat.getSensor() == null, "Cleanup before/after activation: " + removal);
                    }
                }
            }
        }
    }

    private static void geometryAndHealth() {
        var anchor = new Vector2(1, 1);
        for (var shape : BoundsType.values()) {
            var weapon = weapon(1, 0, 0, shape);
            for (var facing : Facing.Direction.values()) {
                var vertices = weapon.vertices(anchor, facing);
                check(vertices.length >= 3 && vertices.length <= 8, "Native vertex limit");
                var polygon = new PolygonShape();
                try { polygon.set(vertices); check(polygon.getVertexCount() == vertices.length, "Valid native polygon"); }
                finally { polygon.dispose(); }
            }
            for (var bounds : new CombatWeapon.Bounds[]{new CombatWeapon.Bounds(0, 0, .001f, .001f),
                new CombatWeapon.Bounds(0, 0, 1e24f, 64), new CombatWeapon.Bounds(0, 0, 1e15f, 1e15f)}) {
                var unsafe = new CombatWeapon.Weapon("bad", 10, 0, new Animation2D.Attack(1, .125f, 0, "sound"),
                    "hit", shape, bounds);
                rejects(() -> unsafe.validateGeometry(anchor), "Unsafe geometry");
            }
        }
        var health = new Health(100);
        health.damage(25); near(75, health.getCurrent(), "Damage subtracts");
        health.damage(Float.MAX_VALUE); near(0, health.getCurrent(), "Damage clamps");
        check(!health.isAlive(), "Zero health dead");
    }

    private static void factoryIntegration() throws Exception {
        var files = Gdx.files;
        var field = GameCore.class.getDeclaredField("gameData"); field.setAccessible(true);
        var previous = field.get(GameCore.INSTANCE);
        var previousEngine = bindEngine(null);
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, args) -> {
                if (method.getName().equals("internal")) { return new FileHandle("assets/" + args[0]); }
                throw new UnsupportedOperationException(method.getName());
            });
        var atlas = new TextureAtlas();
        var atlasFile = new FileHandle("assets/atlases/test_tiled.atlas");
        for (var region : new TextureAtlas.TextureAtlasData(atlasFile, atlasFile.parent(), false).getRegions()) {
            atlas.addRegion(region.name, new DimensionTexture((int) region.page.width, (int) region.page.height),
                region.left, region.top, region.width, region.height).index = region.index;
        }
        var world = new World(Vector2.Zero, true);
        TextureAtlas[] loaded = {atlas};
        var assets = new AssetService(FileHandle::new) {
            @Override public <T> T get(Class<T> type, String id) {
                check(type == TextureAtlas.class && id.equals("OBJECTS"), "Factory uses logical atlas key");
                return type.cast(loaded[0]);
            }
        };
        var input = new InputManager(InputMap.of());
        var engine = new Engine();
        try {
            var data = DataLoader.loadGameData();
            field.set(GameCore.INSTANCE, data);
            bindEngine(engine);
            engine.addSystem(new EventSystem());
            engine.addSystem(new AnimationPlayerSystem());
            engine.addSystem(new AnimationSystem(assets));
            var combat = new CombatSystem(world); engine.addSystem(combat);
            var factory = new EntityFactory(world, assets, input); engine.addSystem(factory);
            var map = new MapAssets(Asset.Map.of("test", "test_tiled.tmx"), "",
                Asset.Atlas.of("OBJECTS", "test_tiled.atlas"), Map.of(), Map.of());
            var tile = new StaticTiledMapTile(new TextureRegion(new DimensionTexture(160, 256)));
            tile.getObjects().add(new RectangleMapObject(0, 0, 32, 32));
            var object = new TiledMapTileMapObject(tile, false, false);
            object.getProperties().put("animationType", "ATTACK"); // Legacy properties cannot select playback.
            var objectProps = new AnimatedPhysicObjectProps(new AnimationData(.35f, "old_tree1/old_tree1", 0, .1f));
            var animatedObject = factory.getAnimatedPhysicObjectEntity(objectProps, object, map);
            check(Animation2D.MAPPER.get(animatedObject).getType() == Animation2D.AnimationType.DEFAULT,
                "Animated environmental objects start in DEFAULT");
            var original = data.weaponsData().requireWeapon(data.playerData().equippedWeapon());
            for (boolean hit : new boolean[]{true, false}) {
                for (int invalid : new int[]{4, Integer.MAX_VALUE}) {
                    var stats = original.stats();
                    var anim = original.animation();
                    var bad = new WeaponsData.WeaponData(
                        new WeaponsData.WeaponData.Stats(stats.damage(), stats.cost(), hit ? invalid : 0),
                        new AnimationData(anim.speed(), anim.atlasKey(), hit ? 0 : invalid, anim.frameInterval()),
                        original.sounds(), original.bounds());
                    field.set(GameCore.INSTANCE, new GameData(data.playerData(),
                        new WeaponsData("NONE", Map.of(data.playerData().equippedWeapon(), bad))));
                    rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), map), "Out of range cue frame");
                }
            }
            var badBounds = new WeaponsData.WeaponData.Bounds(BoundsType.RECT, 0, 0, .001f, .001f);
            var badGeometry = new WeaponsData.WeaponData(original.stats(), original.animation(), original.sounds(), badBounds);
            field.set(GameCore.INSTANCE, new GameData(data.playerData(),
                new WeaponsData("NONE", Map.of(data.playerData().equippedWeapon(), badGeometry))));
            rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), map), "Unsafe Box2D geometry");
            var stats = original.stats();
            var anim = original.animation();
            var cues = new WeaponsData.WeaponData(
                new WeaponsData.WeaponData.Stats(stats.damage(), stats.cost(), 2),
                new AnimationData(anim.speed(), anim.atlasKey(), 3, anim.frameInterval()),
                original.sounds(), original.bounds());
            field.set(GameCore.INSTANCE, new GameData(data.playerData(),
                new WeaponsData("NONE", Map.of(data.playerData().equippedWeapon(), cues))));
            var player = factory.getPlayerEntity(new Vector2(320, 640), map);
            check(player.getComponent(Player.class) != null, "Real player factory declares map-sensor participation");
            var transform = Transform.MAPPER.get(player);
            near(320 * GameConsts.UNIT_SCALE, transform.getPosition().x + transform.getSize().x / 2,
                "Real factory centers initial PLAYER point X");
            near(640 * GameConsts.UNIT_SCALE, transform.getPosition().y + transform.getSize().y / 2,
                "Real factory centers initial PLAYER point Y without a second flip");
            check(Physic.MAPPER.get(player).getBody().getPosition().equals(transform.getPosition())
                && Physic.MAPPER.get(player).getPrevPos().equals(transform.getPosition()),
                "Real initial player body and interpolation share the centered transform");
            check(Animation2D.MAPPER.get(player).getType() == Animation2D.AnimationType.IDLE,
                "Player factory starts in IDLE");
            engine.addEntity(player);
            var animation = Animation2D.MAPPER.get(player);
            var state = Combat.MAPPER.get(player);
            near(100, Health.MAPPER.get(player).getCurrent(), "Factory player stats");
            check(state.getCurrentWeapon().animation().soundId().equals("weapon/sword/attack"), "Factory passes resolved sound ID");
            for (var direction : Facing.Direction.values()) {
                Facing.MAPPER.get(player).setDirection(direction);
                state.requestAttack(); engine.update(0); engine.update(0);
                check(animation.getAnimation().getKeyFrames().length == 4, "Real atlas attack frames " + direction);
                int hitFrame = state.getCurrentWeapon().hitFrame();
                float speed = state.getCurrentWeapon().animation().speed();
                check((state.getSensor() != null) == (hitFrame == 0), "Real weapon frame-zero hitbox");
                if (hitFrame > 0) {
                    engine.update((hitFrame - .5f) * animation.getFrameDuration() / speed);
                    check(state.getSensor() == null, "Real weapon waits until hit frame");
                    engine.update(.51f * animation.getFrameDuration() / speed);
                    check(state.getSensor() != null, "Real atlas hit frame");
                }
                engine.update(1f / speed); engine.update(0);
                check(!state.isAttacking(), "Real animation completion");
                check(animation.getAnimation().getKeyFrames().length == 6, "Idle frames restored");
            }
            check(animation.getMinimumAttackFrames() == 4, "Factory carries both cue requirements");
            var shortAtlas = new TextureAtlas();
            var replacement = new TextureAtlas();
            try {
                for (var frame : atlas.getRegions()) {
                    replacement.addRegion(frame.name, frame).index = frame.index;
                    if (!frame.name.equals("player/attack_up") || frame.index < 3) {
                        shortAtlas.addRegion(frame.name, frame).index = frame.index;
                    }
                }
                var previousAnimation = animation.getAnimation();
                loaded[0] = shortAtlas;
                rejects(() -> engine.update(0), "Reload with too-short non-facing attack animation");
                check(animation.getAnimation() == previousAnimation, "Invalid reload does not install animation");
                rejects(() -> engine.update(0), "Invalid reload remains unaccepted on retry");
                loaded[0] = replacement;
                engine.update(0);
                check(animation.getAnimation() != previousAnimation, "Valid replacement reloads existing entity");
                Facing.MAPPER.get(player).setDirection(Facing.Direction.UP);
                state.requestAttack(); engine.update(0); engine.update(0);
                check(animation.getAnimation().getKeyFrames().length == 4, "Valid replacement supports attack playback");
            } finally { shortAtlas.dispose(); replacement.dispose(); }
        } finally {
            engine.removeAllEntities(); world.dispose(); assets.dispose(); atlas.dispose(); input.dispose();
            field.set(GameCore.INSTANCE, previous); bindEngine(previousEngine); Gdx.files = files;
        }
    }

    private static void atlasReload() {
        var first = new TextureAtlas(); var second = new TextureAtlas();
        first.addRegion("loop", new DimensionTexture(16, 16), 0, 0, 16, 16);
        second.addRegion("loop", new DimensionTexture(32, 32), 0, 0, 32, 32);
        TextureAtlas[] loaded = {first};
        var assets = new AssetService(FileHandle::new) {
            @Override public <T> T get(Class<T> type, String id) { return type.cast(loaded[0]); }
        };
        var engine = new Engine(); engine.addSystem(new AnimationSystem(assets));
        var player = new Entity().add(new Animation2D("OBJECTS", "loop", Animation2D.AnimationType.DEFAULT,
            Animation.PlayMode.LOOP, 1, .125f)).add(new Facing(Facing.Direction.DOWN)).add(new Graphic(new TextureRegion(), Color.WHITE));
        engine.addEntity(player);
        try {
            engine.update(0); engine.update(.125f);
            near(.125f, Animation2D.MAPPER.get(player).incAndGetStateTime(0), "Playback advances");
            var old = Animation2D.MAPPER.get(player).getAnimation();
            loaded[0] = second; engine.update(0);
            check(Animation2D.MAPPER.get(player).getAnimation() != old, "Atlas identity change rebuilds cache");
            near(0, Animation2D.MAPPER.get(player).incAndGetStateTime(0), "Reload resets time");
        } finally { first.dispose(); second.dispose(); assets.dispose(); }
    }

    private static CombatWeapon.Weapon weapon(float speed, int hit, int sound, BoundsType shape) {
        return new CombatWeapon.Weapon("test", 10, hit, new Animation2D.Attack(speed, .125f, sound, "weapon/test/attack"),
            "weapon/test/hit", shape, new CombatWeapon.Bounds(32, 0, 72, 64));
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0002f, message + ": expected " + expected + ", got " + actual);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { checks++; return; }
        throw new AssertionError("Accepted " + message);
    }
    private static Engine bindEngine(Engine engine) {
        try {
            var field = GameCore.class.getDeclaredField("engine");
            field.setAccessible(true);
            var previous = (Engine) field.get(GameCore.INSTANCE);
            field.set(GameCore.INSTANCE, engine);
            return previous;
        } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    }
    private static final class DimensionTexture extends Texture {
        private final int width, height;
        DimensionTexture(int width, int height) { super(); this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void dispose() { }
    }
    private static final class FakeAnimationSystem extends AnimationSystem {
        private static final TextureAtlas ATLAS = new TextureAtlas();
        FakeAnimationSystem() {
            super(new AssetService(FileHandle::new) {
                @Override public <T> T get(Class<T> type, String id) { return type.cast(ATLAS); }
            });
        }
        @Override public void updateAnimation(Animation2D component, Facing.Direction direction) {
            int count = component.getType() == Animation2D.AnimationType.ATTACK ? 4 : 6;
            var frames = new TextureRegion[count];
            for (int i = 0; i < count; i++) { frames[i] = new TextureRegion(); }
            component.setAnimation(new Animation<>(component.getFrameDuration(), frames), direction);
        }
    }
    private static final class Harness implements AutoCloseable {
        final World world = new World(Vector2.Zero, true);
        final Engine engine = new Engine();
        final EventSystem events = new EventSystem();
        final CombatSystem combatSystem = new CombatSystem(world);
        final List<String> sounds = new ArrayList<>();
        final Entity player = new Entity();
        final Engine previousEngine;
        Harness(float speed, int hit, int sound) {
            previousEngine = bindEngine(engine);
            engine.addSystem(events);
            events.subscribe(Event.Sound.Play.class, e -> sounds.add(e.soundId()));
            engine.addSystem(new ControllerSystem()); engine.addSystem(new FacingSystem());
            engine.addSystem(new AnimationPlayerSystem()); engine.addSystem(new FakeAnimationSystem());
            engine.addSystem(combatSystem); engine.addSystem(new PhysicMoveSystem());
            engine.addSystem(new PhysicSystem(world, STEP));
            var definition = new BodyDef(); definition.type = BodyDef.BodyType.DynamicBody;
            var body = world.createBody(definition); body.setUserData(player);
            fixture(body, .15f, .1f, new Vector2(1, .15f), false);
            player.add(new Physic(body, new Vector2())).add(new Transform(new Vector2(), 0, new Vector2(2, 2), new Vector2(1, 1), 0, 0))
                .add(new Facing(Facing.Direction.RIGHT)).add(new Move(4)).add(new Controller())
                .add(new Combat(weapon(speed, hit, sound, BoundsType.SEMICIRCLE))).add(new Health(100))
                .add(new Animation2D("OBJECTS", "player", Animation2D.AnimationType.IDLE, Animation.PlayMode.LOOP, 1, .125f))
                .add(new Graphic(new TextureRegion(), Color.WHITE)).add(new AnimationPlayer(player));
            engine.addEntity(player); update(0);
        }
        Entity target(int count, boolean sensor) {
            var entity = new Entity(); var definition = new BodyDef(); definition.position.set(1.7f, 1);
            var body = world.createBody(definition); body.setUserData(entity);
            for (int i = 0; i < count; i++) { fixture(body, .08f, .08f, new Vector2(0, i * .03f), sensor); }
            entity.add(new Physic(body, new Vector2(1.7f, 1))).add(new Health(100));
            engine.addEntity(entity); return entity;
        }
        void input(InputAction action, int state) { Controller.MAPPER.get(player).getInputListener().offer(action, state); }
        void attack() { input(InputAction.ATTACK, 0); update(0); input(InputAction.ATTACK, 1); update(0); update(0); }
        void update(float delta) { engine.update(delta); }
        void step() { world.step(STEP, 6, 2); }
        void finish() { update(1); update(0); check(!combat().isAttacking(), "Attack finishes"); }
        Body body() { return Physic.MAPPER.get(player).getBody(); }
        Combat combat() { return Combat.MAPPER.get(player); }
        Animation2D animation() { return Animation2D.MAPPER.get(player); }
        com.badlogic.gdx.ai.fsm.DefaultStateMachine<Entity, AnimationState> fsm() {
            return AnimationPlayer.MAPPER.get(player).getAnimationFsm();
        }
        @Override public void close() {
            engine.removeSystem(combatSystem); engine.removeAllEntities(); world.dispose(); bindEngine(previousEngine);
        }
    }
    private static void fixture(Body body, float width, float height, Vector2 center, boolean sensor) {
        var shape = new PolygonShape();
        try {
            shape.setAsBox(width, height, center, 0);
            var definition = new FixtureDef(); definition.shape = shape; definition.isSensor = sensor;
            body.createFixture(definition);
        } finally { shape.dispose(); }
    }
}
