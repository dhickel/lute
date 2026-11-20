package io.mindspice.lute.engine.singletons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.mindspice.lute.engine.data.json.DataLoader;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;

public final class GameConsts {
    private GameConsts() { }

    public static float UNIT_SCALE = 1f / 32f;
    public static float VIEW_HEIGHT = 12f;
    public static float VIEW_WIDTH = 21.3f;
    public static float PHYSIC_INTERVAL = 1f / 60f;
    private static final double DEFAULT_DAY_LENGTH_MINUTES = 24.0;
    public static double DAY_LENGTH_MINUTES = DEFAULT_DAY_LENGTH_MINUTES;
    public static NightAmbient NIGHT_AMBIENT;
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
        try (var file = Gdx.files.internal("game.settings.json").read();
             var parser = DataLoader.mapper.createParser(file)) {
            var settings = parseSettings(parser);
            UNIT_SCALE = settings.unitScale();
            VIEW_HEIGHT = settings.viewHeight();
            VIEW_WIDTH = settings.viewWidth();
            PHYSIC_INTERVAL = settings.physicInterval();
            DAY_LENGTH_MINUTES = settings.dayLengthMinutes();
            NIGHT_AMBIENT = settings.nightAmbient();
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

    public record NightAmbient(String tint, float brightness) {
        public NightAmbient {
            if (tint == null || !tint.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("nightAmbient.tint must be #RRGGBB");
            }
            if (!(brightness >= 0f && brightness <= 1f)) {
                throw new IllegalArgumentException("nightAmbient.brightness must be in [0, 1]");
            }
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        private static NightAmbient parse(JsonNode node) {
            if (!node.isObject() || node.size() != 2 || !node.path("tint").isString() || !node.path("brightness").isNumber()) {
                throw new IllegalArgumentException("nightAmbient requires exactly a tint string and numeric brightness");
            }
            var value = node.get("brightness").decimalValue();
            if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("nightAmbient.brightness must be in [0, 1]");
            }
            return new NightAmbient(node.get("tint").stringValue(), value.floatValue());
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
                           String objectPath, String atlasPath, Double dayLengthMinutes, NightAmbient nightAmbient) {
        public Settings {
            dayLengthMinutes = dayLengthMinutes == null ? DEFAULT_DAY_LENGTH_MINUTES : dayLengthMinutes;
            positive(unitScale, "unitScale");
            positive(viewHeight, "viewHeight");
            positive(viewWidth, "viewWidth");
            positive(physicInterval, "physicInterval");
            positive(dayLengthMinutes, "dayLengthMinutes");
            positive(24 * 60 / dayLengthMinutes, "inGameSecondsPerSimulationSecond");
            positive(cameraSmoothFactor, "cameraSmoothFactor");
            Objects.requireNonNull(physicScaling, "physicScaling");
            Objects.requireNonNull(nightAmbient, "nightAmbient");
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
        try (var parser = DataLoader.mapper.createParser(json)) {
            return parseSettings(parser);
        }
    }

    private static Settings parseSettings(JsonParser parser) {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IllegalArgumentException("Game settings must be a JSON object");
        }
        // Keep ordinary tree binding for legacy settings. Only nightAmbient needs exact
        // decimals and duplicate evidence, which would be lost after reading the whole tree.
        var subtree = DataLoader.mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        var ambient = subtree.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .with(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var root = DataLoader.mapper.createObjectNode();
        for (String key; (key = parser.nextName()) != null;) {
            boolean isAmbient = key.equals("nightAmbient");
            if (isAmbient && root.has(key)) { throw new IllegalArgumentException("Duplicate nightAmbient setting"); }
            parser.nextToken();
            root.set(key, (isAmbient ? ambient : subtree).readTree(parser));
        }
        if (parser.currentToken() != JsonToken.END_OBJECT || parser.nextToken() != null) {
            throw new IllegalArgumentException("Expected a single game settings object");
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
