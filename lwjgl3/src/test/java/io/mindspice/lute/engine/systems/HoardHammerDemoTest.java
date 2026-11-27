package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.controllers.ControllerManagerStub;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Exercises the isolated demo through the real menu, map, AI, and remount paths. */
public final class HoardHammerDemoTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;

    public static void main(String[] args) {
        Controllers.preferredManager = ControllerManagerStub.class.getName();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(640, 400);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new HoardHammerDemoTest(), config);
        if (failure != null) { throw new AssertionError("Hoard hammer demo", failure); }
        System.out.println("HoardHammerDemoTest: menu launch, loadout, combatants, AI, no waves and remount passed");
    }

    @Override public void create() {
        try { exercise(); }
        catch (Throwable error) { failure = error; }
        finally { GAME.dispose(); Gdx.app.exit(); }
    }

    private void exercise() {
        GAME.create();
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        for (int i = 0; i < 3; i++) { tap(Input.Keys.DOWN); }
        tap(Input.Keys.ENTER); // Demo is appended after the original four menu rows.
        HoardSystem hoard = null;
        for (int i = 0; i < 300; i++) {
            GAME.render();
            hoard = GAME.getEngine().getSystem(HoardSystem.class);
            if (hoard != null && hoard.isActive()) { break; }
        }
        require(hoard != null && hoard.isActive() && hoard.run() == null, "menu starts an unscored demo directly");

        var engine = GAME.getEngine();
        var tiled = engine.getSystem(TiledSystem.class);
        var player = engine.getEntitiesFor(Family.all(Player.class).get()).first();
        var cycle = GAME.getGameData().weaponsData().weapons().keySet().stream().sorted().toList();
        require(Controller.MAPPER.get(player).getWeaponCycle().equals(cycle), "player cycles every weapon in stable ID order");
        require(player.getComponent(DredgeLunge.class) != null, "arena player receives the hammer-lunge marker");
        require("basic_axe".equals(Loadout.MAPPER.get(player).getWeaponId()), "demo starts with the axe equipped");

        var actors = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        require(actors.size() == 0 && hoard.stamina() == 1d, "demo starts with no enemies and full stamina");
        var clock = engine.getSystem(WorldTimeSystem.class);
        double before = clock.getTotalInGameSeconds();
        clock.update(1f);
        require(clock.getTotalInGameSeconds() == before, "demo keeps arena daylight and freezes session time");
        hoard.afterPhysicsStep(14f);
        require(actors.size() == 0 && hoard.demoStatus().endsWith("1s"), "no early wave");
        hoard.afterPhysicsStep(1f);
        require(actors.size() == 10, "first wave has ten enemies: " + actors.size());
        var center = HoardSystem.demoCenter(tiled.getMapTriggers()).bounds();
        engine.getSystem(AwarenessSystem.class).update(1f / 30f);
        for (var actor : actors) {
            var demo = actor.getComponent(HoardDemoCombatant.class);
            require(demo != null && demo.player() == player && actor.getComponent(ArenaGuard.class) == null,
                "only swarm enemies spawn, with the player as their target");
            require(center.contains(AwarenessSystem.feet(actor)), "enemies spawn in the center zone");
            require(ActorAi.MAPPER.get(actor).mode() == ActorAi.Mode.ENGAGE, "swarm pursues the player");
        }

        hoard.afterPhysicsStep(14f);
        require(actors.size() == 10, "no second wave before 30 seconds");
        hoard.afterPhysicsStep(1f);
        require(hoard.run() == null && actors.size() == 20, "second wave adds ten without a scored run");
        boolean swarmAttacked = false;
        for (int i = 0; i < 600 && !swarmAttacked; i++) {
            engine.update(GameConsts.PHYSIC_INTERVAL);
            for (var actor : actors) {
                if (Combat.MAPPER.get(actor).getAttackId() > 0) { swarmAttacked = true; break; }
            }
        }
        require(swarmAttacked, "the swarm reaches attack range and swings at the player without player input");

        tiled.hideMap();
        tiled.showMap(ManifestLoader.parseMapManifest("hoard_mode"));
        var remountedPlayer = engine.getEntitiesFor(Family.all(Player.class).get()).first();
        require(remountedPlayer != player && remountedPlayer.getComponent(DredgeLunge.class) != null
            && "basic_axe".equals(Loadout.MAPPER.get(remountedPlayer).getWeaponId()),
            "remount rebuilds the axe-equipped demo player");
        require(hoard.isActive() && hoard.run() == null && actors.size() == 0 && hoard.stamina() == 1d,
            "remount resets the wave timer, entities and stamina");
        require(hoard.demoStatus().endsWith("15s"), "new instance has a fresh countdown");
        verifyAxeSpin(remountedPlayer, "basic_axe", List.of(Facing.Direction.RIGHT, Facing.Direction.DOWN,
            Facing.Direction.LEFT, Facing.Direction.UP, Facing.Direction.RIGHT));
        verifyAxeSpin(remountedPlayer, "black_axe", List.of(Facing.Direction.UP, Facing.Direction.RIGHT,
            Facing.Direction.DOWN, Facing.Direction.LEFT, Facing.Direction.UP));
        hoard.afterPhysicsStep(15f);
        require(actors.size() == 10, "new instance spawns its own wave");
        for (var actor : actors) {
            require(actor.getComponent(HoardDemoCombatant.class).player() == remountedPlayer,
                "remounted swarm targets the new player entity");
        }

        var pads = Controllers.getControllers();
        int unused = ControllerMapping.UNDEFINED;
        var mapping = new ControllerMapping(0, unused, unused, unused, unused, 7, unused, unused, unused, unused,
            unused, unused, unused, unused, unused, unused, unused, unused, unused, unused) { };
        boolean[] held = {false};
        var padType = com.badlogic.gdx.controllers.Controller.class;
        var pad = (com.badlogic.gdx.controllers.Controller) Proxy.newProxyInstance(padType.getClassLoader(),
            new Class<?>[]{padType}, (proxy, method, args) -> switch (method.getName()) {
                case "getMapping" -> mapping;
                case "isConnected" -> true;
                case "getAxis" -> 0f;
                case "getButton" -> (int) args[0] == 7 && held[0];
                default -> throw new AssertionError("Unexpected controller call: " + method.getName());
            });
        pads.add(pad);
        try {
            GAME.getInputManager().updateGamepad();
            held[0] = true;
            GAME.getInputManager().updateGamepad();
            Move.MAPPER.get(remountedPlayer).getDirection().set(1, 1);
            float dt = GameConsts.PHYSIC_INTERVAL;
            engine.getSystem(PhysicMoveSystem.class).update(dt);
            engine.getSystem(PhysicSystem.class).update(dt);
            require(hoard.stamina() < 1d, "demo controller sprint consumes stamina while moving");
        } finally {
            pads.removeValue(pad, true);
            GAME.getInputManager().updateGamepad();
        }
    }

    private static void verifyAxeSpin(com.badlogic.ashley.core.Entity player, String weapon, List<Facing.Direction> expected) {
        var engine = GAME.getEngine();
        var facing = Facing.MAPPER.get(player);
        var animation = Animation2D.MAPPER.get(player);
        var combat = Combat.MAPPER.get(player);
        var loadouts = engine.getSystem(LoadoutSystem.class);
        require(loadouts.equipWeapon(player, weapon), "equip axe for spin");
        var secondary = Loadout.MAPPER.get(player).getWeapon().secondary();
        facing.setDirection(expected.getFirst());
        combat.requestAttack(Combat.AttackSlot.SECONDARY);
        var visited = new ArrayList<Facing.Direction>();
        int previousFrame = -1;
        boolean hadSensor = false;
        for (int i = 0; i < 120; i++) {
            engine.update(GameConsts.PHYSIC_INTERVAL);
            if (!combat.isAttacking()) { break; }
            var direction = facing.getDirection();
            if (visited.isEmpty() || visited.getLast() != direction) { visited.add(direction); }
            int frame = animation.getFrameIndex();
            require(frame >= previousFrame && animation.getAction().equals(secondary.animation().action()),
                "turning preserves the secondary frame sequence");
            var region = (AtlasRegion) Graphic.MAPPER.get(player).getRegion();
            require(region.name.equals(animation.regionKey(secondary.animation().action(), direction)),
                "rendered frame is drawn from the matching direction");
            require(region == animation.getAnimation().getKeyFrames()[frame], "renderer uses the current frame index");
            require((combat.getSensor() != null) == animation.hasReachedFrame(secondary.hitFrame()),
                "spin preserves the authored hit frame");
            hadSensor |= combat.getSensor() != null;
            previousFrame = frame;
        }
        require(visited.equals(expected), "one clockwise turn, back to the starting direction: " + visited);
        require(hadSensor && !combat.isAttacking() && combat.getSensor() == null, "spin completes and clears the sensor");
        require(loadouts.equipWeapon(player, "basic_axe"), "restore demo starting axe");
    }

    private static void tap(int key) {
        var input = Gdx.input.getInputProcessor();
        input.keyDown(key);
        input.keyUp(key);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
