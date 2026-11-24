package io.mindspice.lute.engine.combat;

import io.mindspice.lute.engine.data.json.AttackDamage;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.behaviorgraph.FocusState;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;
import io.mindspice.lute.engine.animation.AnimationState;
import io.mindspice.lute.engine.assets.*;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.data.json.PlayerData;
import io.mindspice.lute.engine.data.json.DataLoader;
import io.mindspice.lute.engine.data.json.AnimationData;
import io.mindspice.lute.engine.data.json.GameData;
import io.mindspice.lute.engine.data.json.CollisionData;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;
import io.mindspice.lute.engine.data.json.WeaponsData;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.*;
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
    private static final Offset ZERO = new Offset(0, 0);
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
        steeredMeleeContacts();
        sameUpdateMeleeReplacement();
        cooldownCancelsMeleeRecovery();
        attackSlotsAndLoadout();
        testWeaponCycle();
        heldBow();
        enemyBow(0f);
        enemyBow(4f);
        cardinalBowShots();
        ArrowFlightTest.run();
        cooldownBetweenBowAndMelee();
        queuedLethalHitCancelsBowRelease();
        bowDamageByDrawFrame();
        independentAttacksAndGeometry(Actor.Role.PLAYER, Actor.Role.ENEMY);
        independentAttacksAndGeometry(Actor.Role.ENEMY, Actor.Role.PLAYER);
        committedMeleeHit();
        playerKillCredit();
        contacts();
        characterFixturesAndContacts();
        cleanup();
        geometryAndHealth();
        if (!List.of(args).contains("--synthetic")) { factoryIntegration(); }
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
        animation.configureAttack(new Animation2D.Attack("jab", 2, .3f, 1, "sound"));
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
            check(game.sounds.equals(List.of("weapon/test/primary/attack")), "Frame 3 emits resolved attack sound once");
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
            var start = game.body().getPosition().cpy();
            game.input(InputAction.ATTACK_PRIMARY, 0); game.update(.125f);
            game.input(InputAction.ATTACK_PRIMARY, 1);
            game.input(InputAction.MOVE_RIGHT, 0); game.input(InputAction.MOVE_UP, 1);
            game.update(.125f);
            check(game.combat().getAttackId() == id, "Busy press does not restart attack");
            check(Facing.MAPPER.get(game.player).getDirection() == Facing.Direction.UP
                && game.animation().getFdirection() == Facing.Direction.UP
                && game.combat().getAttackFacing() == Facing.Direction.UP, "Busy melee turns toward held movement");
            check(game.animation().getFrameIndex() == 2, "Turning advances the existing attack clock");
            check(game.sounds.size() == 1, "Turning does not repeat the attack sound");
            check(game.body().getLinearVelocity().isZero() && game.body().getPosition().equals(start),
                "Held movement does not translate the attacker through physics steps");
            game.finish();
            check(game.fsm().getCurrentState() == AnimationState.WALK, "Held movement resumes walk");
            near(4, game.body().getLinearVelocity().y, "Held up movement resumes");
            game.input(InputAction.MOVE_UP, 0); game.update(0);
            check(game.fsm().getCurrentState() == AnimationState.IDLE, "Release returns to idle");
            game.attack();
            check(game.combat().getAttackId() == id + 1, "New press starts a new attack");
        }
    }

    private static void steeredMeleeContacts() {
        try (var game = new Harness(1, 1, 0)) {
            var right = game.target(2, false);
            Physic.MAPPER.get(right).getBody().setTransform(2.125f, 1, 0);
            var up = game.target(2, false);
            Physic.MAPPER.get(up).getBody().setTransform(1, 2.125f, 0);
            game.attack();
            game.input(InputAction.MOVE_UP, 1); game.update(.125f); game.update(0);
            check(game.animation().getFrameIndex() == 1, "Turning on the hit frame keeps the authored timing");
            damageWithin(10, up, "First sensor points in the new direction");
            near(100, Health.MAPPER.get(right).getCurrent(), "No hit in the original direction");

            var attackId = game.combat().getAttackId();
            game.input(InputAction.MOVE_UP, 0); game.input(InputAction.MOVE_RIGHT, 1);
            game.update(STEP);
            // The rightward contact is now queued; turn again before it is applied.
            game.input(InputAction.MOVE_RIGHT, 0); game.input(InputAction.MOVE_UP, 1);
            game.update(STEP); game.update(0);
            damageWithin(10, right, "An already-active sensor follows the turn and its queued hit lands");
            damageWithin(10, up, "Turning back over an already-hit target cannot hit it again");
            check(game.combat().getAttackId() == attackId && game.body().getFixtureList().size == 2,
                "Turns retain one attack and one sensor");
            game.update(.5f - .125f - 2 * STEP + .001f);
            check(!game.combat().isAttacking() && game.combat().getSensor() == null,
                "Turns do not extend playback or leave a sensor behind");
        }
    }

    private static void sameUpdateMeleeReplacement() {
        for (boolean attackFirst : new boolean[]{true, false}) {
            try (var game = new Harness(1, 0, 0)) {
                game.attack(); game.update(.125f);
                var start = game.body().getPosition().cpy();
                if (attackFirst) { game.input(InputAction.ATTACK_SECONDARY, 1); }
                game.input(InputAction.MOVE_LEFT, 1);
                if (!attackFirst) { game.input(InputAction.ATTACK_SECONDARY, 1); }
                game.update(STEP);
                check(game.combat().getAttackId() == 2 && game.combat().getAttackFacing() == Facing.Direction.LEFT,
                    "Replacement uses the final held direction in either input order");
                check(game.combat().getSensor() == null && game.animation().getAction().equals("slash")
                    && game.animation().getFdirection() == Facing.Direction.LEFT && game.animation().getFrameIndex() == 1,
                    "Previous pose turns without restarting; replacement removes its sensor");
                game.update(STEP);
                check(game.animation().getAction().equals("jab") && game.animation().getFrameIndex() == 0
                    && game.animation().getFdirection() == Facing.Direction.LEFT && game.combat().getSensor() != null,
                    "Accepted replacement starts facing left from frame zero on the next pass");
                check(game.body().getLinearVelocity().isZero() && game.body().getPosition().equals(start),
                    "No movement between attacks");
            }
        }
    }

    private static void cooldownCancelsMeleeRecovery() {
        try (var game = new Harness(1, 0, 0)) {
            var target = game.target(1, false);
            game.attack();
            long id = game.combat().getAttackId();
            check(game.combat().getSensor() != null, "First melee attack has a sensor");
            game.input(InputAction.ATTACK_PRIMARY, 0); game.update(0);
            game.input(InputAction.ATTACK_PRIMARY, 1); game.update(.125f);
            check(game.combat().getAttackId() == id, "Primary cannot restart during its cooldown");
            game.update(.125f);
            check(game.combat().getAttackId() == id && !game.animation().isFinished(), "An early press is not buffered");
            check(game.combat().isCoolingDown("test", Combat.AttackSlot.PRIMARY), "Primary is still cooling down");
            game.input(InputAction.ATTACK_SECONDARY, 1); game.update(0);
            check(game.combat().getAttackId() == id + 1 && game.combat().getSensor() == null,
                "Replacement tears down the old sensor before the next animation pass");
            check(game.animation().getAction().equals("slash"), "Old rendered frame keeps its original action");
            game.update(0); game.step(); game.update(0);
            check(game.animation().getAction().equals("jab") && game.animation().getFrameIndex() == 0,
                "Replacement starts the selected action from frame zero");
            damageWithin(20, target, "Each attack can hit the same target once");
            game.events.update(0);
            check(game.sounds.size() == 2, "Replacement gets its own sound cue");
        }
        try (var game = new Harness(4, 0, 0)) {
            game.attack(); game.update(.125f); game.update(0);
            check(!game.combat().isAttacking() && game.combat().isCoolingDown("test", Combat.AttackSlot.PRIMARY),
                "A faster animation can finish before its cooldown");
            game.attack();
            check(game.combat().getAttackId() == 1, "Idle animation does not bypass cooldown");
            var first = Loadout.MAPPER.get(game.player).getWeapon();
            var otherAttack = attack("other", "slash", 4, 0, 0, BoundsType.SEMICIRCLE, CollisionType.MELEE);
            var other = new CombatWeapon.Weapon("other", "melee/other", otherAttack, otherAttack);
            game.player.add(new Loadout(Loadout.MAPPER.get(game.player).getEquipment(), first.id(),
                Map.of(first.id(), first, other.id(), other), equipment -> {}));
            check(game.loadouts.equipWeapon(game.player, other.id()), "Idle player can switch weapons during cooldown");
            game.attack();
            check(game.combat().getActiveAttack() == otherAttack, "Same slot on another weapon is ready");
            game.update(.125f); game.update(0);
            check(game.loadouts.equipWeapon(game.player, first.id()), "Switch back after the other attack ends");
            game.attack();
            check(game.combat().getAttackId() == 2, "Switching away and back does not reset the first cooldown");
            game.input(InputAction.ATTACK_PRIMARY, 0); game.update(0);
            game.input(InputAction.ATTACK_PRIMARY, 1); game.update(.125f);
            check(game.combat().getAttackId() == 3, "First cooldown expires while the other is still running");
        }
    }

    private static void attackSlotsAndLoadout() {
        var sword = new CombatWeapon.Weapon("basic_sword", "melee/basic_sword",
            attack("basic_sword", "slash", 1, 2, 1, BoundsType.SEMICIRCLE, CollisionType.MELEE),
            attack("basic_sword", "jab", 1, 2, 1, BoundsType.TRIANGLE, CollisionType.MELEE));
        var bow = new CombatWeapon.Weapon("basic_bow", "ranged/basic_bow",
            attack("basic_bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE),
            attack("basic_bow", "jab", 1, 2, 1, BoundsType.TRIANGLE, CollisionType.MELEE));
        var jab = attack("unarmed", "jab", 1, 2, 1, BoundsType.RECT, CollisionType.MELEE);
        var unarmed = new CombatWeapon.Weapon("unarmed", "NONE", jab, jab);
        var catalog = Map.of("basic_sword", sword, "basic_bow", bow, "unarmed", unarmed);
        var equipment = new Loadout.Equipment("basic", "basic", "basic", "basic");
        var changed = new Loadout.Equipment("other", "other", "other", "other");
        var passive = new Entity().add(new Loadout(equipment, "basic_sword", catalog, value -> {}));
        var loadouts = new LoadoutSystem();
        check(loadouts.equip(passive, changed) && loadouts.equipWeapon(passive, "basic_bow"),
            "Entities without combat can change equipped items");
        check(Loadout.MAPPER.get(passive).getEquipment().equals(changed)
            && Loadout.MAPPER.get(passive).getWeapon() == bow && Combat.MAPPER.get(passive) == null,
            "Equipped items belong to loadout without a combat component");
        rejects(() -> new Loadout(equipment, "missing", catalog, value -> {}), "Unknown initial weapon");
        for (var weapon : catalog.values()) {
            for (var action : new InputAction[]{InputAction.ATTACK_PRIMARY, InputAction.ATTACK_SECONDARY}) {
                try (var game = new Harness(1, 0, 0)) {
                    var loadout = new Loadout(equipment, weapon.id(), catalog, value -> {
                        if (!value.equals(equipment) && !value.equals(changed)) {
                            throw new IllegalArgumentException("Unknown equipment");
                        }
                    });
                    game.player.add(loadout);
                    var target = game.target(2, false);
                    var expected = action == InputAction.ATTACK_PRIMARY ? weapon.primary() : weapon.secondary();
                    game.attack(action);
                    check(game.combat().getActiveAttack() == expected, "Input selects immutable attack snapshot: " + action);
                    check(game.combat().isCoolingDown(weapon.id(),
                        action == InputAction.ATTACK_PRIMARY ? Combat.AttackSlot.PRIMARY : Combat.AttackSlot.SECONDARY),
                        "Selected attack starts its own cooldown");
                    check(!game.loadouts.equipWeapon(game.player, "basic_sword") && !game.loadouts.equip(game.player, changed),
                        "Weapon and equipment changes rejected during attack");
                    check(loadout.getEquipment().equals(equipment) && loadout.getWeapon() == weapon
                        && game.combat().getActiveAttack() == expected, "Rejected switch preserves equipment, weapon and snapshot");
                    check(!game.loadouts.equipWeapon(game.player, "missing"), "Busy switch is rejected before resolving an ID");
                    int fixtures = game.body().getFixtureList().size;
                    for (int frame = 1; frame < game.animation().getAnimation().getKeyFrames().length; frame++) {
                        game.update(.125f);
                        game.step(); game.combatSystem.update(0);
                        check(game.combat().isAttacking(), "Attack plays through frame " + frame);
                        if (expected.collisionType() == CollisionType.PROJECTILE) {
                            check(game.combat().getSensor() == null && game.body().getFixtureList().size == fixtures,
                                "Bow shot creates no sensor throughout animation");
                            near(100, Health.MAPPER.get(target).getCurrent(), "Bow shot deals no contact damage");
                            check(game.engine.getEntities().size() == 2, "Bow shot spawns no projectile entity");
                        }
                    }
                    if (expected.collisionType() == CollisionType.MELEE) {
                        damageWithin(10, target, "Selected melee attack deals damage once");
                    }
                    game.events.update(0);
                    check(game.sounds.stream().filter(s -> s.equals(expected.animation().soundId())).count() == 1,
                        "Selected attack sound emitted once");
                    game.update(.125f); game.update(0);
                    check(!game.combat().isAttacking() && game.combat().getSensor() == null, "Selected attack completes normally");
                    check(game.loadouts.equip(game.player, changed) && loadout.getEquipment().equals(changed), "Inactive equipment switch accepted");
                    check(game.loadouts.equipWeapon(game.player, "basic_bow") && loadout.getWeaponId().equals("basic_bow")
                        && loadout.getWeapon() == bow, "Inactive bow switch accepted");
                    check(game.loadouts.equipWeapon(game.player, null) && loadout.getWeaponId() == null
                        && loadout.getWeapon() == unarmed, "Null switch selects explicit unarmed catalog entry");
                    rejects(() -> game.loadouts.equipWeapon(game.player, "missing"), "Unknown loadout weapon");
                    check(loadout.getWeapon() == unarmed && loadout.getWeaponId() == null,
                        "Invalid switch preserves current weapon");
                    game.combat().requestAttack(Combat.AttackSlot.SECONDARY);
                    game.combat().requestAttack(Combat.AttackSlot.PRIMARY);
                    check(!game.loadouts.equipWeapon(game.player, "basic_sword") && !game.loadouts.equip(game.player, equipment),
                        "Pending request locks loadout before attack begins");
                    check(loadout.getWeapon() == unarmed && loadout.getEquipment().equals(changed),
                        "Pending request preserves equipped items");
                    check(game.combat().consumeAttackRequest() == Combat.AttackSlot.SECONDARY,
                        "Pending request retains selected slot");
                    check(game.combat().consumeAttackRequest() == null, "Consumed request is cleared");
                    check(game.loadouts.equipWeapon(game.player, "basic_sword") && game.loadouts.equip(game.player, equipment),
                        "Switch unlocked after request consumed");
                }
            }
        }
    }

    private static void testWeaponCycle() {
        try (var game = new Harness(1, 0, 0)) {
            var sword = Loadout.MAPPER.get(game.player).getWeapon();
            var shot = attack("bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE);
            var bow = new CombatWeapon.Weapon("bow", "ranged/bow", shot, shot);
            var loadout = new Loadout(Loadout.MAPPER.get(game.player).getEquipment(), sword.id(),
                Map.of(sword.id(), sword, bow.id(), bow), equipment -> {});
            game.player.add(loadout).add(new BowAim(1, 1)).add(new Controller(List.of(sword.id(), bow.id())));
            var aim = BowAim.MAPPER.get(game.player);

            game.input(InputAction.CYCLE_WEAPON, 1);
            game.input(InputAction.ATTACK_PRIMARY, 1);
            game.update(0);
            check(loadout.getWeapon() == bow && aim.isAiming(), "R then attack uses the new weapon in the same update");
            game.input(InputAction.CYCLE_WEAPON, 1); game.update(0);
            check(loadout.getWeapon() == bow, "Cycle is ignored while drawing the bow");
            game.input(InputAction.ATTACK_PRIMARY, 0); game.update(0);
            game.input(InputAction.CYCLE_WEAPON, 1); game.update(0);
            check(loadout.getWeapon() == bow, "Cycle honors the active attack guard");
            game.finish();
            game.input(InputAction.CYCLE_WEAPON, 0);
            game.input(InputAction.CYCLE_WEAPON, 2); game.update(0);
            check(loadout.getWeapon() == bow, "Release and repeat do not cycle");
            game.input(InputAction.CYCLE_WEAPON, 1); game.update(0);
            check(loadout.getWeapon() == sword, "Next press wraps back to the first configured weapon");
        }
    }

    private static void enemyBow(float releaseSpeed) {
        try (var game = new Harness(1, 2, 1)) {
            var shot = attack("bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE);
            game.selectWeapon(new CombatWeapon.Weapon("bow", "ranged/bow", shot, shot));
            var archer = game.player;
            archer.remove(Controller.class);
            var aim = new BowAim(1, 1);
            archer.add(aim).add(new ActorAi(BehaviorGraphs.ARCHER));
            var target = game.target(1, true);
            var body = Physic.MAPPER.get(target).getBody();
            body.setType(BodyDef.BodyType.DynamicBody);
            body.setTransform(4, 1, 0);
            body.getFixtureList().first().setUserData(Hurtbox.INSTANCE);
            target.add(new Transform(new Vector2(4, 1), 0, new Vector2(), new Vector2(1, 1), 0, 0));
            var sight = new FocusState(STEP);
            sight.setVisibleTarget(target);
            sight.setTargetOffset(new Vector2(3, 0));
            var graph = BehaviorGraphs.forKey(BehaviorGraphs.ARCHER).get(ActorAi.Mode.ENGAGE);
            graph.travel(archer, sight);
            game.update(STEP);
            check(aim.isAiming(), "Live sight starts enemy draw");
            game.update(STEP);
            check(!aim.isAiming() && !game.combat().isCoolingDown("bow", Combat.AttackSlot.PRIMARY),
                "Lost sight cancels draw without starting cooldown");
            for (int i = 0; i < 60 && !game.combat().isAttacking(); i++) {
                if (i == 20) { body.setLinearVelocity(0, releaseSpeed); }
                graph.travel(archer, sight);
                game.update(STEP);
            }
            check(!aim.isAiming() && game.combat().isCoolingDown("bow", Combat.AttackSlot.PRIMARY),
                "Enemy releases and starts primary cooldown");
            for (int i = 0; i < 90; i++) { game.update(STEP); }
            check(Health.MAPPER.get(target).getCurrent() < 100,
                "Real enemy arrow hits target moving at release speed " + releaseSpeed);
        }
    }

    private static void cardinalBowShots() {
        for (var facing : Facing.Direction.values()) {
            try (var game = new Harness(1, 1, 1)) {
                var shot = attack("basic_bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE);
                game.selectWeapon(new CombatWeapon.Weapon("basic_bow", "ranged/basic_bow", shot, shot));
                var aim = new BowAim(1, 1.5f);
                game.player.add(aim);
                Facing.MAPPER.get(game.player).setDirection(facing);
                game.input(InputAction.ATTACK_PRIMARY, 1); game.update(0);
                float x = 1 + (facing == Facing.Direction.RIGHT ? 1 : facing == Facing.Direction.LEFT ? -1 : 0);
                float y = 1 + (facing == Facing.Direction.UP ? 1.5f : facing == Facing.Direction.DOWN ? -1.5f : 0);
                near(x, aim.getTargetX(), "One map tile width from center: " + facing);
                near(y, aim.getTargetY(), "One map tile height from center: " + facing);
                var target = game.target(1, true);
                var body = Physic.MAPPER.get(target).getBody();
                body.getFixtureList().first().setUserData(Hurtbox.INSTANCE);
                body.setTransform(x, y, 0);
                game.input(InputAction.ATTACK_PRIMARY, 0); game.update(.5f);
                damageWithin(2.5f, target, "Quick shot reaches terminal hurtbox: " + facing);
                game.input(InputAction.ATTACK_PRIMARY, 1); game.update(0);
                check(!aim.isAiming(), "Redraw is blocked immediately after release even on a long update");
                game.input(InputAction.ATTACK_PRIMARY, 0); game.update(.125f);
                game.input(InputAction.ATTACK_PRIMARY, 1); game.update(.125f);
                check(aim.isAiming() && game.animation().getFrameIndex() == 0, "Fresh press redraws during recovery at cooldown expiry");
                game.input(InputAction.ATTACK_PRIMARY, 0); game.update(0);
                game.engine.removeEntity(game.player); game.update(1);
                damageWithin(2.5f, target, "Removing owner discards in-flight arrows");
            }
        }
    }

    private static void cooldownBetweenBowAndMelee() {
        try (var game = new Harness(1, 0, 0)) {
            var shot = attack("basic_bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE);
            var jab = attack("basic_bow", "jab", 1, 0, 0, BoundsType.TRIANGLE, CollisionType.MELEE);
            game.selectWeapon(new CombatWeapon.Weapon("basic_bow", "ranged/basic_bow", shot, jab));
            var aim = new BowAim(8, 1);
            game.player.add(aim);
            var arrowTarget = game.target(2, false);
            Physic.MAPPER.get(arrowTarget).getBody().setTransform(8, 1, 0);
            game.input(InputAction.ATTACK_PRIMARY, 1); game.update(0);
            game.input(InputAction.ATTACK_PRIMARY, 0);
            game.input(InputAction.MOVE_LEFT, 1); game.update(0);
            check(game.animation().getFdirection() == Facing.Direction.RIGHT && game.animation().getFrameIndex() == 5,
                "Held movement cannot override the committed bow release pose");
            game.input(InputAction.ATTACK_SECONDARY, 1); game.update(.125f);
            check(game.animation().getFdirection() == Facing.Direction.RIGHT
                && game.combat().getAttackFacing() == Facing.Direction.LEFT,
                "Replacement jab takes held facing without changing the last rendered release pose");
            game.update(0);
            check(game.combat().getActiveAttack() == jab && game.animation().getFrameIndex() == 0
                && game.animation().getFdirection() == Facing.Direction.LEFT
                && game.combat().isCoolingDown("basic_bow", Combat.AttackSlot.PRIMARY),
                "Jab can replace bow recovery while the shot is still cooling down");
            near(100, Health.MAPPER.get(arrowTarget).getCurrent(), "The first arrow is still in flight");
            game.attack(InputAction.ATTACK_SECONDARY);
            check(game.combat().getAttackId() == 2, "Jab cannot repeat during its own cooldown");
            var meleeTarget = game.target(1, false);
            // Inside the left-facing jab triangle, behind the first arrow.
            Physic.MAPPER.get(meleeTarget).getBody().setTransform(.55f, 1.4f, 0);
            game.body().setAwake(true);
            game.step(); game.step(); // Leave this hit queued until the bow press cancels the jab.
            game.input(InputAction.ATTACK_PRIMARY, 1); game.update(.125f);
            damageWithin(10, meleeTarget, "Draw cancellation preserves queued melee contact");
            check(aim.isAiming() && game.combat().getSensor() == null && game.animation().getFrameIndex() == 0
                && game.combat().isCoolingDown("basic_bow", Combat.AttackSlot.SECONDARY),
                "Once the shot cooldown expires, bow draw replaces the jab even during the jab cooldown");
            game.update(1f);
            damageWithin(2.5f, arrowTarget, "Replacing recovery does not discard the arrow");
        }
    }

    private static void queuedLethalHitCancelsBowRelease() {
        try (var game = new Harness(1, 0, 0)) {
            var shot = attack("basic_bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE);
            game.selectWeapon(new CombatWeapon.Weapon("basic_bow", "ranged/basic_bow", shot, shot));
            var aim = new BowAim(1, 1);
            game.player.add(aim);
            var health = Health.MAPPER.get(game.player);
            health.damage(health.getCurrent() - 1);
            fixture(game.body(), .08f, .08f, new Vector2(1, 1), true);
            game.body().getFixtureList().peek().setUserData(Hurtbox.INSTANCE);
            var target = game.target(1, false);
            Physic.MAPPER.get(target).getBody().setTransform(2, 1, 0);

            var attacker = game.target(1, false);
            var body = Physic.MAPPER.get(attacker).getBody();
            body.setType(BodyDef.BodyType.DynamicBody);
            body.setTransform(-.5f, 0f, 0f);
            var weapon = weapon(1, 0, 0, BoundsType.RECT);
            var combat = new Combat();
            attacker.add(combat).add(new Loadout(new Loadout.Equipment(null, null, null, null), weapon.id(),
                Map.of(weapon.id(), weapon), _ -> { }));
            attacker.add(new Facing(Facing.Direction.RIGHT)).add(new AnimationPlayer(attacker))
                .add(new Animation2D("OBJECTS", "attacker", Animation2D.AnimationType.IDLE, Animation.PlayMode.LOOP, 1, .125f))
                .add(new Graphic(new TextureRegion(), Color.WHITE))
                .add(new Transform(new Vector2(-.5f, 0), 0, new Vector2(2, 2), new Vector2(1, 1), 0, 0));
            game.input(InputAction.ATTACK_PRIMARY, 1);
            combat.requestAttack(Combat.AttackSlot.PRIMARY);
            game.update(0);
            game.update(0);
            check(aim.isAiming() && combat.getSensor() != null, "Bow draw and hostile melee sensor coexist before contact");
            game.step();
            game.step();
            check(health.isAlive() && touching(combat.getSensor(), game.body().getFixtureList().peek()),
                "Lethal contact waits for unlocked combat processing");
            game.input(InputAction.ATTACK_PRIMARY, 0);
            game.update(.5f);
            check(!health.isAlive() && !aim.isAiming() && game.combat().getAttackId() == 0,
                "A prior lethal contact cancels the queued bow release before it starts an attack");
            near(100, Health.MAPPER.get(target).getCurrent(), "Killed archer never launches the pending shot");
        }
    }

    private static void bowDamageByDrawFrame() {
        int[] damage = {8, 15, 23, 30, 30};
        for (int frame = 0; frame < damage.length; frame++) {
            try (var game = new Harness(1, 1, 1)) {
                var shot = attackWithDamage("basic_bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE,
                    new AttackDamage.Range(20, 30));
                game.selectWeapon(new CombatWeapon.Weapon("basic_bow", "ranged/basic_bow", shot, shot));
                game.player.add(new BowAim(1, 1));
                var target = game.target(2, false);
                Physic.MAPPER.get(target).getBody().setTransform(2, 1, 0);
                game.input(InputAction.ATTACK_PRIMARY, 1); game.update(0);
                game.update(frame * shot.animation().frameInterval());
                check(game.animation().getFrameIndex() == frame, "Expected draw frame " + frame);
                var previous = MathUtils.random;
                var rolls = new HitRolls();
                try {
                    MathUtils.random = rolls;
                    game.input(InputAction.ATTACK_PRIMARY, 0); game.update(0);
                    check(game.animation().getFrameIndex() == 5, "All draw strengths start release at frame 5");
                    check(rolls.count == 0, "Bow release does not roll damage before landing");
                    game.update(.5f);
                    near(100 - damage[frame], Health.MAPPER.get(target).getCurrent(),
                        "One roll applies to draw-scaled damage at frame " + frame);
                    check(rolls.count == 1, "Landed arrow rolls once across duplicate fixtures");
                } finally { MathUtils.random = previous; }
            }
        }
    }

    private static void heldBow() {
        try (var game = new Harness(1, 1, 1)) {
            var bow = new CombatWeapon.Weapon("basic_bow", "ranged/basic_bow",
                attack("basic_bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE),
                attack("basic_bow", "jab", 1, 2, 1, BoundsType.TRIANGLE, CollisionType.MELEE));
            game.selectWeapon(bow);
            var aim = new BowAim(1, 1);
            game.player.add(aim);
            var map = InputMap.of();
            int space = com.badlogic.gdx.Input.Keys.SPACE;
            map.setKey(space, InputAction.ATTACK_PRIMARY);
            map.setButton(0, InputAction.ATTACK_PRIMARY);
            var input = new InputManager(map);
            input.regKeyListener(Controller.MAPPER.get(game.player).getInputListener());
            var target = game.target(2, false);
            Physic.MAPPER.get(target).getBody().setTransform(2, 1, 0);
            input.keyDown(space);
            input.touchDown(0, 0, 0, 0);
            game.update(.1f);
            check(game.animation().getAction().equals("shot") && game.animation().getFrameIndex() == 0,
                "First binding starts drawing immediately");
            near(2, aim.getTargetX(), "New aim starts one tile forward");
            near(1, aim.getTargetY(), "Aim starts on facing axis");
            game.input(InputAction.MOVE_UP, 1); game.update(.1f);
            near(1.6f, aim.getTargetY(), "Direction steers held crosshair");
            game.input(InputAction.MOVE_UP, 0); input.keyUp(space); game.update(1);
            check(aim.isAiming() && !game.combat().isAttacking(), "Releasing only one binding cannot fire");
            check(game.animation().getFrameIndex() == 4, "Long update stops at full draw");
            game.update(0);
            check(game.animation().getFrameIndex() == 4, "Zero delta holds full draw");
            input.touchCancelled(0, 0, 0, 0); game.update(1);
            check(game.animation().getType() == Animation2D.AnimationType.IDLE, "Cancellation resets drawn pose");
            near(100, Health.MAPPER.get(target).getCurrent(), "Cancellation deals no damage");
            check(!aim.isAiming() && !game.combat().isAttacking(), "Cancelled touch hides without firing");
            input.keyDown(space); game.update(0);
            near(1, aim.getTargetY(), "Re-aim resets prior steering");
            input.releaseHeldInputs(); game.update(1); input.keyUp(space);
            check(!aim.isAiming() && !game.combat().isAttacking(), "Focus release cancels without firing");
            input.touchDown(0, 0, 0, 0); game.update(0);
            input.touchUp(0, 0, 0, 0); game.update(0);
            check(!aim.isAiming() && game.combat().isAttacking(), "Mouse release hides reticle and starts shot");
            check(game.animation().getFrameIndex() == 5, "Quick release skips unplayed draw frames");
            near(100, Health.MAPPER.get(target).getCurrent(), "Shot is not hitscan");
            game.update(.5f);
            damageWithin(2.5f, target, "One quick shot hits even on long frame");

            input.keyDown(space); game.update(0);
            game.input(InputAction.MOVE_UP, 1); game.update(1f / 6f);
            Physic.MAPPER.get(target).getBody().setTransform(aim.getTargetX(), aim.getTargetY(), 0);
            input.keyUp(space); game.update(0);
            check(game.animation().getFdirection() == Facing.Direction.UP, "Exact diagonal tie faces vertically");
            game.input(InputAction.MOVE_UP, 0); game.update(.5f);
            damageWithin(7.5f, target, "Vertical pose still fires along the diagonal");

            input.keyDown(space); game.update(0);
            game.input(InputAction.MOVE_DOWN, 1); game.update(1f / 6f);
            input.keyUp(space); game.update(0);
            check(game.animation().getFdirection() == Facing.Direction.UP, "Centered release retains facing despite held movement");
        }
    }

    private static void independentAttacksAndGeometry(Actor.Role attacker, Actor.Role victim) {
        var primary = new CombatWeapon.Attack("test", new AttackDamage.Range(15, 20), 0, .375f,
            new Animation2D.Attack("slash", 1, .125f, 1, "primary"), "hit", BoundsType.RECT,
            new CombatWeapon.Bounds(32, 16, 72, 48, ZERO, ZERO, ZERO, ZERO), CollisionType.MELEE);
        var secondary = new CombatWeapon.Attack("test", new AttackDamage.Range(10, 10), 2, .375f,
            new Animation2D.Attack("jab", 1, .125f, 3, "secondary"), "hit", BoundsType.TRIANGLE,
            new CombatWeapon.Bounds(32, 24, 56, 40, ZERO, ZERO, ZERO, ZERO), CollisionType.MELEE);
        for (var input : new InputAction[]{InputAction.ATTACK_PRIMARY, InputAction.ATTACK_SECONDARY}) {
            try (var game = new Harness(1, 0, 0)) {
                game.player.add(new Actor(attacker, 0, 0));
                game.selectWeapon(new CombatWeapon.Weapon("test", "melee/test", primary, secondary));
                var near = game.target(2, false).add(new Actor(victim, 0, 0));
                var far = game.target(1, false).add(new Actor(victim, 0, 0));
                Physic.MAPPER.get(near).getBody().setTransform(1.5f, 1, 0);
                Physic.MAPPER.get(far).getBody().setTransform(2.1f, 1, 0);
                boolean first = input == InputAction.ATTACK_PRIMARY;
                var expected = first ? primary : secondary;
                game.attack(input);
                check(game.animation().getAction().equals(expected.animation().action()), "Selected action reaches animation");
                check((game.combat().getSensor() != null) == first, "Independent hit-frame start");
                game.events.update(0);
                check(game.sounds.isEmpty(), "Neither nonzero cue plays at frame zero");
                game.update(.125f); game.events.update(0);
                check(game.sounds.equals(first ? List.of("primary") : List.of()), "Distinct first-frame sound cue");
                game.update(.125f);
                var polygon = (PolygonShape) game.combat().getSensor().getShape();
                check(polygon.getVertexCount() == (first ? 4 : 3), "Selected attack owns its actual fixture shape");
                var point = new Vector2();
                float tip = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < polygon.getVertexCount(); i++) {
                    polygon.getVertex(i, point); tip = Math.max(tip, point.x);
                }
                near(first ? 2.25f : 1.75f, tip, "Independent configured reach");
                game.step(); game.combatSystem.update(0);
                int nearHit = 100 - Health.MAPPER.get(near).getCurrent();
                check(first ? nearHit >= 15 && nearHit <= 20 : nearHit == 10, "Independent selected damage, once per target");
                int farHit = 100 - Health.MAPPER.get(far).getCurrent();
                check(first ? farHit >= 15 && farHit <= 20 : farHit == 0, "Only longer primary reaches distant target");
                game.update(.125f); game.events.update(0);
                check(game.sounds.equals(List.of(first ? "primary" : "secondary")), "Independent secondary cue reached once");
                game.update(0); game.events.update(0);
                check(game.sounds.size() == 1, "Independent cue is not repeated");
                game.finish();
            }
        }
    }

    private static void committedMeleeHit() {
        try (var game = new Harness(1, 2, 1)) {
            var target = game.target(3, false);
            Physic.MAPPER.get(target).getBody().setTransform(2.125f, 1, 0);
            var previous = MathUtils.random;
            var rolls = new HitRolls();
            try {
                MathUtils.random = rolls;
                game.attack();
                check(rolls.count == 0 && game.combat().getAttackFacing() == Facing.Direction.RIGHT,
                    "Committing an attack captures facing without consuming the hit roll");
                Facing.MAPPER.get(game.player).setDirection(Facing.Direction.LEFT);
                game.update(0); // Reloading the visual facing does not advance playback.
                game.update(.25f);
                check(game.combat().getSensor() != null, "Committed hit frame installs the sensor");
                game.step(); game.combatSystem.update(0);
                near(89, Health.MAPPER.get(target).getCurrent(), "Hitbox still points along the committed attack");
                game.step(); game.combatSystem.update(0);
                check(rolls.count == 1, "Duplicate fixtures and contact ticks do not consume another roll");
            } finally { MathUtils.random = previous; }
        }
    }

    private static void playerKillCredit() {
        for (boolean projectile : new boolean[]{false, true}) {
            for (var source : new Actor.Role[]{Actor.Role.PLAYER, Actor.Role.ENEMY}) {
                for (var victim : new Actor.Role[]{Actor.Role.ENEMY, Actor.Role.NPC}) {
                    try (var game = new Harness(1, 0, 0)) {
                        var kills = new ArrayList<Event.Player.KilledEnemy>();
                        game.events.subscribe(Event.Player.KilledEnemy.class, kills::add);
                        game.player.add(new Actor(source, 0, 0));
                        var target = game.target(2, false).add(new Actor(victim, 0, 0));
                        if (projectile) {
                            var shot = attack("bow", "shot", 1, 2, 1, BoundsType.RECT, CollisionType.PROJECTILE);
                            game.selectWeapon(new CombatWeapon.Weapon("bow", "ranged/bow", shot, shot));
                            game.player.add(new BowAim(1, 1));
                            Physic.MAPPER.get(target).getBody().setTransform(2, 1, 0);
                        }
                        for (boolean lethal : new boolean[]{false, true}) {
                            if (lethal) { Health.MAPPER.get(target).damage(Health.MAPPER.get(target).getCurrent() - 1); }
                            if (projectile) {
                                game.input(InputAction.ATTACK_PRIMARY, 1); game.update(0);
                                game.input(InputAction.ATTACK_PRIMARY, 0); game.update(.5f);
                            } else {
                                game.attack(); game.step(); game.combatSystem.update(0);
                            }
                            game.finish(); game.events.update(0);
                            var health = Health.MAPPER.get(target);
                            check(health.getCurrent() < health.getMaximum() && health.isAlive() != lethal,
                                "Real hit resolves before checking kill credit");
                            boolean credited = lethal && source == Actor.Role.PLAYER && victim == Actor.Role.ENEMY;
                            check(kills.equals(credited ? List.of(new Event.Player.KilledEnemy(game.player, target)) : List.of()),
                                "Only the player's lethal hit on an enemy gets credit: projectile=" + projectile);
                        }
                        kills.clear();
                        game.engine.removeEntity(target); // corpse retirement
                        game.engine.removeEntity(game.target(1, false).add(new Actor(Actor.Role.ENEMY, 0, 0)));
                        game.events.update(0);
                        check(kills.isEmpty(), "Removing a corpse or a living enemy grants no kill credit");
                    }
                }
            }
        }
    }

    private static final class HitRolls extends java.util.Random {
        int count;
        @Override public float nextFloat() { count++; return .75f; }
        @Override public int nextInt(int bound) { count++; return bound - 1; }
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
                damageWithin(10, target, "Sleeping owner hits beyond sprite in " + facing);
                game.step(); game.combatSystem.update(0);
                damageWithin(10, target, "Multiple fixtures damage once per attack");
                game.finish(); game.attack(); game.step(); game.combatSystem.update(0);
                damageWithin(20, target, "New attack can hit same target");
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

    private static void characterFixturesAndContacts() {
        var feet = new CharacterBounds(20, 4, 44, 14);
        var standing = new CharacterBounds(16, 6, 48, 56);
        var world = new World(Vector2.Zero, true);
        try {
            var definition = new BodyDef();
            definition.type = BodyDef.BodyType.DynamicBody;
            definition.fixedRotation = true;
            var body = world.createBody(definition);
            var physical = CharacterFixtures.ellipse(body, feet, false);
            var hurtbox = CharacterFixtures.ellipse(body, standing, true);
            hurtbox.setUserData(Hurtbox.INSTANCE);
            ellipse(physical, feet, false);
            ellipse(hurtbox, standing, true);
            var wall = world.createBody(new BodyDef());
            fixture(wall, 2, .05f, new Vector2(1, .75f), false);
            body.setLinearVelocity(0, 1);
            for (int i = 0; i < 10; i++) { world.step(STEP, 6, 2); }
            near(10 * STEP, body.getPosition().y, "Upper hurtbox passes through solid scenery without blocking movement");
            check(touching(hurtbox, wall.getFixtureList().first()), "Upper hurtbox actually overlaps the solid wall");
            check(!touching(physical, wall.getFixtureList().first()), "Feet have not reached wall during hurtbox-only overlap");
            for (int i = 0; i < 80; i++) { world.step(STEP, 6, 2); }
            check(body.getPosition().y > .2f && body.getPosition().y < .3f, "Only feet stop the moving character at the wall");
            check(touching(physical, wall.getFixtureList().first()), "Physical ellipse makes a real blocking contact");
            near(0, body.getLinearVelocity().y, "Solid feet collider stops velocity");
            world.destroyBody(body);
            check(world.getBodyCount() == 1, "Body removal destroys both persistent fixtures safely");
            rejects(() -> CharacterFixtures.ellipse(wall, new CharacterBounds(0, 0, .001f, .001f), false),
                "Collapsed native ellipse");
            rejects(() -> CharacterFixtures.ellipse(wall, new CharacterBounds(0, 0, 64, .001f), true),
                "Extremely thin native ellipse");
        } finally { world.dispose(); }
        for (boolean feetOnly : new boolean[]{false, true}) {
            try (var game = new Harness(1, 0, 0)) {
                var target = game.target(0, false);
                var targetBody = Physic.MAPPER.get(target).getBody();
                targetBody.setTransform(.7f, 0, 0);
                var physical = CharacterFixtures.ellipse(targetBody, feet, false);
                var hurtbox = CharacterFixtures.ellipse(targetBody, standing, true);
                hurtbox.setUserData(Hurtbox.INSTANCE);
                var bounds = feetOnly ? new CombatWeapon.Bounds(32, 4, 72, 4.5f, ZERO, ZERO, ZERO, ZERO)
                    : new CombatWeapon.Bounds(32, 24, 72, 56, ZERO, ZERO, ZERO, ZERO);
                var attack = new CombatWeapon.Attack("test", new AttackDamage.Scalar(10), 0, .375f,
                    new Animation2D.Attack("slash", 1, .125f, 0, "sound"), "hit", BoundsType.RECT, bounds, CollisionType.MELEE);
                game.selectWeapon(new CombatWeapon.Weapon("test", "melee/test", attack, attack));
                if (!feetOnly) {
                    CharacterFixtures.ellipse(targetBody, standing, true).setUserData(Hurtbox.INSTANCE);
                    fixture(targetBody, .08f, .08f, new Vector2(1, 1), false);
                }
                game.attack(); game.step();
                var sensor = game.combat().getSensor();
                check(touching(sensor, feetOnly ? physical : hurtbox), "Native attack reaches selected target fixture");
                check(!touching(sensor, feetOnly ? hurtbox : physical), "Feet and upper hurtbox are independently placed");
                near(100, Health.MAPPER.get(target).getCurrent(), "Hurtbox contact defers damage until unlocked update");
                game.combatSystem.update(0);
                damageWithin(feetOnly ? 0 : 10, target,
                    feetOnly ? "Physical-only overlap does not damage explicit hurtbox targets" : "Upper-body hurtbox is damaged outside feet, once across duplicate fixtures");
                game.step(); game.combatSystem.update(0);
                damageWithin(feetOnly ? 0 : 10, target, "Persistent contacts cannot repeat damage");
                game.finish();
                check(targetBody.getFixtureList().contains(hurtbox, true) && hurtbox.getUserData() == Hurtbox.INSTANCE,
                    "Attack cleanup leaves persistent tagged target hurtbox intact");
                check(game.body().getFixtureList().size == 1, "Temporary attack fixture is removed independently");
                if (!feetOnly) {
                    game.attack(); game.step();
                    game.engine.removeEntity(target);
                    game.combatSystem.update(0);
                    damageWithin(10, target, "Removed hurtbox target discards queued damage safely");
                }
            }
        }
        try (var game = new Harness(1, 0, 0)) {
            var ownerHurtbox = CharacterFixtures.ellipse(game.body(), standing, true);
            ownerHurtbox.setUserData(Hurtbox.INSTANCE);
            var dead = game.target(0, false);
            var scenery = game.target(0, false);
            for (var entity : List.of(dead, scenery)) {
                var body = Physic.MAPPER.get(entity).getBody();
                body.setTransform(.7f, 0, 0);
                CharacterFixtures.ellipse(body, standing, true).setUserData(Hurtbox.INSTANCE);
            }
            Health.MAPPER.get(dead).damage(100);
            scenery.remove(Health.class);
            var unrelatedSensor = game.target(1, true);
            game.attack(); game.step(); game.combatSystem.update(0);
            near(0, Health.MAPPER.get(dead).getCurrent(), "Dead tagged hurtbox remains immune");
            near(100, Health.MAPPER.get(unrelatedSensor).getCurrent(), "Untagged sensors remain excluded beside explicit hurtboxes");
            near(100, Health.MAPPER.get(game.player).getCurrent(), "Owner hurtbox never receives its own attack");
            game.player.remove(Combat.class);
            check(game.body().getFixtureList().size == 2 && game.body().getFixtureList().contains(ownerHurtbox, true),
                "Removing combat cleans temporary attack, not the owner's persistent hurtbox");
        }
    }

    private static boolean touching(Fixture first, Fixture second) {
        for (var contact : first.getBody().getWorld().getContactList()) {
            if (contact.isTouching() && ((contact.getFixtureA() == first && contact.getFixtureB() == second)
                || (contact.getFixtureA() == second && contact.getFixtureB() == first))) { return true; }
        }
        return false;
    }

    private static void ellipse(Fixture fixture, CharacterBounds bounds, boolean sensor) {
        check(fixture.isSensor() == sensor, "Character fixture sensor flag");
        near(0, fixture.getFriction(), "Character fixture has no movement friction");
        var shape = (PolygonShape) fixture.getShape();
        check(shape.getVertexCount() == 8, "Ellipse uses eight native vertices");
        var point = new Vector2();
        for (int i = 0; i < 8; i++) {
            float angle = i * com.badlogic.gdx.math.MathUtils.PI2 / 8;
            float x = ((bounds.x1() + bounds.x2()) * .5f
                + (bounds.x2() - bounds.x1()) * .5f * com.badlogic.gdx.math.MathUtils.cos(angle)) * GameConsts.UNIT_SCALE;
            float y = ((bounds.y1() + bounds.y2()) * .5f
                + (bounds.y2() - bounds.y1()) * .5f * com.badlogic.gdx.math.MathUtils.sin(angle)) * GameConsts.UNIT_SCALE;
            boolean found = false;
            for (int j = 0; j < 8; j++) {
                shape.getVertex(j, point);
                found |= point.dst2(x, y) < .0000001f;
            }
            check(found, "Authored local ellipse vertex is scaled exactly once: " + i);
        }
    }

    private static void cleanup() {
        for (int hitFrame : new int[]{0, 2}) {
            for (String removal : new String[]{"death", "entity", "combat", "loadout", "animation", "system", "target"}) {
                try (var game = new Harness(1, hitFrame, 0)) {
                    var target = game.target(1, false);
                    var combat = game.combat();
                    game.attack(); game.step();
                    switch (removal) {
                        case "death" -> Health.MAPPER.get(game.player).damage(100);
                        case "entity" -> game.engine.removeEntity(game.player);
                        case "combat" -> game.player.remove(Combat.class);
                        case "loadout" -> game.player.remove(Loadout.class);
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
            var weapon = weapon(1, 0, 0, shape).primary();
            for (var facing : Facing.Direction.values()) {
                var vertices = weapon.vertices(anchor, facing);
                check(vertices.length >= 3 && vertices.length <= 8, "Native vertex limit");
                var polygon = new PolygonShape();
                try { polygon.set(vertices); check(polygon.getVertexCount() == vertices.length, "Valid native polygon"); }
                finally { polygon.dispose(); }
            }
            var shifted = new CombatWeapon.Attack(weapon.weaponId(), weapon.damage(), weapon.hitFrame(), weapon.cooldownSeconds(),
                weapon.animation(), weapon.hitSoundId(), shape,
                new CombatWeapon.Bounds(32, 0, 72, 64, new Offset(-3, 7), new Offset(5, -9),
                    new Offset(-11, -13), new Offset(17, 19)), weapon.collisionType());
            shifted.validateGeometry(anchor);
            for (var facing : Facing.Direction.values()) {
                var offset = switch (facing) {
                    case UP -> new Offset(-3, 7);
                    case DOWN -> new Offset(5, -9);
                    case LEFT -> new Offset(-11, -13);
                    case RIGHT -> new Offset(17, 19);
                };
                var original = weapon.vertices(anchor, facing);
                var translated = shifted.vertices(anchor, facing);
                for (int i = 0; i < original.length; i++) {
                    near(original[i].x + offset.x() * GameConsts.UNIT_SCALE, translated[i].x,
                        "Post-rotation X offset: " + facing);
                    near(original[i].y + offset.y() * GameConsts.UNIT_SCALE, translated[i].y,
                        "Post-rotation Y offset: " + facing);
                }
            }
            for (var bounds : new CombatWeapon.Bounds[]{new CombatWeapon.Bounds(0, 0, .001f, .001f, ZERO, ZERO, ZERO, ZERO),
                new CombatWeapon.Bounds(0, 0, 1e24f, 64, ZERO, ZERO, ZERO, ZERO),
                new CombatWeapon.Bounds(0, 0, 1e15f, 1e15f, ZERO, ZERO, ZERO, ZERO)}) {
                var unsafe = new CombatWeapon.Attack("bad", new AttackDamage.Scalar(10), 0, .375f, new Animation2D.Attack("slash", 1, .125f, 0, "sound"),
                    "hit", shape, bounds, CollisionType.MELEE);
                rejects(() -> unsafe.validateGeometry(anchor), "Unsafe geometry");
            }
        }
        var health = new Health(100);
        health.damage(25); near(75, health.getCurrent(), "Damage subtracts");
        health.damage(Integer.MAX_VALUE); near(0, health.getCurrent(), "Damage clamps");
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
        var shared = sharedAtlases();
        TextureAtlas[] loaded = {shared.get(CharacterAssets.CHARACTER.id())};
        var assets = new AssetService(FileHandle::new) {
            @Override public <T> T get(Class<T> type, String id) {
                check(type == TextureAtlas.class, "Factory resolves typed atlases");
                var result = id.equals("OBJECTS") ? atlas
                    : id.equals(CharacterAssets.CHARACTER.id()) ? loaded[0] : shared.get(id);
                check(result != null, "Factory uses declared logical atlas key: " + id);
                return type.cast(result);
            }
        };
        var input = new InputManager(InputMap.of());
        var engine = new Engine();
        try {
            var data = DataLoader.loadGameData();
            field.set(GameCore.INSTANCE, data);
            bindEngine(engine);
            engine.addSystem(new EventSystem());
            engine.addSystem(new AttackCooldownSystem());
            engine.addSystem(new AnimationPlayerSystem());
            engine.addSystem(new AnimationSystem(assets));
            var combat = new CombatSystem(world, data.projectilesData().ironArrow()); engine.addSystem(combat);
            var factory = new EntityFactory(world, assets); engine.addSystem(factory);
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
            for (var id : data.weaponsData().weapons().keySet()) {
                for (boolean primary : new boolean[]{true, false}) {
                    var weapon = data.weaponsData().requireWeapon(id);
                    var original = primary ? weapon.attackStats().primary() : weapon.attackStats().secondary();
                    for (boolean hit : new boolean[]{true, false}) {
                        for (int invalid : new int[]{8, Integer.MAX_VALUE}) {
                            var bad = withCues(original, hit ? invalid : 0, hit ? 0 : invalid);
                            field.set(GameCore.INSTANCE, replaceAttack(data, id, primary, bad));
                            rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT),
                                "Out of range cue in complete catalog: " + id + "/" + primary + "/" + hit);
                        }
                    }
                    var badBounds = new WeaponData.Bounds(BoundsType.RECT, 0, 0, .001f, .001f, ZERO, ZERO, ZERO, ZERO);
                    var badGeometry = new AttackData(original.animation(), badBounds, original.stats(), original.sounds(), original.collisionType());
                    field.set(GameCore.INSTANCE, replaceAttack(data, id, primary, badGeometry));
                    rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT), "Unsafe catalog Box2D geometry");
                }
            }
            field.set(GameCore.INSTANCE, data);
            for (var asset : List.of(CharacterAssets.CHARACTER, CharacterAssets.HEAD, CharacterAssets.BODY,
                CharacterAssets.HANDS, CharacterAssets.FEET, CharacterAssets.WEAPONS)) {
                var candidate = shared.get(asset.id());
                String prefix = asset == CharacterAssets.CHARACTER ? "player/"
                    : asset.id().equals(CharacterAssets.WEAPONS.id()) ? "melee/basic_sword/" : "basic/";
                var frame = sourceFrame(candidate, prefix + "idle_down");
                int width = frame.getRegionWidth();
                try {
                    frame.setRegionWidth(32);
                    rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT),
                        "Invalid loaded frame geometry: " + asset.id());
                } finally { frame.setRegionWidth(width); }
            }
            for (var asset : List.of(CharacterAssets.HEAD, CharacterAssets.BODY, CharacterAssets.HANDS,
                CharacterAssets.FEET, CharacterAssets.WEAPONS)) {
                var candidate = shared.get(asset.id());
                String key = asset == CharacterAssets.WEAPONS ? "ranged/basic_bow/jab_up" : "basic/shot_up";
                var frame = sourceFrame(candidate, key);
                candidate.getRegions().removeValue(frame, true);
                try {
                    rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT),
                        "Missing directional layer frame: " + asset.id());
                } finally { candidate.getRegions().add(frame); }
            }
            var cues = data;
            for (var id : data.weaponsData().weapons().keySet()) {
                var attacks = data.weaponsData().requireWeapon(id).attackStats();
                cues = replaceAttack(cues, id, true, withCues(attacks.primary(), 6, 7));
                cues = replaceAttack(cues, id, false, withCues(attacks.secondary(), 6, 7));
            }
            field.set(GameCore.INSTANCE, cues);
            for (var logical : new PlayerData.LogicalSize[]{new PlayerData.LogicalSize(129, 64), new PlayerData.LogicalSize(64, 129)}) {
                var original = data.playerData();
                var invalid = new PlayerData(original.name(), original.moveSpeed(), original.maxHealth(), original.loadout(),
                    original.animation(), original.sounds(), logical, original.collision(), original.hitboxBounds());
                field.set(GameCore.INSTANCE, new GameData(invalid, data.weaponsData(), data.projectilesData(), data.actorsData(), data.objectsData()));
                rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT), "Logical dimensions must fit the loaded canvas");
            }
            for (boolean collision : new boolean[]{false, true}) {
                var original = data.playerData();
                var tiny = new CharacterBounds(0, 0, .001f, .001f);
                var invalid = new PlayerData(original.name(), original.moveSpeed(), original.maxHealth(), original.loadout(),
                    original.animation(), original.sounds(), original.logicalSize(),
                    collision ? CollisionData.ellipse(tiny) : original.collision(), collision ? original.hitboxBounds() : tiny);
                field.set(GameCore.INSTANCE, new GameData(invalid, data.weaponsData(), data.projectilesData(), data.actorsData(), data.objectsData()));
                int bodyCount = world.getBodyCount();
                rejects(() -> factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT), "Native-unsafe configured ellipse is rejected in Java");
                check(world.getBodyCount() == bodyCount, "Invalid character fixture construction cannot leak a partial body");
            }
            var original = data.playerData();
            var different = new PlayerData(original.name(), original.moveSpeed(), original.maxHealth(), original.loadout(),
                original.animation(), original.sounds(), new PlayerData.LogicalSize(80, 72), original.collision(), original.hitboxBounds());
            field.set(GameCore.INSTANCE, new GameData(different, data.weaponsData(), data.projectilesData(), data.actorsData(), data.objectsData()));
            var differentPlayer = factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT);
            near(80 * GameConsts.UNIT_SCALE, Transform.MAPPER.get(differentPlayer).getSize().x, "Factory accepts configured future logical width");
            near(72 * GameConsts.UNIT_SCALE, Transform.MAPPER.get(differentPlayer).getSize().y, "Factory accepts configured future logical height");
            world.destroyBody(Physic.MAPPER.get(differentPlayer).getBody());
            field.set(GameCore.INSTANCE, cues);
            var player = factory.getPlayerEntity(new Vector2(320, 640), null, Facing.Direction.RIGHT);
            check(player.getComponent(Player.class) != null, "Real player factory declares map-sensor participation");
            var transform = Transform.MAPPER.get(player);
            near(64 * GameConsts.UNIT_SCALE, transform.getSize().x, "Factory uses logical width rather than source canvas");
            near(64 * GameConsts.UNIT_SCALE, transform.getSize().y, "Factory uses logical height rather than source canvas");
            var canvas = CompositeCharacter.MAPPER.get(player);
            check(canvas.canvasWidth() == 128 && canvas.canvasHeight() == 128, "Factory keeps native source canvas separate");
            var fixtures = Physic.MAPPER.get(player).getBody().getFixtureList();
            check(fixtures.size == 2, "Factory creates separate persistent feet and hurtbox fixtures");
            for (var fixture : fixtures) {
                boolean hurtbox = fixture.getUserData() == Hurtbox.INSTANCE;
                ellipse(fixture, hurtbox ? data.playerData().hitboxBounds() : data.playerData().collision().bounds(), hurtbox);
            }
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
            near(data.playerData().maxHealth(), Health.MAPPER.get(player).getCurrent(), "Factory player stats");
            var loadout = Loadout.MAPPER.get(player);
            var defaultWeapon = data.playerData().loadout().weapon();
            check(loadout != null && java.util.Objects.equals(loadout.getWeaponId(), defaultWeapon),
                "Factory installs authored default runtime loadout");
            check(loadout.getWeapon().primary().animation().soundId().equals("weapon/" + loadout.getWeapon().id() + "/primary/attack"),
                "Factory passes resolved sound ID");
            var originalEquipment = loadout.getEquipment();
            var loadouts = new LoadoutSystem();
            engine.addSystem(loadouts);
            rejects(() -> loadouts.equip(player, new Loadout.Equipment("missing", "basic", "basic", "basic")),
                "Equipment switching validates loaded atlas regions");
            rejects(() -> loadouts.equip(player, new Loadout.Equipment(" ", null, null, null)),
                "Equipment switching rejects blank IDs");
            check(loadout.getEquipment().equals(originalEquipment), "Invalid equipment switch is atomic");
            var empty = new Loadout.Equipment(null, null, null, null);
            check(loadouts.equip(player, empty) && loadout.getEquipment().equals(empty), "All armor slots can be empty");
            check(loadouts.equip(player, originalEquipment), "Valid loaded equipment is accepted");
            for (String id : new String[]{"basic_sword", "basic_bow", null}) {
                check(loadouts.equipWeapon(player, id), "Factory loadout selects weapon");
                for (var slot : Combat.AttackSlot.values()) {
                    for (var direction : Facing.Direction.values()) {
                        Facing.MAPPER.get(player).setDirection(direction);
                        state.requestAttack(slot); engine.update(0); engine.update(0);
                        var active = state.getActiveAttack();
                        String soundPrefix = "weapon/" + (id == null ? "unarmed" : id) + "/"
                            + (slot == Combat.AttackSlot.PRIMARY ? "primary" : "secondary");
                        check(active.animation().soundId().equals(soundPrefix + "/attack")
                            && active.hitSoundId().equals(soundPrefix + "/hit"),
                            "Accepted snapshot retains selected attack sound identities");
                        check(animation.getAnimation().getKeyFrames().length == 8, "Flat action atlas frames " + direction);
                        check(active.animation().action().equals(id == null || slot == Combat.AttackSlot.SECONDARY
                            ? "jab" : id.equals("basic_bow") ? "shot" : "slash"), "Factory selects action");
                        float speed = active.animation().speed();
                        check(state.getSensor() == null, "Factory attack waits for hit cue");
                        engine.update(5.5f * animation.getFrameDuration() / speed);
                        check(state.getSensor() == null, "No sensor before hit cue");
                        engine.update(.51f * animation.getFrameDuration() / speed);
                        check((state.getSensor() != null) == (active.collisionType() == CollisionType.MELEE),
                            "Factory melee activates while projectile remains sensor-free");
                        engine.update(2 * animation.getFrameDuration() / speed); engine.update(0);
                        check(!state.isAttacking(), "Factory action completes");
                        check(animation.getAnimation().getKeyFrames().length == 8, "Idle frames restored");
                    }
                }
            }
            var shortAtlas = new TextureAtlas();
            var replacement = new TextureAtlas();
            try {
                for (var frame : shared.get(CharacterAssets.CHARACTER.id()).getRegions()) {
                    replacement.addRegion(frame.name, frame).index = frame.index;
                }
                var previousAnimation = animation.getAnimation();
                for (var action : new String[]{"slash", "jab", "shot"}) {
                    for (var direction : Facing.Direction.values()) {
                        shortAtlas.getRegions().clear();
                        var key = "player/" + action + "_" + direction.name().toLowerCase(java.util.Locale.ROOT);
                        for (var frame : replacement.getRegions()) {
                            if (!frame.name.equals(key) || frame.index < 7) {
                                shortAtlas.addRegion(frame.name, frame).index = frame.index;
                            }
                        }
                        loaded[0] = shortAtlas;
                        rejects(() -> engine.update(0), "Reload validates all catalog actions and facings: " + key);
                        check(animation.getAnimation() == previousAnimation, "Invalid reload does not install animation");
                        rejects(() -> engine.update(0), "Invalid reload remains unaccepted on retry");
                    }
                }
                loaded[0] = replacement;
                engine.update(0);
                check(animation.getAnimation() != previousAnimation, "Valid replacement reloads existing entity");
                Facing.MAPPER.get(player).setDirection(Facing.Direction.UP);
                state.requestAttack(Combat.AttackSlot.SECONDARY); engine.update(0); engine.update(0);
                check(animation.getAnimation().getKeyFrames().length == 8, "Valid replacement supports action playback");
            } finally { shortAtlas.dispose(); replacement.dispose(); }
        } finally {
            engine.removeAllEntities(); world.dispose(); assets.dispose(); atlas.dispose(); input.dispose();
            shared.values().forEach(TextureAtlas::dispose);
            field.set(GameCore.INSTANCE, previous); bindEngine(previousEngine); Gdx.files = files;
        }
    }

    private static AttackData withCues(AttackData attack, int hitFrame, int soundFrame) {
        var animation = attack.animation();
        var stats = attack.stats();
        return new AttackData(new AnimationData(animation.speed(), animation.atlasKey(), soundFrame, animation.frameInterval()),
            attack.bounds(), new Stats(stats.damage(), stats.cost(), hitFrame, stats.cooldownSeconds()), attack.sounds(), attack.collisionType());
    }

    private static GameData replaceAttack(GameData data, String id, boolean primary, AttackData replacement) {
        var weapons = new java.util.HashMap<>(data.weaponsData().weapons());
        var original = weapons.get(id);
        var attacks = original.attackStats();
        var changed = new AttackStats(primary ? replacement : attacks.primary(), primary ? attacks.secondary() : replacement);
        weapons.put(id, new WeaponData(changed, original.atlasKey()));
        return new GameData(data.playerData(), new WeaponsData(data.weaponsData().atlas(), weapons),
            data.projectilesData(), data.actorsData(), data.objectsData());
    }

    private static TextureAtlas.AtlasRegion sourceFrame(TextureAtlas atlas, String key) {
        for (var region : atlas.getRegions()) {
            if (region.name.equals(key)) { return region; }
        }
        throw new AssertionError("Missing atlas source frame: " + key);
    }

    private static Map<String, TextureAtlas> sharedAtlases() {
        var atlases = new java.util.HashMap<String, TextureAtlas>();
        for (var asset : CharacterAssets.all("weapons.atlas")) {
            var atlas = new TextureAtlas();
            var file = new FileHandle("assets/" + asset.path());
            var data = new TextureAtlas.TextureAtlasData(file, file.parent(), false);
            for (var region : data.getRegions()) {
                // Deliberately omit cast: the runtime must work without magic assets.
                if (region.name.startsWith("cast_") || region.name.contains("/cast_")) { continue; }
                atlas.addRegion(region.name, new DimensionTexture((int) region.page.width, (int) region.page.height),
                    region.left, region.top, region.width, region.height).index = region.index;
            }
            atlases.put(asset.id(), atlas);
        }
        return atlases;
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
        var primary = attack("test", "slash", speed, hit, sound, shape, CollisionType.MELEE);
        var secondary = attack("test", "jab", speed, hit, sound, shape, CollisionType.MELEE);
        return new CombatWeapon.Weapon("test", "melee/test", primary, secondary);
    }
    private static CombatWeapon.Attack attack(String id, String action, float speed, int hit, int sound,
                                               BoundsType shape, CollisionType collisionType) {
        return attackWithDamage(id, action, speed, hit, sound, shape, collisionType, new AttackDamage.Scalar(10));
    }
    private static CombatWeapon.Attack attackWithDamage(String id, String action, float speed, int hit, int sound,
                                                        BoundsType shape, CollisionType collisionType, AttackDamage damage) {
        String soundPrefix = "weapon/" + id + (action.equals("jab") ? "/secondary" : "/primary");
        return new CombatWeapon.Attack(id, damage, hit, collisionType == CollisionType.PROJECTILE ? .25f : .375f,
            new Animation2D.Attack(action, speed, .125f, sound, soundPrefix + "/attack"),
            soundPrefix + "/hit", shape, new CombatWeapon.Bounds(32, 0, 72, 64, ZERO, ZERO, ZERO, ZERO), collisionType);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
    private static void damageWithin(float base, Entity target, String message) {
        var health = Health.MAPPER.get(target);
        int taken = health.getMaximum() - health.getCurrent();
        check(taken >= Math.round(base * .9f) && taken <= Math.round(base * 1.1f), message + ": damage " + taken);
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
            int count = component.getAction().equals("shot") ? 8
                : component.getType() == Animation2D.AnimationType.ATTACK ? 4 : 6;
            var frames = new TextureRegion[count];
            for (int i = 0; i < count; i++) { frames[i] = new TextureRegion(); }
            component.setAnimation(new Animation<>(component.getFrameDuration(), frames), direction);
        }
    }
    private static final class Harness implements AutoCloseable {
        final World world = new World(Vector2.Zero, true);
        final Engine engine = new Engine();
        final EventSystem events = new EventSystem();
        final CombatSystem combatSystem = new CombatSystem(world,
            new Projectile("projectiles/test", 8, 5, CollisionData.ellipse(new CharacterBounds(0, 0, 10, 8.5f)), 0.3f, 16, 32, 90));
        final LoadoutSystem loadouts = new LoadoutSystem();
        final List<String> sounds = new ArrayList<>();
        final Entity player = new Entity();
        final Engine previousEngine;
        Harness(float speed, int hit, int sound) {
            previousEngine = bindEngine(engine);
            engine.addSystem(events);
            events.subscribe(Event.Sound.Play.class, e -> sounds.add(e.soundId()));
            engine.addSystem(new AttackCooldownSystem());
            engine.addSystem(new ControllerSystem()); engine.addSystem(new FacingSystem());
            engine.addSystem(new AnimationPlayerSystem()); engine.addSystem(new FakeAnimationSystem());
            engine.addSystem(combatSystem); engine.addSystem(loadouts); engine.addSystem(new PhysicMoveSystem());
            engine.addSystem(new PhysicSystem(world, STEP));
            var definition = new BodyDef(); definition.type = BodyDef.BodyType.DynamicBody;
            var body = world.createBody(definition); body.setUserData(player);
            fixture(body, .15f, .1f, new Vector2(1, .15f), false);
            player.add(new Physic(body, new Vector2())).add(new Transform(new Vector2(), 0, new Vector2(2, 2), new Vector2(1, 1), 0, 0))
                .add(new Facing(Facing.Direction.RIGHT)).add(new Move(4)).add(new Controller())
                .add(new Combat()).add(new Health(100))
                .add(new Animation2D("OBJECTS", "player", Animation2D.AnimationType.IDLE, Animation.PlayMode.LOOP, 1, .125f))
                .add(new Graphic(new TextureRegion(), Color.WHITE)).add(new AnimationPlayer(player));
            selectWeapon(weapon(speed, hit, sound, BoundsType.SEMICIRCLE));
            engine.addEntity(player); update(0);
        }
        Entity target(int count, boolean sensor) {
            var entity = new Entity(); var definition = new BodyDef(); definition.position.set(1.7f, 1);
            var body = world.createBody(definition); body.setUserData(entity);
            for (int i = 0; i < count; i++) { fixture(body, .08f, .08f, new Vector2(0, i * .03f), sensor); }
            entity.add(new Physic(body, new Vector2(1.7f, 1))).add(new Health(100));
            engine.addEntity(entity); return entity;
        }
        void selectWeapon(CombatWeapon.Weapon weapon) {
            player.add(new Loadout(new Loadout.Equipment("basic", "basic", "basic", "basic"), weapon.id(),
                Map.of(weapon.id(), weapon), equipment -> {}));
        }
        void input(InputAction action, int state) { Controller.MAPPER.get(player).getInputListener().offer(action, state); }
        void attack() { attack(InputAction.ATTACK_PRIMARY); }
        void attack(InputAction action) { input(action, 0); update(0); input(action, 1); update(0); update(0); }
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
