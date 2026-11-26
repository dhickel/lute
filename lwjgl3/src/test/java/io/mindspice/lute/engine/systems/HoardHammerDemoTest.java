package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.controllers.ControllerManagerStub;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

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

        var actors = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        Entity dredge = null;
        int swarm = 0;
        for (var actor : actors) {
            var demo = actor.getComponent(HoardDemoCombatant.class);
            if (demo == null) { continue; }
            if (demo.side() == HoardDemoCombatant.Side.DREDGE) { dredge = actor; }
            else { swarm++; }
        }
        require(dredge != null && swarm > 0 && swarm <= 8, "one Dredge and a bounded brainless swarm spawn");
        require("black_hammer".equals(Loadout.MAPPER.get(dredge).getWeaponId())
            && dredge.getComponent(ArenaGuard.class) != null
            && dredge.getComponent(DredgeLunge.class) != null,
            "demo Dredge is explicitly equipped and marked for hammer lunge");
        engine.getSystem(AwarenessSystem.class).update(1f / 30f);
        require(ActorAi.MAPPER.get(dredge).mode() == ActorAi.Mode.ENGAGE,
            "Dredge selects a live swarm target through demo AI state");
        for (var actor : actors) {
            var demo = actor.getComponent(HoardDemoCombatant.class);
            if (demo != null && demo.side() == HoardDemoCombatant.Side.SWARM) {
                require(ActorAi.MAPPER.get(actor).mode() == ActorAi.Mode.ENGAGE,
                    "swarm selects the Dredge rather than the player");
            }
        }

        var clock = engine.getSystem(WorldTimeSystem.class);
        double before = clock.getTotalInGameSeconds();
        clock.update(1f);
        require(clock.getTotalInGameSeconds() == before, "demo keeps arena daylight and freezes session time");
        var physics = engine.getSystem(PhysicSystem.class);
        physics.update(1f);
        require(hoard.run() == null && actors.size() <= 9, "fixed-step updates do not open scored waves");
        boolean swarmAttacked = false;
        for (int i = 0; i < 600 && !swarmAttacked; i++) {
            engine.update(GameConsts.PHYSIC_INTERVAL);
            for (var actor : actors) {
                var demo = actor.getComponent(HoardDemoCombatant.class);
                if (demo != null && demo.side() == HoardDemoCombatant.Side.SWARM
                    && Combat.MAPPER.get(actor).getAttackId() > 0) { swarmAttacked = true; break; }
            }
        }
        require(swarmAttacked, "the swarm reaches attack range and swings at Dredge without player input");

        tiled.hideMap();
        tiled.showMap(ManifestLoader.parseMapManifest("hoard_mode"));
        var remountedPlayer = engine.getEntitiesFor(Family.all(Player.class).get()).first();
        require(remountedPlayer != player && remountedPlayer.getComponent(DredgeLunge.class) != null,
            "remount rebuilds demo player and marker");
        require(hoard.isActive() && hoard.run() == null
            && engine.getEntitiesFor(Family.all(HoardDemoCombatant.class).get()).size() == swarm + 1,
            "remount rebuilds only the bounded demo fight without a run");
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
