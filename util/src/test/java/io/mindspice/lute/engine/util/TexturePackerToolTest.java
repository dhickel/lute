package io.mindspice.lute.engine.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TexturePackerToolTest {
    private static final String[] REQUIRED_SPRITE_INPUTS = {"character/player", "character/basic_npc",
        "enemies/brainless_undead", "equipment/basic/body", "equipment/basic/head",
        "equipment/basic/feet", "equipment/basic/hands", "weapons/melee/basic_sword", "weapons/ranged/basic_bow", "objects/campfire1"};

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("lute-runtime-generation-");
        try {
            Path raw = directory.resolve("raw_assets");
            Path output = directory.resolve("assets");
            requiredSpriteInputs(directory.resolve("required-sprites"));
            createInputs(raw);
            createMap(raw, "alpha");
            createMap(raw, "beta");
            Files.createDirectories(output);
            Path sentinel = output.resolve("game.tiled-session");
            Files.writeString(sentinel, "do not touch");
            Path interrupted = directory.resolve(".runtime-assets-transaction");
            Files.createDirectories(interrupted.resolve("previous"));
            Files.writeString(interrupted.resolve("previous/recovery-data"), "retain backup");
            rejects(() -> TexturePackerTool.generate(raw, output));
            check(Files.readString(interrupted.resolve("previous/recovery-data")).equals("retain backup"),
                "Concurrent or interrupted transaction must be retained, never overwritten or removed");
            deleteTree(interrupted);
            TexturePackerTool.generate(raw, output);
            for (String name : new String[]{"alpha", "beta"}) {
                Path atlasFile = output.resolve("atlases/" + name + ".atlas");
                check(Files.isRegularFile(atlasFile), "Per-map atlas must use directory name");
                var atlas = new TextureAtlasData(new FileHandle(atlasFile.toFile()),
                    new FileHandle(output.resolve("atlases").toFile()), false);
                check(atlas.getRegions().size == 2, "Both static and indexed images must be packed");
                check(hasRegion(atlas, "house", -1), "Static region name must remain stable");
                check(hasRegion(atlas, "tree/tree", 0), "Nested indexed region names must remain stable");
                check(Files.exists(output.resolve("maps/" + name + "/objects/objects.tsx")), "Retain TSX definitions");
                check(Files.exists(output.resolve("tilesets/shared/terrain.png.tiles-8x8-m0-s0.png")), "Share extruded terrain image");
                check(!Files.exists(output.resolve("tilesets/shared/terrain.png")), "Do not publish unused unpadded terrain image");
                check(Files.exists(output.resolve("tilesets/shared/terrain.tsx")), "Retain shared terrain definition");
                check(Files.exists(output.resolve("maps/" + name + "/" + name + ".manifest.json")), "Manifest remains with map");
                check(!Files.exists(output.resolve("maps/" + name + "/objects/house.png")), "Never publish object source PNGs");
                check(Files.exists(raw.resolve("maps/" + name + "/objects/house.png")), "Generation must retain editable source");
            }
            String before = Files.readString(output.resolve("atlases/alpha.atlas"));
            TexturePackerTool.generate(raw, output);
            check(Files.readString(sentinel).equals("do not touch"), "Regeneration must preserve unrelated output");
            check(Files.readString(output.resolve("atlases/alpha.atlas")).equals(before), "Packing must be deterministic");
            rejects(() -> TexturePackerTool.generate(raw, raw));
            rejects(() -> TexturePackerTool.generate(raw, directory));
            Path link = output.resolve("linked");
            Files.createSymbolicLink(link, raw);
            rejects(() -> TexturePackerTool.generate(raw, output));
            Files.delete(link);
            Files.createSymbolicLink(raw.resolve("linked"), output);
            rejects(() -> TexturePackerTool.generate(raw, output));
            Files.delete(raw.resolve("linked"));
            Files.delete(raw.resolve("maps/beta/objects/house.png"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            check(Files.readString(output.resolve("atlases/alpha.atlas")).equals(before), "Failed generation must not publish earlier maps");
            check(Files.readString(sentinel).equals("do not touch"), "Failure must not remove unrelated files");
            createMap(raw, "beta");
            Files.writeString(raw.resolve("maps/beta/objects/pack.json"), "{\"rotation\":true}");
            rejects(() -> TexturePackerTool.generate(raw, output));
            createMap(raw, "beta");
            Path mapPacking = raw.resolve("maps/beta/objects/pack.json");
            Files.writeString(mapPacking, Files.readString(mapPacking)
                .replace("\"combineSubdirectories\":true", "\"combineSubdirectories\":false"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            check(Files.readString(output.resolve("atlases/alpha.atlas")).equals(before),
                "Map objects still require combined packing and must not publish on failure");
            createMap(raw, "beta");
            Files.writeString(raw.resolve("maps/beta/objects/tree/pack.json"), "{\"rotation\":true}");
            rejects(() -> TexturePackerTool.generate(raw, output));
            Files.delete(raw.resolve("maps/beta/objects/tree/pack.json"));
            Path manifest = raw.resolve("maps/beta/beta.manifest.json");
            Files.writeString(manifest, Files.readString(manifest).replace("\"sound_assets\":{}", "\"sound_assets\":null"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            createMap(raw, "beta");
            Files.writeString(manifest, Files.readString(manifest).replace("\"atlasAsset\":", "\"atlas_asset\":\"wrong.atlas\",\"atlasAsset\":"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            createMap(raw, "beta");
            Path map = raw.resolve("maps/beta/beta.tmx");
            Files.writeString(map, Files.readString(map).replace("../../tilesets/shared/terrain.tsx", "../../../outside.tsx"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            try (var paths = Files.list(directory)) {
                check(paths.noneMatch(path -> path.getFileName().toString().startsWith(".runtime-assets-")),
                    "Temporary output must be cleaned after success and failure");
            }
            terrainExtrusion(directory.resolve("terrain-extrusion"));
            terrainOverflow(directory.resolve("terrain-overflow"));
            ownershipAndStaleOutputs(directory.resolve("ownership"));
            atlasCollisions(directory.resolve("collisions"));
            publicationRollback(directory.resolve("publication"));
            spriteAtlases(directory.resolve("sprites"));
            sharedObjectsAtlas(directory.resolve("shared-objects"));
            spritePageDefaults(directory.resolve("sprite-page-defaults"));
            singleFrameArrows(directory.resolve("single-frame-arrows"));
            System.out.println("TexturePackerToolTest: passed (required sprite inputs, names, two maps, ownership, stale outputs, collisions, rollback, path and symlink safety)");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
        }
    }

    private static void terrainExtrusion(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        createMap(raw, "beta");
        Files.writeString(raw.resolve("maps/beta/beta.tmx"), """
            <map><tileset firstgid="51" source="../../tilesets/shared/terrain.tsx"/>
            <layer width="1" height="1"><data encoding="csv">52</data></layer></map>
            """);
        Path shared = raw.resolve("tilesets/shared");
        var source = new BufferedImage(7, 7, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) { source.setRGB(x, y, 0x80000000 | x << 16 | y << 8 | 0x44); }
        }
        source.setRGB(1, 1, 0x00123456); // Transparent pixels may still carry authored RGB.
        ImageIO.write(source, "png", shared.resolve("terrain.png").toFile());
        Path tsx = shared.resolve("terrain.tsx");
        String external = """
            <tileset name="terrain" tilewidth="2" tileheight="2" columns="2" tilecount="99" margin="1" spacing="1">
              <image source="terrain.png" width="7" height="7"/>
              <tile id="2"><animation><frame tileid="3" duration="90"/></animation></tile>
            </tileset>
            """;
        Files.writeString(tsx, external);
        Path map = raw.resolve("maps/alpha/alpha.tmx");
        String authoredMap = """
            <map><tileset firstgid="1" source="objects/objects.tsx"/>
              <tileset firstgid="3" source="../../tilesets/shared/terrain.tsx"/>
              <tileset firstgid="7" name="inline" tilewidth="7" tileheight="7">
                <image source="../../tilesets/shared/terrain.png"/>
              </tileset>
              <group><imagelayer><image source="../../tilesets/shared/terrain.png"/></imagelayer></group>
              <layer width="3" height="1"><data encoding="csv">3,7,6</data></layer>
            </map>
            """;
        Files.writeString(map, authoredMap);
        var authoredSheet = snapshot(shared);
        TexturePackerTool.generate(raw, output);
        Path generatedTsx = output.resolve(raw.relativize(tsx));
        Path generatedTmx = output.resolve(raw.relativize(map));
        var xml = new XmlReader();
        var terrain = xml.parse(new FileHandle(generatedTsx.toFile()));
        var padded = checkExtrudedSheet(generatedTsx, terrain, 2, 2, 8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int sourceX = 1 + 3 * (x / 4) + Math.clamp(x % 4 - 1, 0, 1);
                int sourceY = 1 + 3 * (y / 4) + Math.clamp(y % 4 - 1, 0, 1);
                check(padded.getRGB(x, y) == source.getRGB(sourceX, sourceY), "Preserve tile pixels and extrude RGBA edges/corners");
            }
        }
        var tile = terrain.getChildByName("tile");
        check(tile.getIntAttribute("id") == 2 && tile.getChildByName("animation").getChildByName("frame").getIntAttribute("tileid") == 3,
            "Retain tile and animation IDs");
        var generatedMap = xml.parse(new FileHandle(generatedTmx.toFile()));
        var inline = generatedMap.getChildrenByName("tileset").get(2);
        var inlineImage = checkExtrudedSheet(generatedTmx, inline, 1, 1, 9, 9);
        check(inline.getIntAttribute("firstgid") == 7 && inlineImage.getRGB(8, 8) == source.getRGB(6, 6),
            "Retain inline firstgid and use the separate layout for the same source image");
        check(generatedMap.getChildByName("layer").getChildByName("data").getText().equals("3,7,6"), "Retain map tile data");
        check(Files.mismatch(shared.resolve("terrain.png"), output.resolve("tilesets/shared/terrain.png")) == -1,
            "Keep the original image unchanged when used by an image layer");
        var atlasDir = new FileHandle(output.resolve("atlases").toFile());
        var secondMap = new TextureAtlasData(atlasDir.child("beta.atlas"), atlasDir, false);
        check(secondMap.getRegions().size == 3 && hasRegion(secondMap, "__lute_tile/51", 1),
            "A map sharing the same TSX with a different firstgid must pack only its own tile and objects");
        check(!terrain.hasAttribute("luteAtlasLocalIds"), "A map-specific subset must never leak into a shared TSX");
        var atlas = new TextureAtlasData(atlasDir.child("alpha.atlas"), atlasDir, false);
        var keys = new java.util.HashSet<String>();
        for (var region : atlas.getRegions()) {
            if (!region.name.startsWith("__lute_tile/")) { continue; }
            String key = region.name + ":" + region.index;
            keys.add(key);
            var page = ImageIO.read(region.page.textureFile.file());
            int size = region.name.equals("__lute_tile/7") ? 7 : 2;
            int start = switch (key) {
                case "__lute_tile/3:0" -> 1;
                case "__lute_tile/3:3" -> 4;
                case "__lute_tile/7:0" -> 0;
                default -> throw new AssertionError("Unexpected terrain region " + key);
            };
            for (int y = -1; y <= size; y++) {
                for (int x = -1; x <= size; x++) {
                    int wanted = source.getRGB(start + Math.clamp(x, 0, size - 1), start + Math.clamp(y, 0, size - 1));
                    check(page.getRGB(region.left + x, region.top + y) == wanted, "Atlas must preserve exact RGBA interiors, edges and corners");
                }
            }
        }
        check(keys.equals(java.util.Set.of("__lute_tile/3:0", "__lute_tile/3:3", "__lute_tile/7:0")),
            "Use only placed external and inline tiles with nondefault source grids");
        check(snapshot(shared).equals(authoredSheet) && Files.readString(map).equals(authoredMap), "Do not modify authored tilesets or maps");
        var published = snapshot(output);
        TexturePackerTool.generate(raw, output);
        check(snapshot(output).equals(published), "Regeneration must not accumulate padding");
        Files.writeString(tsx, external.replace("columns=\"2\"", "columns=\"3\""));
        rejects(() -> TexturePackerTool.generate(raw, output));
        Files.writeString(tsx, external.replace("margin=\"1\"", "margin=\"9\""));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(published), "Invalid authored grid must not replace previously generated assets");
    }

    private static void terrainOverflow(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets"), output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        Path inputs = raw.resolve("maps/alpha");
        Path map = inputs.resolve("alpha.tmx");
        // The padded width of one region and height of the other consume an entire page axis.
        for (String name : new String[]{"wide", "tall"}) {
            var img = new BufferedImage(name.equals("wide") ? 2046 : 2, name.equals("tall") ? 2046 : 2, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(img, "png", inputs.resolve(name + ".png").toFile());
        }
        String original = """
            <map><tileset firstgid="1" name="wide" tilewidth="2046" tileheight="2"><image source="wide.png"/></tileset>
            <tileset firstgid="2" name="tall" tilewidth="2" tileheight="2046"><image source="tall.png"/></tileset>
            <layer width="2" height="1"><data encoding="csv">1,2</data></layer></map>
            """;
        Files.writeString(map, original);
        TexturePackerTool.generate(raw, output);
        var dir = new FileHandle(output.resolve("atlases").toFile());
        var atlas = new TextureAtlasData(dir.child("alpha.atlas"), dir, false);
        check(atlas.getPages().size == 3, "Both blank terrain tiles must survive on separate pages alongside the object page");
        for (var page : atlas.getPages()) {
            check(page.width <= 2048 && page.height <= 2048, "Terrain page budget must be enforced");
        }
        var published = snapshot(output);
        TexturePackerTool.generate(raw, output);
        check(snapshot(output).equals(published), "Multiple terrain pages and ownership inventory regenerate identically");
        Files.writeString(map, original.replace(">1,2<", ">1,3<"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(published), "Unresolvable sheet GID must not damage a prior generation");
        Files.writeString(map, original.replace(">1,2<", ">1,0<"));
        TexturePackerTool.generate(raw, output);
        check(!dir.child("alpha3.png").exists(), "Regenerating a smaller set removes the obsolete terrain page");
    }

    private static BufferedImage checkExtrudedSheet(Path definition, Element tileset, int columns, int rows,
            int width, int height) throws IOException {
        int margin = tileset.getIntAttribute("margin");
        int spacing = tileset.getIntAttribute("spacing");
        check(margin == 1 && spacing == 2, "Generated Tiled layout must describe the one-pixel borders");
        check(tileset.getIntAttribute("columns") == columns && tileset.getIntAttribute("tilecount") == columns * rows,
            "Normalize column and tile count metadata to the actual grid");
        var image = tileset.getChildByName("image");
        var padded = ImageIO.read(definition.resolveSibling(image.getAttribute("source")).toFile());
        check(image.getIntAttribute("width") == width && padded.getWidth() == width
            && image.getIntAttribute("height") == height && padded.getHeight() == height,
            "Image dimensions and relative path must match the generated PNG");
        int tileWidth = tileset.getIntAttribute("tilewidth");
        int tileHeight = tileset.getIntAttribute("tileheight");
        // Match libGDX's sheet loop, which stops at the image edge rather than using tilecount.
        check(1 + (width - tileWidth - margin) / (tileWidth + spacing) == columns
            && 1 + (height - tileHeight - margin) / (tileHeight + spacing) == rows,
            "Generated metadata must enumerate the same grid in libGDX");
        return padded;
    }

    private static void singleFrameArrows(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        Path arrows = raw.resolve("sprites/weapons/projectiles");
        Files.createDirectories(arrows);
        var image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 7, 0xff804020);
        for (String filename : new String[]{"arrow.png", "fire_arrow_00.png"}) {
            ImageIO.write(image, "png", arrows.resolve(filename).toFile());
        }
        var sources = snapshot(arrows);
        TexturePackerTool.generate(raw, output);
        var atlas = new TextureAtlasData(new FileHandle(output.resolve("atlases/weapons.atlas").toFile()),
            new FileHandle(output.resolve("atlases").toFile()), false);
        check(atlas.getRegions().size == 4, "Pack both single-frame arrows alongside sword and bow frames");
        check(hasRegion(atlas, "melee/basic_sword/idle_down", 0)
            && hasRegion(atlas, "ranged/basic_bow/idle_down", 0), "Arrow packing must retain existing weapon frames");
        check(hasRegion(atlas, "projectiles/arrow", -1), "Unindexed arrow must remain a static projectile region");
        check(hasRegion(atlas, "projectiles/fire_arrow", 0), "Single indexed projectile must retain frame zero");
        checkFolderIsolation(atlas);
        for (var region : atlas.getRegions()) {
            if (!region.name.startsWith("projectiles/")) { continue; }
            check(!region.rotate && region.width == 32 && region.height == 16
                && region.originalWidth == 32 && region.originalHeight == 16
                && region.offsetX == 0 && region.offsetY == 0, "Preserve single-frame arrow canvas and orientation");
            var page = ImageIO.read(region.page.textureFile.file());
            check(page.getRGB(region.left, region.top) == 0
                && page.getRGB(region.left + 5, region.top + 7) == image.getRGB(5, 7),
                "Preserve arrow pixels and transparent padding");
        }
        var generated = snapshot(output);
        TexturePackerTool.generate(raw, output);
        check(snapshot(output).equals(generated), "Arrow atlas generation must be deterministic");
        check(snapshot(arrows).equals(sources), "Packing must not modify arrow source files");
    }

    private static void requiredSpriteInputs(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        Path sprites = raw.resolve("sprites");
        Path held = directory.resolve("held-sprites");
        Files.move(sprites, held);
        try {
            for (boolean empty : new boolean[]{false, true}) {
                if (empty) { Files.createDirectory(sprites); }
                rejectsRequiredSprites(raw, output);
                check(!Files.exists(output), "Missing or empty top-level sprites must not publish a fresh output");
                check(!Files.exists(directory.resolve(".runtime-assets-transaction")), "Failed generation must clean staging");
                if (empty) { Files.delete(sprites); }
            }
        } finally {
            Files.move(held, sprites);
        }
        TexturePackerTool.generate(raw, output);
        Files.writeString(output.resolve("game.tiled-session"), "preserve session");
        Files.createDirectories(output.resolve("unowned/empty"));
        Files.writeString(output.resolve("unowned/notes"), "preserve unowned bytes");
        var complete = snapshot(output);
        var ownership = Files.readString(output.resolve(".runtime-assets-owned"));
        check(ownership.contains("\timages/menu/mainmenubg.jpg\n") && Files.readAllBytes(output.resolve("images/menu/mainmenubg.jpg"))[0] == 42,
            "Menu artwork is published unchanged and owned");
        for (String name : new String[]{"character", "enemies", "body", "head", "feet", "hands", "weapons"}) {
            check(ownership.contains("\tatlases/" + name + ".atlas\n"), "Complete baseline must own every sprite atlas");
            var atlas = new TextureAtlasData(new FileHandle(output.resolve("atlases/" + name + ".atlas").toFile()),
                new FileHandle(output.resolve("atlases").toFile()), false);
            int expected = switch (name) {
                case "character" -> 2;
                case "enemies", "body", "head", "feet", "hands" -> 1;
                default -> 2;
            };
            check(atlas.getRegions().size == expected, "Complete baseline must contain required frames");
            for (var page : atlas.getPages()) {
                check(page.textureFile.exists() && ownership.contains("\tatlases/" + page.textureFile.name() + "\n"),
                    "Complete baseline must own every published sprite page");
            }
        }
        String[] requiredDirectories = {"", "character", "character/player", "character/basic_npc",
            "enemies", "enemies/brainless_undead", "equipment", "equipment/basic", "equipment/basic/body",
            "equipment/basic/head", "equipment/basic/feet", "equipment/basic/hands", "weapons", "weapons/melee",
            "weapons/melee/basic_sword", "weapons/ranged", "weapons/ranged/basic_bow"};
        for (String input : requiredDirectories) {
            Path folder = sprites.resolve(input);
            Files.move(folder, held);
            try {
                for (boolean empty : new boolean[]{false, true}) {
                    if (empty) {
                        Files.createDirectory(folder);
                        Files.writeString(folder.resolve("README.txt"), "No sprite images supplied");
                    }
                    rejectsRequiredSprites(raw, output);
                    check(snapshot(output).equals(complete), "Invalid required input must preserve all output bytes and inventory: " + input);
                    check(Files.isDirectory(output.resolve("unowned/empty")), "Failed generation must preserve empty unowned directories");
                    check(!Files.exists(directory.resolve(".runtime-assets-transaction")), "Failed generation must clean staging");
                    if (empty) { deleteTree(folder); }
                }
            } finally {
                Files.move(held, folder);
            }
        }
        TexturePackerTool.generate(raw, output);
        check(snapshot(output).equals(complete), "Restoring valid inputs must reproduce the complete owned output and manifest");
    }

    private static void rejectsRequiredSprites(Path raw, Path output) throws IOException {
        try { TexturePackerTool.generate(raw, output); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().startsWith("Missing or empty required sprite input: " + raw.resolve("sprites")),
                "Failure must identify required sprite input, not an unrelated map or ownership error");
            return;
        }
        throw new AssertionError("Missing or empty required sprites must fail generation");
    }

    private static void sharedObjectsAtlas(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        createMap(raw, "beta");
        var source = raw.resolve("sprites/objects");
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 5, 0xffff8000);
        ImageIO.write(image, "png", source.resolve("sign.png").toFile());
        Files.delete(source.resolve("campfire1/idle_down_00.png"));
        for (int i = 0; i < 6; i++) {
            ImageIO.write(image, "png", source.resolve("campfire1/campfire1_0" + i + ".png").toFile());
        }
        TexturePackerTool.generate(raw, output);
        var atlases = new FileHandle(output.resolve("atlases").toFile());
        var objects = new TextureAtlasData(atlases.child("objects.atlas"), atlases, false);
        check(objects.getRegions().size == 7 && hasRegion(objects, "sign", -1), "Static object uses its unindexed key");
        for (int i = 0; i < 6; i++) {
            check(hasRegion(objects, "campfire1/campfire1", i), "Shared object atlas retains each indexed frame");
        }
        for (var frame : objects.getRegions()) {
            check(frame.width == 32 && frame.height == 32 && !frame.rotate, "Object art preserves its sprite canvas");
        }
        for (String map : new String[]{"alpha", "beta"}) {
            var owned = new TextureAtlasData(atlases.child(map + ".atlas"), atlases, false);
            check(hasRegion(owned, "house", -1) && !hasRegion(owned, "campfire1/campfire1", 0),
                "Map atlases retain local objects but never duplicate shared campfire frames");
        }
    }

    private static void spritePageDefaults(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        for (String input : REQUIRED_SPRITE_INPUTS) {
            Path folder = raw.resolve("sprites").resolve(input);
            Files.createDirectories(folder);
            var image = new BufferedImage(1100, 32, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(3, 4, 0xff336699);
            ImageIO.write(image, "png", folder.resolve("idle_down_00.png").toFile());
        }
        TexturePackerTool.generate(raw, output);
        checkDefaultSpritePages(output);
        // Partial packing overrides inherit the same page budget and sprite-preserving defaults.
        for (String input : new String[]{"character", "enemies", "equipment", "weapons"}) {
            Files.writeString(raw.resolve("sprites").resolve(input).resolve("pack.json"), "{\"paddingX\":4}");
        }
        TexturePackerTool.generate(raw, output);
        checkDefaultSpritePages(output);
    }

    private static void checkDefaultSpritePages(Path output) throws IOException {
        for (String name : new String[]{"character", "enemies", "body", "head", "feet", "hands", "weapons"}) {
            var atlas = new TextureAtlasData(new FileHandle(output.resolve("atlases/" + name + ".atlas").toFile()),
                new FileHandle(output.resolve("atlases").toFile()), false);
            int expectedRegions = switch (name) {
                case "character" -> 2;
                case "enemies", "body", "head", "feet", "hands" -> 1;
                default -> 2;
            };
            check(atlas.getRegions().size == expectedRegions, "Every wide source canvas must be packed");
            check(atlas.getPages().size == (name.equals("weapons") ? 2 : name.equals("character") ? 2 : 1),
                "Separate asset folders retain separate pages when each folder fits");
            checkFolderIsolation(atlas);
            for (var region : atlas.getRegions()) {
                check(region.width == 1100 && region.height == 32 && !region.rotate
                    && region.originalWidth == 1100 && region.originalHeight == 32,
                    "A canvas wider than 1024 must retain its original size and orientation");
            }
            for (var page : atlas.getPages()) {
                var image = ImageIO.read(page.textureFile.file());
                check(image.getWidth() == 2048 && image.getHeight() < 2048,
                    "Sprite pages allow 2048 width without forcing both dimensions to the maximum");
            }
        }
    }

    private static void spriteAtlases(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        String[] inputs = {"character/player", "character/basic_npc", "enemies/brainless_undead", "equipment/basic/body", "equipment/basic/head",
            "equipment/basic/feet", "equipment/basic/hands", "equipment/iron/body", "equipment/iron/head",
            "equipment/iron/feet", "equipment/iron/hands", "equipment/basic/accessories",
            "weapons/melee/basic_sword", "weapons/ranged/basic_bow"};
        for (String input : inputs) {
            Path folder = raw.resolve("sprites").resolve(input);
            Files.createDirectories(folder);
            for (int index = 0; index < 2; index++) {
                var image = new BufferedImage(12, 10, BufferedImage.TYPE_INT_ARGB);
                if (index == 0) { image.setRGB(3, 4, 0xff000000 | (input.hashCode() & 0x00ffffff)); }
                ImageIO.write(image, "png", folder.resolve("idle_down_0" + index + ".png").toFile());
            }
        }
        Path characterSettings = raw.resolve("sprites/character/pack.json");
        String settings = """
            {"rotation":false,"stripWhitespaceX":false,"stripWhitespaceY":false,
             "flattenPaths":false,"combineSubdirectories":false,"useIndexes":true,
             "alias":false,"ignoreBlankImages":false,"maxWidth":16,"maxHeight":16}
            """;
        Files.writeString(characterSettings, settings);
        Path accessory = raw.resolve("sprites/equipment/basic/accessories/idle_down_00.png");
        byte[] accessoryBefore = Files.readAllBytes(accessory);
        TexturePackerTool.generate(raw, output);
        check(java.util.Arrays.equals(Files.readAllBytes(accessory), accessoryBefore),
            "Skipping accessories must preserve the exported source bytes");
        check(!Files.exists(output.resolve("atlases/accessories.atlas")),
            "Unsupported accessories must not create a runtime atlas");
        for (String name : new String[]{"character", "enemies", "body", "head", "feet", "hands", "weapons"}) {
            var atlas = new TextureAtlasData(new FileHandle(output.resolve("atlases/" + name + ".atlas").toFile()),
                new FileHandle(output.resolve("atlases").toFile()), false);
            checkFolderIsolation(atlas);
            for (var region : atlas.getRegions()) {
                String sourceDirectory;
                if (name.equals("character")) {
                    check(region.name.startsWith("player/") || region.name.startsWith("basic_npc/"),
                        "Character regions retain actor prefixes");
                    sourceDirectory = "character/" + region.name.substring(0, region.name.indexOf('/'));
                } else if (name.equals("enemies")) {
                    check(region.name.startsWith("brainless_undead/"), "Enemy regions retain actor prefixes");
                    sourceDirectory = "enemies/" + region.name.substring(0, region.name.indexOf('/'));
                } else if (name.equals("weapons")) {
                    sourceDirectory = "weapons/" + region.name.substring(0, region.name.lastIndexOf('/'));
                } else {
                    sourceDirectory = "equipment/" + region.name.substring(0, region.name.indexOf('/')) + "/" + name;
                }
                check(region.index == 0 || region.index == 1, "Sprite indexes remain stable");
                check(!region.rotate && region.width == 12 && region.height == 10
                    && region.originalWidth == 12 && region.originalHeight == 10
                    && region.offsetX == 0 && region.offsetY == 0, "Preserve dimensions, padding and orientation");
                var pageImage = ImageIO.read(region.page.textureFile.file());
                check(pageImage.getRGB(region.left, region.top) == 0, "Retain transparent border pixels");
                var sourceImage = ImageIO.read(raw.resolve("sprites").resolve(sourceDirectory)
                    .resolve("idle_down_0" + region.index + ".png").toFile());
                check(pageImage.getRGB(region.left + 3, region.top + 4) == sourceImage.getRGB(3, 4),
                    "Retain each source folder's pixels and indexed frames");
            }
            for (var page : atlas.getPages()) { check(page.textureFile.exists(), "All atlas pages must be published"); }
            if (name.equals("character")) {
                check(hasRegion(atlas, "player/idle_down", 0) && hasRegion(atlas, "basic_npc/idle_down", 0),
                    "Player and NPC share one prefixed character atlas");
                check(atlas.getPages().size >= 2, "Character actor folders remain isolated across pages");
            }
            if (name.equals("enemies")) { check(hasRegion(atlas, "brainless_undead/idle_down", 0), "Enemy prefix is retained"); }
            if (name.equals("weapons")) {
                check(hasRegion(atlas, "melee/basic_sword/idle_down", 0)
                    && hasRegion(atlas, "ranged/basic_bow/idle_down", 1), "Melee and ranged share weapons atlas");
            }
            if (java.util.Set.of("body", "head", "feet", "hands").contains(name)) {
                check(hasRegion(atlas, "basic/idle_down", 0) && hasRegion(atlas, "iron/idle_down", 1),
                    "Equipment set prefixes remain stable");
            }
        }
        Path equipmentSettings = raw.resolve("sprites/equipment/pack.json");
        Files.writeString(equipmentSettings, settings);
        TexturePackerTool.generate(raw, output);
        check(Files.exists(output.resolve("atlases/head4.png")), "Gathered equipment must support multiple pages");
        Files.writeString(equipmentSettings, settings.replace("16", "64"));
        TexturePackerTool.generate(raw, output);
        check(Files.exists(output.resolve("atlases/head2.png")) && !Files.exists(output.resolve("atlases/head3.png")),
            "Remove stale pages while retaining one page per fitting equipment set");
        Files.delete(equipmentSettings);
        TexturePackerTool.generate(raw, output);
        var validEquipment = snapshot(output);
        Files.writeString(equipmentSettings, settings.replace("\"combineSubdirectories\":false", "\"combineSubdirectories\":true"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(validEquipment), "Reject sprite folder mixing without publishing partial output");
        Files.delete(equipmentSettings);
        Path unknownSlot = raw.resolve("sprites/equipment/basic/boots");
        Files.createDirectories(unknownSlot);
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(validEquipment), "Unknown slot must fail without publishing partial atlases");
        Files.delete(unknownSlot);
        Path nestedSettings = raw.resolve("sprites/equipment/basic/head/pack.json");
        Files.writeString(nestedSettings, "{}");
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(validEquipment), "Reject nested equipment packing overrides safely");
        Files.delete(nestedSettings);
        Path basicHead = raw.resolve("sprites/equipment/basic/head");
        Files.move(basicHead, raw.resolve("sprites/equipment/iron/missing-basic-head"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(validEquipment), "Missing required basic slot must preserve outputs");
        Files.move(raw.resolve("sprites/equipment/iron/missing-basic-head"), basicHead);
        check(!Files.exists(output.resolve("sprites")), "Runtime sprites must use only atlases");
        var initial = snapshot(output);
        createMap(raw, "character2");
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(initial), "Map first page versus sprite continuation page must fail safely");
        deleteTree(raw.resolve("maps/character2"));
        Files.writeString(characterSettings, settings.replace("\"rotation\":false", "\"rotation\":true"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(initial), "Invalid sprite packing must preserve outputs");
        Files.writeString(characterSettings, settings.replace("16", "64"));
        TexturePackerTool.generate(raw, output);
        check(Files.exists(output.resolve("atlases/character2.png"))
            && !Files.exists(output.resolve("atlases/character3.png")), "Remove stale sprite pages");
        var snapshot = snapshot(output);
        createMap(raw, "character");
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(snapshot), "Map versus sprite atlas collision must preserve all outputs");
        deleteTree(raw.resolve("maps/character"));
        Files.writeString(output.resolve("atlases/weapons.png"), "modified owned sprite");
        snapshot = snapshot(output);
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(snapshot), "Ownership failure must preserve every output byte");
        Path bow = raw.resolve("sprites/weapons/ranged/basic_bow");
        deleteTree(bow);
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(snapshot(output).equals(snapshot), "Missing required sprite input must preserve outputs");
    }

    private static java.util.Map<String, String> snapshot(Path directory) throws IOException {
        var result = new java.util.TreeMap<String, String>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                result.put(directory.relativize(file).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    private static void checkFolderIsolation(TextureAtlasData atlas) {
        for (var page : atlas.getPages()) {
            var folders = new java.util.HashSet<String>();
            for (var region : atlas.getRegions()) {
                if (region.page != page) { continue; }
                int slash = region.name.lastIndexOf('/');
                folders.add(slash == -1 ? "" : region.name.substring(0, slash));
            }
            check(folders.size() == 1, "A page must not mix frames from different asset folders");
        }
    }

    private static boolean hasRegion(TextureAtlasData atlas, String name, int index) {
        for (var region : atlas.getRegions()) {
            if (region.name.equals(name) && region.index == index) { return true; }
        }
        return false;
    }

    private static void ownershipAndStaleOutputs(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        createMap(raw, "beta");
        Path settings = raw.resolve("maps/alpha/objects/pack.json");
        Files.writeString(settings, Files.readString(settings).replace("\"alias\":false", "\"alias\":false, \"maxWidth\":16, \"maxHeight\":16"));
        TexturePackerTool.generate(raw, output);
        check(Files.exists(output.resolve("atlases/alpha2.png")), "Fixture must generate multiple atlas pages");
        Files.createDirectories(output.resolve("unowned/empty"));
        Files.writeString(output.resolve("unowned/notes"), "preserve me");
        Files.writeString(output.resolve("game.tiled-session"), "session bytes");
        Files.delete(raw.resolve("maps/alpha/objects/tree/tree_00.png"));
        Path tsx = raw.resolve("maps/alpha/objects/objects.tsx");
        Files.writeString(tsx, Files.readString(tsx).replace(
            "<tile id=\"1\"><image width=\"8\" height=\"8\" source=\"tree/tree_00.png\"/></tile>", ""));
        deleteTree(raw.resolve("maps/beta"));
        TexturePackerTool.generate(raw, output);
        check(!Files.exists(output.resolve("atlases/alpha2.png")), "Obsolete owned atlas pages must be removed");
        check(!Files.exists(output.resolve("atlases/beta.atlas")) && !Files.exists(output.resolve("maps/beta/beta.manifest.json")),
            "Removed maps must not leave owned runtime assets");
        check(Files.readString(output.resolve("unowned/notes")).equals("preserve me"), "Unowned nested files must survive");
        check(Files.isDirectory(output.resolve("unowned/empty")), "Unowned empty directories must survive");
        check(Files.readString(output.resolve("game.tiled-session")).equals("session bytes"), "Session must survive transactional publication");
        Path page = output.resolve("atlases/alpha.png");
        byte[] pageBytes = Files.readAllBytes(page);
        Files.writeString(page, "edited owned file");
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(Files.readString(page).equals("edited owned file"), "Modified owned outputs must never be overwritten");
        Files.write(page, pageBytes);
        Files.delete(output.resolve(".runtime-assets-owned"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(!Files.exists(output.resolve(".runtime-assets-owned")), "Existing unowned files must not be silently claimed");
        Files.writeString(page, "different unowned file");
        rejects(() -> TexturePackerTool.generate(raw, output, true));
        check(Files.readString(page).equals("different unowned file"), "Adoption must not overwrite differing unowned files");
        Files.write(page, pageBytes);
        TexturePackerTool.generate(raw, output, true);
        TexturePackerTool.generate(raw, output);
        Files.writeString(output.resolve(".runtime-assets-owned"), "0".repeat(64) + "\t../outside\n");
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(Files.readString(output.resolve("game.tiled-session")).equals("session bytes"), "Invalid ownership must leave output intact");
    }

    private static void atlasCollisions(Path directory) throws IOException {
        Path raw = directory.resolve("raw_assets");
        Path output = directory.resolve("assets");
        createInputs(raw);
        createMap(raw, "alpha");
        createMap(raw, "alpha2");
        Path settings = raw.resolve("maps/alpha/objects/pack.json");
        Files.writeString(settings, Files.readString(settings).replace("\"alias\":false", "\"alias\":false, \"maxWidth\":16, \"maxHeight\":16"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(!Files.exists(output), "Atlas page collisions between map names must fail before publication");
        deleteTree(raw.resolve("maps/alpha2"));
        Path reserved = Files.createDirectory(raw.resolve("maps/alpha/objects/__lute_tile"));
        Files.copy(raw.resolve("maps/alpha/objects/house.png"), reserved.resolve("3_0.png"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(!Files.exists(output), "Objects must not publish in the qualified terrain namespace");
        deleteTree(reserved);
        Files.copy(raw.resolve("maps/alpha/objects/tree/tree_00.png"), raw.resolve("maps/alpha/objects/tree/tree_0.png"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(!Files.exists(output), "Duplicate logical frame indexes must fail before publication");
    }

    private static void publicationRollback(Path directory) throws IOException {
        Path output = directory.resolve("assets");
        Path backup = directory.resolve("previous");
        Files.createDirectories(output);
        Files.writeString(output.resolve("game.tiled-session"), "unchanged");
        rejects(() -> TexturePackerTool.publish(directory.resolve("missing-prepared"), output, backup));
        check(Files.readString(output.resolve("game.tiled-session")).equals("unchanged"), "Failed second rename must restore the complete output");
        check(!Files.exists(backup), "Successful rollback must restore the original location");
        Path prepared = directory.resolve("prepared");
        Files.createDirectory(prepared);
        Files.writeString(prepared.resolve("new"), "new generation");
        Files.createDirectory(backup);
        Files.writeString(backup.resolve("unowned"), "never overwrite");
        rejects(() -> TexturePackerTool.publish(prepared, output, backup));
        check(Files.readString(backup.resolve("unowned")).equals("never overwrite"), "Backup collisions must never overwrite unowned data");
        check(Files.readString(output.resolve("game.tiled-session")).equals("unchanged"), "Failed first rename must leave original output intact");
    }

    private static void createInputs(Path raw) throws IOException {
        Files.createDirectories(raw.resolve("data"));
        Files.createDirectories(raw.resolve("audio"));
        Files.createDirectories(raw.resolve("images/menu"));
        Files.write(raw.resolve("images/menu/mainmenubg.jpg"), new byte[]{42});
        Files.writeString(raw.resolve("game.settings.json"), """
            {"mapsPath":"maps/", "atlasPath":"atlases/", "startupMap":"alpha", "projectPath":"world.tiled-project",
             "debugLayers":{"physics":true,"triggers":true,"rawLightMap":false,"lightingOutlines":false},
             "dataPath":"data/", "soundPath":"audio/", "musicPath":"audio/"}
            """);
        Files.writeString(raw.resolve("world.tiled-project"), "{}");
        for (String input : REQUIRED_SPRITE_INPUTS) {
            Path folder = raw.resolve("sprites").resolve(input);
            Files.createDirectories(folder);
            var image = new BufferedImage(12, 10, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(3, 4, 0xff336699);
            ImageIO.write(image, "png", folder.resolve("idle_down_00.png").toFile());
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
        }
    }

    private static void createMap(Path raw, String name) throws IOException {
        Path map = raw.resolve("maps").resolve(name);
        Files.createDirectories(map.resolve("objects/tree"));
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) { image.setRGB(x, y, 0xff336699); }
        }
        ImageIO.write(image, "png", map.resolve("objects/house.png").toFile());
        ImageIO.write(image, "png", map.resolve("objects/tree/tree_00.png").toFile());
        Path shared = raw.resolve("tilesets/shared");
        Files.createDirectories(shared);
        ImageIO.write(image, "png", shared.resolve("terrain.png").toFile());
        Files.writeString(shared.resolve("terrain.tsx"), """
            <tileset name="terrain" tilewidth="8" tileheight="8"><image source="terrain.png"/></tileset>
            """);
        Files.writeString(map.resolve("objects/pack.json"), """
            {"rotation":false, "stripWhitespaceX":false, "stripWhitespaceY":false,
             "flattenPaths":false, "combineSubdirectories":true, "useIndexes":true, "alias":false}
            """);
        Files.writeString(map.resolve(name + ".manifest.json"),
            "{\"map_asset\":\"" + name + "/" + name + ".tmx\",\"atlasAsset\":\"" + name + ".atlas\",\"music_assets\":{},\"sound_assets\":{}}");
        Files.writeString(map.resolve(name + ".tmx"), """
            <map><tileset firstgid="1" source="objects/objects.tsx"/>
              <tileset firstgid="3" source="../../tilesets/shared/terrain.tsx"/></map>
            """);
        Files.writeString(map.resolve("objects/objects.tsx"), """
            <tileset><tile id="0"><image width="8" height="8" source="house.png"/></tile>
              <tile id="1"><image width="8" height="8" source="tree/tree_00.png"/></tile></tileset>
            """);
    }

    private interface Action { void run() throws IOException; }
    private static void rejects(Action action) {
        try { action.run(); }
        catch (IOException | RuntimeException expected) { return; }
        throw new AssertionError("Invalid generation input must fail");
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
