package io.mindspice.lute.engine.singletons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.data.json.DataLoader;

import java.io.IOException;

public final class GameConsts {
    private GameConsts() { }

    public static float UNIT_SCALE = 1f / 32f;
    public static float VIEW_HEIGHT = 12f;
    public static float VIEW_WIDTH = 21.3f;
    public static float PHYSIC_INTERVAL = 1f / 60f;
    private static final double DEFAULT_DAY_LENGTH_MINUTES = 24.0;
    public static double DAY_LENGTH_MINUTES = DEFAULT_DAY_LENGTH_MINUTES;
    public static final Vector2 PHYSIC_SCALING = new Vector2(1f, 1f);
    public static float CAMERA_SMOOTH_FACTOR = 4f;
    public static String PROJECT_PATH;
    public static String STARTUP_MAP;
    public static String MAPS_PATH = "maps/";
    public static String DATA_PATH = "data/";
    public static String SOUND_PATH = "audio/";
    public static String MUSIC_PATH = "audio/";
    public static String OBJECT_PATH = "objects/";
    public static String ATLAS_PATH = "atlases/";

    public static void loadSettings() {
        try (var file = Gdx.files.internal("game.settings.json").read()) {
            var settings = parseSettings(DataLoader.mapper.readTree(file));
            UNIT_SCALE = settings.unitScale();
            VIEW_HEIGHT = settings.viewHeight();
            VIEW_WIDTH = settings.viewWidth();
            PHYSIC_INTERVAL = settings.physicInterval();
            DAY_LENGTH_MINUTES = settings.dayLengthMinutes();
            PHYSIC_SCALING.set(settings.physicScaling().x(), settings.physicScaling().y());
            CAMERA_SMOOTH_FACTOR = settings.cameraSmoothFactor();
            PROJECT_PATH = settings.projectPath();
            STARTUP_MAP = settings.startupMap();
            MAPS_PATH = settings.mapsPath();
            DATA_PATH = settings.dataPath();
            SOUND_PATH = settings.soundPath();
            MUSIC_PATH = settings.musicPath();
            OBJECT_PATH = settings.objectPath();
            ATLAS_PATH = settings.atlasPath();
        } catch (IOException | RuntimeException failure) {
            throw new GdxRuntimeException("Failed to load game.settings.json", failure);
        }
    }

    public record Scaling(float x, float y) {
        public Scaling {
            positive(x, "physicScaling.x");
            positive(y, "physicScaling.y");
        }
    }

    public record Settings(float unitScale, float viewHeight, float viewWidth, float physicInterval,
                           Scaling physicScaling, float cameraSmoothFactor, String projectPath, String startupMap,
                           String mapsPath, String dataPath, String soundPath, String musicPath,
                           String objectPath, String atlasPath, Double dayLengthMinutes) {
        public Settings {
            dayLengthMinutes = dayLengthMinutes == null ? DEFAULT_DAY_LENGTH_MINUTES : dayLengthMinutes;
            positive(unitScale, "unitScale");
            positive(viewHeight, "viewHeight");
            positive(viewWidth, "viewWidth");
            positive(physicInterval, "physicInterval");
            positive(dayLengthMinutes, "dayLengthMinutes");
            positive(24 * 60 / dayLengthMinutes, "inGameSecondsPerSimulationSecond");
            positive(cameraSmoothFactor, "cameraSmoothFactor");
            java.util.Objects.requireNonNull(physicScaling, "physicScaling");
            if (projectPath == null || !projectPath.matches("[^/\\\\\\s:]+\\.tiled-project")) {
                throw new IllegalArgumentException("projectPath must name a .tiled-project file at the asset root");
            }
            if (startupMap == null || startupMap.isBlank() || startupMap.contains("/") || startupMap.contains("\\")
                || startupMap.equals(".") || startupMap.equals("..")) {
                throw new IllegalArgumentException("startupMap must be a map directory name, not a path");
            }
            for (var path : new String[]{mapsPath, dataPath, soundPath, musicPath, objectPath, atlasPath}) {
                if (path == null || path.isBlank() || !path.endsWith("/")) {
                    throw new IllegalArgumentException("Asset directory paths must be nonblank and end with '/'");
                }
            }
        }
    }

    public static Settings parseSettings(String json) {
        try {
            return parseSettings(DataLoader.mapper.readTree(json));
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid game settings JSON", failure);
        }
    }

    private static Settings parseSettings(tools.jackson.databind.JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Game settings must be a JSON object");
        }
        for (var key : new String[]{"projectPath", "startupMap"}) {
            if (!root.path(key).isString()) { throw new IllegalArgumentException(key + " must be a JSON string"); }
        }
        var dayLength = root.get("dayLengthMinutes");
        if (dayLength != null && (!dayLength.isNumber() || dayLength.isNull())) {
            throw new IllegalArgumentException("dayLengthMinutes must be a JSON number");
        }
        return DataLoader.mapper.treeToValue(root, Settings.class);
    }

    private static void positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
