package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.systems.EntityFactory;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;
import io.mindspice.lute.engine.tiled.properties.PlayerCharProps;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Map;

public final class TiledAnimationPropertiesTest {
    public static void main(String[] args) {
        var mapAsset = Asset.Map.of("test_tiled.tmx");
        var parameters = (BaseTiledMapLoader.Parameters) mapAsset.descriptor().params;
        TiledMap map = new PropertyLoader().loadProperties(mapAsset.descriptor().fileName, parameters);
        try {
            int trees = 0;
            TiledMapTileMapObject tree = null;
            for (var object : map.getLayers().get("Objects").getObjects()) {
                if (object instanceof TiledMapTileMapObject tileObject
                        && "old_tree1/old_tree1".equals(tileObject.getTile().getProperties().get("atlasKey"))) {
                    tree = tileObject;
                    var props = AnimatedPhysicObjectProps.fromMapObj(tree);
                    check(props.keyframes() == 10, "Tree " + object.getProperties().get("id") + " must retain tile keyFrames=10");
                    check(props.animationSpeed() == 0.35f, "Explicit tile animationSpeed must override the integer class default");
                    trees++;
                }
            }
            check(trees == 5, "Expected five animated trees");

            PointMapObject point = null;
            for (var object : map.getLayers().get("Triggers").getObjects()) {
                if (object instanceof PointMapObject spawn
                        && "PlayerCharacter".equals(object.getProperties().get("type"))) {
                    point = spawn;
                    break;
                }
            }
            check(point != null, "Expected a PlayerCharacter spawn point");
            var player = PlayerCharProps.fromMapObj(point);
            System.out.println("PlayerSpawn id=" + point.getProperties().get("id")
                + " loaded keyFrames=" + point.getProperties().get("keyFrames")
                + ", parsed keyFrames=" + player.keyframes() + "; animated trees=" + trees);
            check(Integer.valueOf(8).equals(point.getProperties().get("keyFrames")),
                "PlayerSpawn must inherit keyFrames=8 from the project, not rely on a parser fallback");
            check(player.keyframes() == 8, "Player parser must preserve the inherited frame rate");

            var atlas = Asset.Atlas.of("objects.atlas");
            var assets = new MapAssets(mapAsset, null, Map.of("OBJECTS", atlas), Map.of(), Map.of());
            var factory = new EntityFactory(null, null, null);
            var spawnPosition = point.getPoint();
            for (int invalid : new int[]{0, -1}) {
                point.getProperties().put("keyFrames", invalid);
                var invalidPlayer = PlayerCharProps.fromMapObj(point);
                check(invalidPlayer.keyframes() == invalid, "An explicit invalid player value must not be replaced");
                rejectsFrames(() -> factory.getPlayerEntity(invalidPlayer, spawnPosition, assets), "player", atlas.file());

                tree.getProperties().put("keyFrames", invalid);
                var invalidTree = AnimatedPhysicObjectProps.fromMapObj(tree);
                check(invalidTree.keyframes() == invalid, "An explicit placement value must override its tile");
                TiledMapTileMapObject loadedTree = tree;
                rejectsFrames(() -> factory.getAnimatedPhysicObjectEntity(invalidTree, loadedTree, assets),
                    "old_tree1/old_tree1", atlas.file());
            }
            tree.getProperties().remove("keyFrames");
            check(AnimatedPhysicObjectProps.fromMapObj(tree).keyframes() == 10, "Placement overrides must not mutate the tile");
            tree.getTile().getProperties().remove("keyFrames");
            try {
                AnimatedPhysicObjectProps.fromMapObj(tree);
                throw new AssertionError("Missing required keyFrames must fail");
            } catch (NullPointerException expected) {
                check("keyFrames".equals(expected.getMessage()), "Missing-property error must identify keyFrames");
            }
            System.out.println("TiledAnimationPropertiesTest: passed (no graphics context or game launch)");
        } finally {
            map.dispose();
        }
    }

    private static void rejectsFrames(Runnable action, String atlasKey, String atlasFile) {
        try {
            action.run();
            throw new AssertionError("Invalid keyFrames must fail");
        } catch (GdxRuntimeException expected) {
            check(expected.getMessage().contains("keyFrames must be positive"), "Frame validation must remain strict");
            check(expected.getMessage().contains(atlasKey), "Frame validation must identify the animation key");
            check(expected.getMessage().contains(atlasFile), "Frame validation must identify the atlas asset");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class PropertyLoader extends FixedTmxMapLoader {
        PropertyLoader() {
            super(path -> new FileHandle("assets/" + path));
        }

        TiledMap loadProperties(String path, BaseTiledMapLoader.Parameters parameters) {
            var file = resolve(path);
            root = xml.parse(file);
            return loadTiledMap(file, parameters, imagePath -> {
                try {
                    var image = ImageIO.read(new java.io.File(imagePath));
                    return new TextureRegion(new DimensionTexture(image.getWidth(), image.getHeight()));
                } catch (IOException failure) {
                    throw new AssertionError("Cannot read image dimensions: " + imagePath, failure);
                }
            });
        }
    }

    private static final class DimensionTexture extends Texture {
        private final int width;
        private final int height;

        DimensionTexture(int width, int height) {
            super();
            this.width = width;
            this.height = height;
        }

        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
    }
}
