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
    public static final Vector2 PHYSIC_SCALING = new Vector2(1f, 1f);
    public static float CAMERA_SMOOTH_FACTOR = 4f;
    public static String PROJECT_PATH = "game.tiled-project";
    public static String MAPS_PATH = "maps/";
    public static String DATA_PATH = "data/";
    public static String SOUND_PATH = "audio/";
    public static String MUSIC_PATH = "audio/";
    public static String OBJECT_PATH = "objects/";
    public static String ATLAS_PATH = "atlases/";

    public static void loadSettings() {
        try (var file = Gdx.files.internal("game.settings.json").read()) {
            var settings = DataLoader.mapper.readValue(file, Settings.class);
            UNIT_SCALE = settings.unitScale();
            VIEW_HEIGHT = settings.viewHeight();
            VIEW_WIDTH = settings.viewWidth();
            PHYSIC_INTERVAL = settings.physicInterval();
            PHYSIC_SCALING.set(settings.physicScaling().x(), settings.physicScaling().y());
            CAMERA_SMOOTH_FACTOR = settings.cameraSmoothFactor();
            PROJECT_PATH = settings.projectPath();
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
                           Scaling physicScaling, float cameraSmoothFactor, String projectPath,
                           String mapsPath, String dataPath, String soundPath, String musicPath,
                           String objectPath, String atlasPath) {
        public Settings {
            positive(unitScale, "unitScale");
            positive(viewHeight, "viewHeight");
            positive(viewWidth, "viewWidth");
            positive(physicInterval, "physicInterval");
            positive(cameraSmoothFactor, "cameraSmoothFactor");
            java.util.Objects.requireNonNull(physicScaling, "physicScaling");
            if (projectPath == null || projectPath.isBlank()) {
                throw new IllegalArgumentException("projectPath is required");
            }
            for (var path : new String[]{mapsPath, dataPath, soundPath, musicPath, objectPath, atlasPath}) {
                if (path == null || path.isBlank() || !path.endsWith("/")) {
                    throw new IllegalArgumentException("Asset directory paths must be nonblank and end with '/'");
                }
            }
        }
    }

    private static void positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
