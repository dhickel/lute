package io.mindspice.lute.engine.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TexturePackerToolTest {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("lute-runtime-generation-");
        try {
            Path raw = directory.resolve("raw_assets");
            Path output = directory.resolve("assets");
            Files.createDirectories(raw.resolve("data"));
            Files.createDirectories(raw.resolve("audio"));
            Files.writeString(raw.resolve("game.settings.json"), """
                {"mapsPath":"maps/", "atlasPath":"atlases/", "projectPath":"game.tiled-project",
                 "dataPath":"data/", "soundPath":"audio/", "musicPath":"audio/"}
                """);
            Files.writeString(raw.resolve("game.tiled-project"), "{}");
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
                check(Files.exists(output.resolve("maps/" + name + "/terrain.png")), "Retain terrain images");
                check(!Files.exists(output.resolve("maps/" + name + "/objects/house.png")), "Never publish object source PNGs");
                check(Files.exists(raw.resolve(name + "/objects/house.png")), "Generation must retain editable source");
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
            Files.delete(raw.resolve("beta/objects/house.png"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            check(Files.readString(output.resolve("atlases/alpha.atlas")).equals(before), "Failed generation must not publish earlier maps");
            check(Files.readString(sentinel).equals("do not touch"), "Failure must not remove unrelated files");
            createMap(raw, "beta");
            Files.writeString(raw.resolve("beta/objects/pack.json"), "{\"rotation\":true}");
            rejects(() -> TexturePackerTool.generate(raw, output));
            createMap(raw, "beta");
            Files.writeString(raw.resolve("beta/objects/tree/pack.json"), "{\"rotation\":true}");
            rejects(() -> TexturePackerTool.generate(raw, output));
            Files.delete(raw.resolve("beta/objects/tree/pack.json"));
            Path manifest = raw.resolve("beta/beta.manifest.json");
            Files.writeString(manifest, Files.readString(manifest).replace("\"sound_assets\":{}", "\"sound_assets\":null"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            createMap(raw, "beta");
            Files.writeString(manifest, Files.readString(manifest).replace("\"atlasAsset\":", "\"atlas_asset\":\"wrong.atlas\",\"atlasAsset\":"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            createMap(raw, "beta");
            Path map = raw.resolve("beta/beta.tmx");
            Files.writeString(map, Files.readString(map).replace("terrain.png", "../outside.png"));
            rejects(() -> TexturePackerTool.generate(raw, output));
            try (var paths = Files.list(directory)) {
                check(paths.noneMatch(path -> path.getFileName().toString().startsWith(".runtime-assets-")),
                    "Temporary output must be cleaned after success and failure");
            }
            ownershipAndStaleOutputs(directory.resolve("ownership"));
            atlasCollisions(directory.resolve("collisions"));
            publicationRollback(directory.resolve("publication"));
            System.out.println("TexturePackerToolTest: passed (names, two maps, ownership, stale outputs, collisions, rollback, path and symlink safety)");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
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
        Path settings = raw.resolve("alpha/objects/pack.json");
        Files.writeString(settings, Files.readString(settings).replace("\"alias\":false", "\"alias\":false, \"maxWidth\":16, \"maxHeight\":16"));
        TexturePackerTool.generate(raw, output);
        check(Files.exists(output.resolve("atlases/alpha2.png")), "Fixture must generate multiple atlas pages");
        Files.createDirectories(output.resolve("unowned/empty"));
        Files.writeString(output.resolve("unowned/notes"), "preserve me");
        Files.writeString(output.resolve("game.tiled-session"), "session bytes");
        Files.delete(raw.resolve("alpha/objects/tree/tree_00.png"));
        Path tsx = raw.resolve("alpha/objects/objects.tsx");
        Files.writeString(tsx, Files.readString(tsx).replace(
            "<tile id=\"1\"><image width=\"8\" height=\"8\" source=\"tree/tree_00.png\"/></tile>", ""));
        deleteTree(raw.resolve("beta"));
        TexturePackerTool.generate(raw, output);
        check(!Files.exists(output.resolve("atlases/alpha2.png")), "Obsolete owned atlas pages must be removed");
        check(!Files.exists(output.resolve("atlases/beta.atlas")) && !Files.exists(output.resolve("maps/beta.manifest.json")),
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
        Path settings = raw.resolve("alpha/objects/pack.json");
        Files.writeString(settings, Files.readString(settings).replace("\"alias\":false", "\"alias\":false, \"maxWidth\":16, \"maxHeight\":16"));
        rejects(() -> TexturePackerTool.generate(raw, output));
        check(!Files.exists(output), "Atlas page collisions between map names must fail before publication");
        deleteTree(raw.resolve("alpha2"));
        Files.copy(raw.resolve("alpha/objects/tree/tree_00.png"), raw.resolve("alpha/objects/tree/tree_0.png"));
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
        Files.writeString(raw.resolve("game.settings.json"), """
            {"mapsPath":"maps/", "atlasPath":"atlases/", "projectPath":"game.tiled-project",
             "dataPath":"data/", "soundPath":"audio/", "musicPath":"audio/"}
            """);
        Files.writeString(raw.resolve("game.tiled-project"), "{}");
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
        }
    }

    private static void createMap(Path raw, String name) throws IOException {
        Path map = raw.resolve(name);
        Files.createDirectories(map.resolve("objects/tree"));
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) { image.setRGB(x, y, 0xff336699); }
        }
        ImageIO.write(image, "png", map.resolve("objects/house.png").toFile());
        ImageIO.write(image, "png", map.resolve("objects/tree/tree_00.png").toFile());
        ImageIO.write(image, "png", map.resolve("terrain.png").toFile());
        Files.writeString(map.resolve("objects/pack.json"), """
            {"rotation":false, "stripWhitespaceX":false, "stripWhitespaceY":false,
             "flattenPaths":false, "combineSubdirectories":true, "useIndexes":true, "alias":false}
            """);
        Files.writeString(map.resolve(name + ".manifest.json"),
            "{\"map_asset\":\"" + name + "/" + name + ".tmx\",\"atlasAsset\":\"" + name + ".atlas\",\"music_assets\":{},\"sound_assets\":{}}");
        Files.writeString(map.resolve(name + ".tmx"), """
            <map><tileset firstgid="1" source="objects/objects.tsx"/>
              <tileset firstgid="3" name="terrain"><image source="terrain.png"/></tileset></map>
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
