package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.XmlReader.Element;
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
                    check(props.keyFrames() == 10, "Tree " + object.getProperties().get("id") + " must retain tile keyFrames=10");
                    check(props.animationSpeed() == 0.35f, "Explicit tile animationSpeed must override the integer class default");
                    trees++;
                }
            }
            check(trees == 5, "Expected five animated trees");

            var point = playerSpawn(map);
            var player = PlayerCharProps.fromMapObj(point);
            System.out.println("PlayerSpawn id=" + point.getProperties().get("id")
                + " loaded keyFrames=" + point.getProperties().get("keyFrames")
                + ", parsed keyFrames=" + player.keyFrames() + "; animated trees=" + trees);
            check(Integer.valueOf(8).equals(point.getProperties().get("keyFrames")),
                "PlayerSpawn id=18 must load keyFrames=8 from its explicit map property");
            check(player.keyFrames() == 8, "Player parser must preserve the explicit frame rate");
            check(map.getTileSets().getTile(2663).getProperties().get("keyFrames", Integer.class) == 8,
                "The unused player tile has an independent keyFrames=8");

            var atlas = Asset.Atlas.of("objects.atlas");
            var assets = new MapAssets(mapAsset, null, Map.of("OBJECTS", atlas), Map.of(), Map.of());
            var factory = new EntityFactory(null, null, null);
            checkPlayerPrecedence(mapAsset, parameters, factory, assets, atlas);
            for (int invalid : new int[]{0, -1}) {
                tree.getProperties().put("keyFrames", invalid);
                var invalidTree = AnimatedPhysicObjectProps.fromMapObj(tree);
                check(invalidTree.keyFrames() == invalid, "An explicit placement value must override its tile");
                TiledMapTileMapObject loadedTree = tree;
                rejectsFrames(() -> factory.getAnimatedPhysicObjectEntity(invalidTree, loadedTree, assets),
                    invalid, "old_tree1/old_tree1", atlas.descriptor().fileName);
            }
            tree.getProperties().remove("keyFrames");
            check(AnimatedPhysicObjectProps.fromMapObj(tree).keyFrames() == 10, "Placement overrides must not mutate the tile");
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

    private static PointMapObject playerSpawn(TiledMap map) {
        for (var object : map.getLayers().get("Triggers").getObjects()) {
            if (Integer.valueOf(18).equals(object.getProperties().get("id"))) {
                check(object instanceof PointMapObject, "PlayerSpawn must remain a point, not a tile object");
                check("PlayerCharacter".equals(object.getProperties().get("type")), "Expected PlayerCharacter class");
                return (PointMapObject) object;
            }
        }
        throw new AssertionError("Expected PlayerSpawn id=18");
    }

    private static void checkPlayerPrecedence(Asset.Map mapAsset, BaseTiledMapLoader.Parameters parameters,
                                              EntityFactory factory, MapAssets assets, Asset.Atlas atlas) {
        String path = mapAsset.descriptor().fileName;
        for (int classFrames : new int[]{0, 8}) {
            // Load the saved point unchanged: a class-default-only repair must not satisfy this regression.
            var savedMap = new PropertyLoader(classFrames).loadProperties(path, parameters);
            try {
                check(PlayerCharProps.fromMapObj(playerSpawn(savedMap)).keyFrames() == 8,
                    "Saved PlayerSpawn must explicitly override project keyFrames=" + classFrames);
            } finally {
                savedMap.dispose();
            }
            for (Integer explicitFrames : new Integer[]{null, 0, -1, 8}) {
                var fixture = new PropertyLoader(classFrames).loadProperties(path, parameters, explicitFrames);
                try {
                    var point = playerSpawn(fixture);
                    int expected = explicitFrames == null ? classFrames : explicitFrames;
                    String source = "PlayerSpawn id=18: class keyFrames=" + classFrames + ", explicit=" + explicitFrames;
                    check(Integer.valueOf(expected).equals(point.getProperties().get("keyFrames")),
                        source + " must obey explicit-over-class precedence");
                    var props = PlayerCharProps.fromMapObj(point);
                    check(props.keyFrames() == expected, source + " must not be silently replaced by the parser");
                    if (expected <= 0) {
                        rejectsFrames(() -> factory.getPlayerEntity(props, point.getPoint(), assets),
                            expected, "player/idle_right", atlas.descriptor().fileName);
                    }
                    check(fixture.getTileSets().getTile(2663).getProperties().get("keyFrames", Integer.class) == 8,
                        source + " must be independent of the unused player's tile keyFrames=8");
                } finally {
                    fixture.dispose();
                }
            }
        }
        System.out.println("Player precedence: saved override against class defaults 0/8; eight loaded fixture cases passed");
    }

    private static void rejectsFrames(Runnable action, int keyFrames, String animationKey, String atlasFile) {
        try {
            action.run();
            throw new AssertionError("Invalid keyFrames must fail for " + animationKey);
        } catch (GdxRuntimeException expected) {
            check(expected.getMessage().contains("keyFrames must be positive: " + keyFrames),
                "Frame validation must reject the actual invalid value: " + expected.getMessage());
            check(expected.getMessage().contains("key: " + animationKey + ", atlas: " + atlasFile),
                "Frame validation must identify the actual animation and atlas: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class PropertyLoader extends FixedTmxMapLoader {
        private final Integer classFrames;

        PropertyLoader() {
            this(null);
        }

        PropertyLoader(Integer classFrames) {
            super(path -> new FileHandle("assets/" + path));
            this.classFrames = classFrames;
        }

        @Override
        protected void loadProjectFile(String path) {
            super.loadProjectFile(path);
            if (classFrames != null) {
                for (var member : projectClassInfo.get("PlayerCharacter")) {
                    if ("keyFrames".equals(member.name)) {
                        member.defaultValue.set(classFrames.longValue(), null);
                    }
                }
            }
        }

        TiledMap loadProperties(String path, BaseTiledMapLoader.Parameters parameters, Integer explicitFrames) {
            return loadProperties(path, parameters, true, explicitFrames);
        }

        TiledMap loadProperties(String path, BaseTiledMapLoader.Parameters parameters) {
            return loadProperties(path, parameters, false, null);
        }

        private TiledMap loadProperties(String path, BaseTiledMapLoader.Parameters parameters,
                                       boolean replaceSpawnFrames, Integer explicitFrames) {
            var file = resolve(path);
            root = xml.parse(file);
            if (replaceSpawnFrames) {
                for (var object : root.getChildrenByNameRecursively("object")) {
                    if (!"18".equals(object.getAttribute("id", null))) { continue; }
                    var properties = object.getChildByName("properties");
                    if (properties == null) {
                        properties = new Element("properties", object);
                        object.addChild(properties);
                    }
                    for (var property : properties.getChildrenByName("property")) {
                        if ("keyFrames".equals(property.getAttribute("name"))) {
                            properties.removeChild(property);
                        }
                    }
                    if (explicitFrames != null) {
                        var property = new Element("property", properties);
                        property.setAttribute("name", "keyFrames");
                        property.setAttribute("type", "int");
                        property.setAttribute("value", explicitFrames.toString());
                        properties.addChild(property);
                    }
                }
            }
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
