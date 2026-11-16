package io.mindspice.lute.engine.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class TexturePackerTool {
    private static final Pattern INDEXED_IMAGE = Pattern.compile("(.+)_([0-9]+)$");
    private static final String OWNERSHIP = ".runtime-assets-owned";

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--adopt-identical"))) {
            throw new IllegalArgumentException("Usage: TexturePackerTool <raw_assets> <assets> [--adopt-identical]");
        }
        generate(Path.of(args[0]), Path.of(args[1]), args.length == 3);
    }

    static void generate(Path source, Path destination) throws IOException {
        generate(source, destination, false);
    }

    static void generate(Path source, Path destination, boolean adoptIdentical) throws IOException {
        Path raw = source.toAbsolutePath().normalize();
        Path output = destination.toAbsolutePath().normalize();
        if (!raw.getFileName().toString().equals("raw_assets") || !output.getFileName().toString().equals("assets")
                || !raw.getParent().equals(output.getParent())) {
            throw new IllegalArgumentException("Source and output must be sibling raw_assets and assets directories");
        }
        rejectSymlinks(raw);
        rejectSymlinks(output);
        if (!Files.isDirectory(raw)) { throw new IOException("Missing source directory: " + raw); }
        if (Files.exists(output) && !Files.isDirectory(output)) { throw new IOException("Output must be a directory: " + output); }
        Path transaction = raw.getParent().resolve(".runtime-assets-transaction");
        // Exclusive directory creation serializes generators. An interrupted run is left intact for recovery.
        if (Files.exists(transaction)) {
            throw new IOException("Generation already running or interrupted; inspect retained transaction: " + transaction);
        }
        Files.createDirectory(transaction);
        Path stage = transaction.resolve("generated");
        Path backup = transaction.resolve("previous");
        try {
            Files.createDirectory(stage);
            for (String file : List.of("game.settings.json", "game.tiled-project")) {
                copy(raw.resolve(file), stage.resolve(file));
            }
            for (String folder : List.of("data", "audio")) {
                copyTree(raw.resolve(folder), stage.resolve(folder));
            }
            var settings = new JsonReader().parse(new FileHandle(raw.resolve("game.settings.json").toFile()));
            if (!"maps/".equals(settings.getString("mapsPath")) || !"atlases/".equals(settings.getString("atlasPath"))
                    || !"game.tiled-project".equals(settings.getString("projectPath"))
                    || !"data/".equals(settings.getString("dataPath"))
                    || !"audio/".equals(settings.getString("soundPath"))
                    || !"audio/".equals(settings.getString("musicPath"))) {
                throw new IllegalArgumentException("Runtime generation requires the maps/, atlases/, data/, audio/ layout");
            }
            int maps = 0;
            try (var directories = Files.list(raw.resolve("maps"))) {
                for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                    if (!Files.isDirectory(directory.resolve("objects"))) { continue; }
                    generateMap(raw, directory, stage);
                    maps++;
                }
            }
            if (maps == 0) { throw new IllegalArgumentException("No map directories with an objects input folder in " + raw); }
            generateSprites(raw.resolve("sprites"), stage);
            Path prepared = transaction.resolve("prepared");
            preparePublication(stage, output, prepared, adoptIdentical);
            publish(prepared, output, backup);
            deleteTree(backup);
            System.out.println("Generated runtime assets for " + maps + " map(s) in " + output);
        } finally {
            // Never remove the only surviving copy if rollback or backup cleanup failed.
            if (!Files.exists(backup)) { deleteTree(transaction); }
        }
    }

    private static void preparePublication(Path generated, Path output, Path prepared, boolean adoptIdentical) throws IOException {
        Map<String, String> previous = readOwnership(output.resolve(OWNERSHIP));
        Map<String, String> next = inventory(generated);
        for (var entry : previous.entrySet()) {
            Path file = output.resolve(entry.getKey());
            if (Files.exists(file) && (!Files.isRegularFile(file) || !hash(file).equals(entry.getValue()))) {
                throw new IOException("Refusing to replace modified owned asset: " + file);
            }
        }
        for (String name : next.keySet()) {
            Path file = output.resolve(name);
            if (!Files.exists(file) || previous.containsKey(name)) { continue; }
            if (!adoptIdentical || !Files.isRegularFile(file) || !hash(file).equals(next.get(name))) {
                throw new IOException("Unowned output collision (use --adopt-identical only for matching migration files): " + file);
            }
            System.out.println("Adopting identical runtime asset: " + file);
        }
        Files.createDirectory(prepared);
        if (Files.exists(output)) { copyTree(output, prepared); }
        for (String name : previous.keySet()) {
            Files.deleteIfExists(prepared.resolve(name));
        }
        copyTree(generated, prepared);
        StringBuilder ownership = new StringBuilder();
        next.forEach((name, hash) -> ownership.append(hash).append('\t').append(name).append('\n'));
        Files.writeString(prepared.resolve(OWNERSHIP), ownership.toString());
    }

    static void publish(Path prepared, Path output, Path backup) throws IOException {
        if (Files.exists(backup)) { throw new IOException("Refusing to overwrite backup path: " + backup); }
        boolean existed = Files.exists(output);
        if (existed) { Files.move(output, backup, StandardCopyOption.ATOMIC_MOVE); }
        try {
            Files.move(prepared, output, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            if (existed) {
                try { Files.move(backup, output, StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }

    private static Map<String, String> readOwnership(Path file) throws IOException {
        Map<String, String> result = new TreeMap<>();
        if (!Files.exists(file)) { return result; }
        for (String line : Files.readAllLines(file)) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 2 || !fields[0].matches("[a-f0-9]{64}")) {
                throw new IOException("Invalid ownership manifest: " + file);
            }
            String name = fields[1];
            Path path = Path.of(name);
            if (path.isAbsolute() || !path.normalize().equals(path) || !path.toString().equals(name)
                    || path.startsWith("..") || name.isBlank()
                    || name.contains("\\") || name.contains("\r") || name.equals(OWNERSHIP) || name.equals("game.tiled-session")
                    || result.putIfAbsent(name, fields[0]) != null) {
                throw new IOException("Invalid owned asset path: " + name);
            }
        }
        return result;
    }

    private static Map<String, String> inventory(Path directory) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = directory.relativize(file).toString().replace('\\', '/');
                if (name.contains("\n") || name.contains("\r") || name.contains("\t")) {
                    throw new IOException("Unsupported asset filename: " + file);
                }
                result.put(name, hash(file));
            }
        }
        return result;
    }

    private static String hash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) { return; }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
        }
    }

    private static void generateMap(Path raw, Path directory, Path stage) throws IOException {
        String name = directory.getFileName().toString();
        Path objects = directory.resolve("objects");
        Path map = directory.resolve(name + ".tmx");
        Path manifest = directory.resolve(name + ".manifest.json");
        var data = new JsonReader().parse(new FileHandle(manifest.toFile()));
        String atlasAsset = data.getString("atlasAsset", data.getString("atlas_asset", ""));
        String mapAsset = data.getString("mapAsset", data.getString("map_asset", ""));
        if (!name.concat(".atlas").equals(atlasAsset) || !(name + "/" + name + ".tmx").equals(mapAsset)
                || !atlasAsset.equals(data.getString("atlas_asset", atlasAsset))
                || !mapAsset.equals(data.getString("map_asset", mapAsset))) {
            throw new IllegalArgumentException("Manifest must reference " + name + ".atlas and " + name + "/" + name + ".tmx");
        }
        for (String field : List.of("music_assets", "sound_assets")) {
            String camelCase = field.equals("music_assets") ? "musicAssets" : "soundAssets";
            var assets = data.has(field) ? data.get(field) : data.get(camelCase);
            if (assets == null || !assets.isObject()) {
                throw new IllegalArgumentException("Manifest requires " + field + " (an empty object is allowed): " + manifest);
            }
        }
        var packSettings = new Json().fromJson(TexturePacker.Settings.class, new FileHandle(objects.resolve("pack.json").toFile()));
        var atlas = packAtlas(objects, stage, name, packSettings);
        Path runtimeMap = stage.resolve("maps").resolve(name);
        var xml = new XmlReader();
        Element root = xml.parse(new FileHandle(map.toFile()));
        copy(map, runtimeMap.resolve(name + ".tmx"));
        copy(manifest, runtimeMap.resolve(name + ".manifest.json"));
        for (Element tileset : root.getChildrenByName("tileset")) {
            String source = tileset.getAttribute("source", null);
            Path definition = source == null ? map : mapDependency(raw, directory, map.getParent().resolve(source));
            Element element = source == null ? tileset : xml.parse(new FileHandle(definition.toFile()));
            if (source != null) { copy(definition, stage.resolve(raw.relativize(definition))); }
            Element sheet = element.getChildByName("image");
            if (sheet != null) {
                copyImage(raw, directory, stage, definition, sheet);
            } else {
                for (Element tile : element.getChildrenByName("tile")) {
                    Element image = tile.getChildByName("image");
                    if (image == null) { continue; }
                    Path input = inside(objects, definition.getParent().resolve(image.getAttribute("source")));
                    if (!Files.isRegularFile(input)) { throw new IOException("Missing object input image: " + input); }
                    String relative = objects.relativize(input).toString().replace('\\', '/');
                    // Loader resolves keys relative to the collection TSX, which lives at the objects root.
                    if (!definition.getParent().equals(objects)) {
                        throw new IllegalArgumentException("Image collection TSX must live directly in " + objects);
                    }
                    requireRegion(atlas, relative, image);
                }
            }
        }
        for (Element layer : root.getChildrenByNameRecursively("imagelayer")) {
            Element image = layer.getChildByName("image");
            if (image != null) { copyImage(raw, directory, stage, map, image); }
        }
    }

    private static TextureAtlasData packAtlas(Path objects, Path stage, String name,
                                               TexturePacker.Settings packSettings) throws IOException {
        if (packSettings.rotation || packSettings.stripWhitespaceX || packSettings.stripWhitespaceY
                || packSettings.flattenPaths || !packSettings.useIndexes || !packSettings.combineSubdirectories
                || packSettings.scale.length != 1 || packSettings.scale[0] != 1
                || packSettings.scaleSuffix.length != 1 || !packSettings.scaleSuffix[0].isEmpty()) {
            throw new IllegalArgumentException("Object packing must preserve paths, indexes, dimensions and orientation: " + objects);
        }
        try (var files = Files.walk(objects)) {
            if (files.anyMatch(file -> file.getFileName().toString().equals("pack.json") && !file.getParent().equals(objects))) {
                throw new IllegalArgumentException("Nested pack.json overrides are not supported: " + objects);
            }
        }
        Path packed = Files.createTempDirectory(stage.getParent(), "packed-");
        TexturePacker.process(packSettings, objects.toString(), packed.toString(), name);
        var atlas = new TextureAtlasData(new FileHandle(packed.resolve(name + ".atlas").toFile()),
            new FileHandle(packed.toFile()), false);
        var keys = new HashSet<String>();
        for (var region : atlas.getRegions()) {
            if (!keys.add(region.name + "\t" + region.index)) {
                throw new IllegalArgumentException("Duplicate packed region: " + region.name + " [" + region.index + "]");
            }
        }
        Path atlasDirectory = stage.resolve("atlases");
        Files.createDirectories(atlasDirectory);
        try (var files = Files.list(packed)) {
            for (Path file : files.sorted().toList()) {
                // Atlas page names (e.g. alpha2.png) can collide with another map's first page.
                Files.copy(file, atlasDirectory.resolve(file.getFileName()));
            }
        }
        return atlas;
    }

    private static void generateSprites(Path sprites, Path stage) throws IOException {
        if (!Files.exists(sprites)) { return; }
        for (String required : List.of("character", "body/basic", "head/basic", "feet/basic", "hands/basic",
                "weapons/melee/basic_sword", "weapons/ranged/basic_bow")) {
            Path directory = sprites.resolve(required);
            if (!Files.isDirectory(directory) || spriteImages(directory).isEmpty()) {
                throw new IllegalArgumentException("Missing or empty required sprite input: " + directory);
            }
        }
        for (String name : List.of("character", "body", "head", "feet", "hands", "weapons")) {
            Path input = sprites.resolve(name);
            var settings = new TexturePacker.Settings();
            settings.rotation = false;
            settings.stripWhitespaceX = false;
            settings.stripWhitespaceY = false;
            settings.flattenPaths = false;
            settings.combineSubdirectories = true;
            settings.useIndexes = true;
            settings.alias = false;
            settings.ignoreBlankImages = false;
            Path config = input.resolve("pack.json");
            if (Files.exists(config)) {
                settings = new Json().fromJson(TexturePacker.Settings.class, new FileHandle(config.toFile()));
            }
            if (settings.ignoreBlankImages) {
                throw new IllegalArgumentException("Sprite packing must retain transparent frames: " + input);
            }
            var atlas = packAtlas(input, stage, name, settings);
            for (Path file : spriteImages(input)) {
                var image = ImageIO.read(file.toFile());
                if (image == null) { throw new IOException("Invalid sprite image: " + file); }
                String relative = input.relativize(file).toString().replace('\\', '/');
                if (name.equals("character") && relative.contains("/")) {
                    throw new IllegalArgumentException("Character frames must be flat: " + file);
                }
                requireRegion(atlas, relative, image.getWidth(), image.getHeight());
            }
        }
    }

    private static List<Path> spriteImages(Path input) throws IOException {
        try (var files = Files.walk(input)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".png")).sorted().toList();
        }
    }

    private static void requireRegion(TextureAtlasData atlas, String source, Element image) {
        requireRegion(atlas, source, image.getIntAttribute("width"), image.getIntAttribute("height"));
    }

    private static void requireRegion(TextureAtlasData atlas, String source, int width, int height) {
        int extension = source.lastIndexOf('.');
        if (extension < 0) { throw new IllegalArgumentException("Object image lacks extension: " + source); }
        String key = source.substring(0, extension);
        var indexed = INDEXED_IMAGE.matcher(key);
        int index = -1;
        if (indexed.matches()) {
            key = indexed.group(1);
            index = Integer.parseInt(indexed.group(2));
        }
        for (var region : atlas.getRegions()) {
            if (!region.name.equals(key) || region.index != index) { continue; }
            if (region.rotate || region.width != region.originalWidth || region.height != region.originalHeight
                    || region.width != width || region.height != height || region.offsetX != 0 || region.offsetY != 0) {
                throw new IllegalArgumentException("Packed region must preserve authored image dimensions: " + source);
            }
            return;
        }
        throw new IllegalArgumentException("Missing packed region for " + source);
    }

    private static void copyImage(Path raw, Path mapDirectory, Path stage, Path definition, Element image) throws IOException {
        Path source = mapDependency(raw, mapDirectory, definition.getParent().resolve(image.getAttribute("source")));
        copy(source, stage.resolve(raw.relativize(source)));
    }

    private static Path mapDependency(Path raw, Path mapDirectory, Path candidate) {
        Path path = inside(raw, candidate);
        if (!path.startsWith(mapDirectory) && !path.startsWith(raw.resolve("tilesets"))) {
            throw new IllegalArgumentException("Map dependency must be in its map or shared tilesets: " + path);
        }
        return path;
    }

    private static Path inside(Path root, Path candidate) {
        Path path = candidate.normalize();
        if (!path.startsWith(root)) { throw new IllegalArgumentException("Asset path escapes " + root + ": " + path); }
        return path;
    }

    private static void copyTree(Path source, Path output) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path target = output.resolve(source.relativize(path));
                if (Files.isDirectory(path)) { Files.createDirectories(target); }
                else { copy(path, target); }
            }
        }
    }

    private static void copy(Path source, Path output) throws IOException {
        rejectSymlinks(output);
        Files.createDirectories(output.getParent());
        Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void rejectSymlinks(Path root) throws IOException {
        for (Path path = root; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) { throw new IOException("Refusing symlink asset path: " + path); }
        }
        if (Files.isDirectory(root)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.toList()) {
                    if (Files.isSymbolicLink(path)) { throw new IOException("Refusing symlink asset path: " + path); }
                    if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
                        throw new IOException("Refusing special asset path: " + path);
                    }
                }
            }
        }
    }
}
