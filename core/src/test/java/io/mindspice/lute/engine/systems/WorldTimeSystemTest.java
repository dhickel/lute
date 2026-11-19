package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import io.mindspice.lute.engine.singletons.GameConsts;

public final class WorldTimeSystemTest {
    private static int checks;

    public static void main(String[] args) {
        settingsSchema();

        var time = new WorldTimeSystem(24);
        near(time.getTotalInGameSeconds(), 21_600, "Starts at 06:00");
        require(time.getDayIndex() == 0, "Starts on day zero");
        near(time.getMinuteOfDay(), 360, "Reports the starting minute of day");

        time.update(.25f);
        near(time.getTotalInGameSeconds(), 21_615, "Advances at 60 in-game seconds per simulation second");
        near(time.getMinuteOfDay(), 360.25, "Preserves fractional minutes");

        var customRate = new WorldTimeSystem(48);
        customRate.update(1f);
        near(customRate.getTotalInGameSeconds(), 21_630, "Uses the configured day length for its rate");
        near(customRate.getDayLengthMinutes(), 48, "Reports the configured day length");

        time.update((float) ((86_400 - 21_615) / 60.0));
        near(time.getTotalInGameSeconds(), 86_400, "Uses total seconds for midnight rollover");
        require(time.getDayIndex() == 1, "Rolls to the next zero-based day");
        near(time.getMinuteOfDay(), 0, "Resets minute of day at midnight");

        time.update(24 * 60);
        require(time.getDayIndex() == 2, "Rolls over another complete day");
        near(time.getMinuteOfDay(), 0, "Keeps midnight stable after another day");

        var engine = new Engine();
        var installedTime = new WorldTimeSystem(24);
        var observed = new ObservationSystem(installedTime);
        engine.addSystem(observed);
        engine.addSystem(installedTime);
        engine.update(1 / 60f);
        near(observed.observedTotal, installedTime.getTotalInGameSeconds(), "Priority updates the clock before default systems");
        require(engine.getSystems().first() == installedTime, "Clock has an explicit priority before default systems");

        System.out.println("WorldTimeSystemTest: " + checks + " checks passed");
    }

    private static void settingsSchema() {
        var omitted = GameConsts.parseSettings(settingsJson(null));
        near(omitted.dayLengthMinutes(), 24, "Omitted day length uses the default");

        var custom = GameConsts.parseSettings(settingsJson("7.5"));
        near(custom.dayLengthMinutes(), 7.5, "Settings preserve fractional day lengths");

        rejects(() -> GameConsts.parseSettings(settingsJson("1e-323")),
            "day length with a nonfinite clock rate");
        rejects(() -> GameConsts.parseSettings(settingsJson("null")),
            "explicit null day length");
        for (String invalid : new String[]{"0", "-1", "1e309", "\"24\""}) {
            rejects(() -> GameConsts.parseSettings(settingsJson(invalid)),
                "invalid day length: " + invalid);
        }
    }

    private static String settingsJson(String dayLengthMinutes) {
        return "{"
            + "\"unitScale\":0.03125,\"viewHeight\":18,\"viewWidth\":32,"
            + "\"physicInterval\":0.016666668,\"physicScaling\":{\"x\":1,\"y\":1},"
            + "\"cameraSmoothFactor\":4,\"projectPath\":\"game.tiled-project\","
            + "\"mapsPath\":\"maps/\",\"dataPath\":\"data/\",\"soundPath\":\"audio/\","
            + "\"musicPath\":\"audio/\",\"objectPath\":\"objects/\",\"atlasPath\":\"atlases/\""
            + (dayLengthMinutes == null ? "" : ",\"dayLengthMinutes\":" + dayLengthMinutes) + "}";
    }

    private static final class ObservationSystem extends EntitySystem {
        private final WorldTimeSystem time;
        private double observedTotal;

        private ObservationSystem(WorldTimeSystem time) {
            this.time = time;
        }

        @Override
        public void update(float deltaTime) {
            observedTotal = time.getTotalInGameSeconds();
        }
    }

    private static void near(double actual, double expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static void rejects(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (RuntimeException expected) { return; }
        throw new AssertionError("Accepted " + message);
    }
}
