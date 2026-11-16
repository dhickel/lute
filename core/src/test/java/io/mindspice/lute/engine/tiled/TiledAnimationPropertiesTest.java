package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.XmlReader.Element;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.properties.AnimatedPhysicObjectProps;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class TiledAnimationPropertiesTest {
    public static void main(String[] args) {
        var types = new JsonReader().parse(new FileHandle("assets/game.tiled-project")).get("propertyTypes");
        Set<String> members = new HashSet<>();
        boolean nested = false;
        for (var type : types) {
            if ("Animation".equals(type.getString("name"))) {
                for (var member : type.get("members")) { members.add(member.getString("name")); }
            }
            if ("AnimatedPhysicObject".equals(type.getString("name"))) {
                for (var member : type.get("members")) {
                    check(!"animationType".equals(member.getString("name")), "Object class must not configure animation type");
                    if ("animation".equals(member.getString("name"))) {
                        nested = "class".equals(member.getString("type")) && "Animation".equals(member.getString("propertyType"));
                    }
                }
            }
            if ("SpawnPoint".equals(type.getString("name"))) {
                for (var member : type.get("members")) {
                    check(!"animationType".equals(member.getString("name")), "Player class must not configure animation type");
                }
            }
        }
        check(nested && members.equals(Set.of("speed", "atlasKey", "soundFrame", "frameInterval")),
            "AnimatedPhysicObject must use the shared camelCase Animation class");
        var parameters = new BaseTiledMapLoader.Parameters();
        parameters.projectFilePath = GameConsts.PROJECT_PATH;
        TiledMap map = new PropertyLoader().loadProperties(Asset.Map.of("TEST", "test_tiled.tmx").path(), parameters);
        try {
            int trees = 0;
            TiledMapTileMapObject tree = null;
            for (var object : map.getLayers().get("Objects").getObjects()) {
                if (object instanceof TiledMapTileMapObject tile
                    && tile.getTile().getProperties().get("animation", MapProperties.class) != null) {
                    tree = tile;
                    var props = AnimatedPhysicObjectProps.fromMapObj(tile);
                    var data = props.animation();
                    check(data.atlasKey().equals("old_tree1/old_tree1")
                        && data.frameInterval() == .1f && data.speed() == .35f && data.soundFrame() == 0,
                        "Tree must use nested authored animation fields");
                    trees++;
                }
            }
            check(trees == 5, "Expected five animated trees");
            var point = map.getLayers().get("Triggers").getObjects().get("PlayerSpawn");
            check(point.getProperties().get("animationType") == null, "Player does not configure animation type");
            check(point.getProperties().get("keyFrames") == null && point.getProperties().get("atlasAsset") == null,
                "Player spawn has no legacy atlas or keyFrames");
            var override = new MapProperties();
            override.put("speed", 1f); override.put("atlasKey", "other");
            override.put("soundFrame", 0); override.put("frameInterval", .2f);
            tree.getProperties().put("animation", override);
            check(AnimatedPhysicObjectProps.fromMapObj(tree).animation().frameInterval() == .2f,
                "Placement animation replaces tile animation");
            override.put("frameInterval", 0f);
            final var selected = tree;
            rejects(() -> AnimatedPhysicObjectProps.fromMapObj(selected));
            tree.getProperties().remove("animation");
            check(AnimatedPhysicObjectProps.fromMapObj(tree).animation().frameInterval() == .1f,
                "Removing override restores tile animation");
            tree.getTile().getProperties().remove("animation");
            rejects(() -> AnimatedPhysicObjectProps.fromMapObj(selected));
            var fixture = new PropertyLoader().loadWithPartialOverride(Asset.Map.of("TEST", "test_tiled.tmx").path(), parameters);
            try {
                var overridden = (TiledMapTileMapObject) fixture.getLayers().get("Objects").getObjects().get(1);
                var actual = AnimatedPhysicObjectProps.fromMapObj(overridden).animation();
                check(actual.frameInterval() == .2f && actual.speed() == 1f && actual.atlasKey().equals("old_tree1/old_tree1"),
                    "Partial class override preserves tile fields and explicit class-default values");
                var other = (TiledMapTileMapObject) fixture.getLayers().get("Objects").getObjects().get(2);
                check(AnimatedPhysicObjectProps.fromMapObj(other).animation().frameInterval() == .1f,
                    "Placement overrides must not mutate the shared tile");
            } finally { fixture.dispose(); }
            System.out.println("TiledAnimationPropertiesTest: passed (no graphics context or game launch)");
        } finally { map.dispose(); }
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { return; }
        throw new AssertionError("Invalid animation property was accepted");
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
    private static final class PropertyLoader extends FixedTmxMapLoader {
        PropertyLoader() { super(path -> new FileHandle("assets/" + path)); }
        TiledMap loadProperties(String path, BaseTiledMapLoader.Parameters parameters) {
            return load(path, parameters, false);
        }
        TiledMap loadWithPartialOverride(String path, BaseTiledMapLoader.Parameters parameters) {
            return load(path, parameters, true);
        }
        private TiledMap load(String path, BaseTiledMapLoader.Parameters parameters, boolean override) {
            var file = resolve(path);
            root = xml.parse(file);
            if (override) {
                for (var object : root.getChildrenByNameRecursively("object")) {
                    if (!"8".equals(object.getAttribute("id", null))) { continue; }
                    var properties = new Element("properties", object);
                    var animation = new Element("property", properties);
                    animation.setAttribute("name", "animation");
                    animation.setAttribute("type", "class");
                    animation.setAttribute("propertytype", "Animation");
                    var fields = new Element("properties", animation);
                    var interval = new Element("property", fields);
                    interval.setAttribute("name", "frameInterval");
                    interval.setAttribute("type", "float");
                    interval.setAttribute("value", "0.2");
                    var speed = new Element("property", fields);
                    speed.setAttribute("name", "speed");
                    speed.setAttribute("type", "float");
                    speed.setAttribute("value", "1.0");
                    fields.addChild(interval); fields.addChild(speed); animation.addChild(fields); properties.addChild(animation); object.addChild(properties);
                }
            }
            return loadTiledMap(file, parameters, imagePath -> {
                try {
                    var image = ImageIO.read(new java.io.File(imagePath));
                    return new TextureRegion(new DimensionTexture(image.getWidth(), image.getHeight()));
                } catch (IOException failure) { throw new AssertionError(failure); }
            });
        }
    }
    private static final class DimensionTexture extends Texture {
        private final int width, height;
        DimensionTexture(int width, int height) { super(); this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
    }
}
