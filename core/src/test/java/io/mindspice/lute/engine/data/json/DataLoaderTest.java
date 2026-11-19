package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.*;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DataLoaderTest {
    private static final Offset ZERO = new Offset(0, 0);
    public static void main(String[] args) throws Exception {
        var previousFiles = Gdx.files;
        try {
            useAssets();
            GameConsts.loadSettings();
            check(GameConsts.VIEW_WIDTH == 21.3f && GameConsts.UNIT_SCALE == 1f / 32f, "settings constants");
            var player = DataLoader.parsePlayerData();
            var definitions = DataLoader.parseWeaponData();
            playerSchema(player, definitions);
            characterBoundsSchema(player);
            weaponSchema(definitions);
            projectileSchema();
            missingProjectileData();
            invalidMetadata(player, definitions);
            assetIds(definitions);
            settingsAtomicity();
            check(Files.mismatch(Path.of("raw_assets/data/player.json"), Path.of("assets/data/player.json")) == -1,
                "generated player matches source");
            check(Files.mismatch(Path.of("raw_assets/data/weapons.json"), Path.of("assets/data/weapons.json")) == -1,
                "generated weapons match source");
            check(Files.mismatch(Path.of("raw_assets/data/projectiles.json"), Path.of("assets/data/projectiles.json")) == -1,
                "generated projectiles match source");
            System.out.println("DataLoaderTest: passed");
        } finally { Gdx.files = previousFiles; }
    }

    private static void projectileSchema() {
        var arrow = DataLoader.loadGameData().projectilesData().ironArrow();
        rejects(() -> DataLoader.mapper.readValue("{\"projectiles\":{}}", ProjectilesData.class),
            "missing iron arrow definition");
        check(arrow.atlasKey().equals("projectiles/IronArrow") && arrow.speed() == 8 && arrow.lifetime() == 5,
            "iron arrow binds the atlas region and flight settings");
        check(arrow.collisionBounds().equals(new ProjectilesData.CollisionBounds(8.5f, 10)),
            "iron arrow binds the authored tip dimensions");
        for (String key : new String[]{"atlas_key", "speed", "lifetime", "collision_bounds"}) {
            rejects(() -> {
                var tree = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.valueToTree(arrow);
                tree.remove(key);
                DataLoader.mapper.treeToValue(tree, ProjectilesData.Projectile.class);
            }, "missing projectile " + key);
        }
        rejects(() -> DataLoader.mapper.readValue("{\"length\":8,\"width\":0}",
            ProjectilesData.CollisionBounds.class), "zero projectile width");
    }

    private static void missingProjectileData() throws Exception {
        var emptyDirectory = Files.createTempDirectory("missing-projectile-data");
        String projectilePath = GameConsts.DATA_PATH + "projectiles.json";
        try {
            useResolver(path -> new FileHandle(path.equals(projectilePath)
                ? emptyDirectory.resolve("projectiles.json").toFile() : Path.of("assets", path).toFile()));
            rejects(DataLoader::loadGameData, "missing runtime projectile file without a bundled fallback");
        } finally {
            useAssets();
            Files.delete(emptyDirectory);
        }
    }

    private static void playerSchema(PlayerData player, WeaponsData definitions) {
        check(player.maxHealth() == 100 && player.moveSpeed() == 4, "player stats");
        check(player.animation().frameInterval() == .125f && player.animation().speed() == 1
            && player.animation().atlasKey().isEmpty() && player.animation().soundFrame() == 0, "flat base animation");
        check(player.logicalSize().width() == 64 && player.logicalSize().height() == 64, "logical character size");
        check(player.collisionBounds().equals(new CharacterBounds(20, 4, 44, 14)), "authored feet ellipse");
        check(player.hitboxBounds().equals(new CharacterBounds(16, 6, 48, 56)), "authored standing hurtbox");
        for (String key : new String[]{"logical_size", "collision_bounds", "hitbox_bounds"}) {
            rejects(() -> {
                var tree = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.valueToTree(player);
                tree.remove(key);
                DataLoader.mapper.treeToValue(tree, PlayerData.class);
            }, "missing " + key);
        }
        for (float value : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            rejects(() -> new PlayerData.LogicalSize(value, 64), "invalid logical width");
            rejects(() -> new PlayerData.LogicalSize(64, value), "invalid logical height");
        }
        rejects(() -> new CharacterBounds(1, 1, 1, 4), "empty ellipse");
        rejects(() -> new CharacterBounds(1, 1, 4, Float.NaN), "nonfinite ellipse");
        rejects(() -> new CharacterBounds(-1, 1, 4, 4), "negative ellipse origin");
        var loadout = player.loadout();
        check(loadout.head().equals("basic") && loadout.body().equals("basic")
            && loadout.hands().equals("basic") && loadout.feet().equals("basic")
            && loadout.weapon() != null && definitions.weapons().containsKey(loadout.weapon()), "default loadout");
        check(player.sounds().move().equals("sword_hit.wav") && player.sounds().damage().equals("sword_hit.wav"),
            "player sound definitions");
        var unequipped = new PlayerData.Loadout(null, null, null, null, null);
        for (String json : new String[]{"{}", "{\"head\":null,\"body\":null,\"hands\":null,\"feet\":null,\"weapon\":null}"}) {
            check(DataLoader.mapper.readValue(json, PlayerData.Loadout.class).equals(unequipped),
                "omitted and null IDs both leave slots empty");
        }
        check(DataLoader.mapper.readValue("{\"head\":\"basic\",\"body\":\"basic\",\"hands\":\"basic\",\"weapon\":\"basic_sword\"}",
            PlayerData.Loadout.class).equals(new PlayerData.Loadout("basic", "basic", "basic", null, "basic_sword")),
            "omitted feet leave only the feet slot empty");
        check(DataLoader.mapper.readValue("{\"head\":\"basic\",\"body\":\"basic\",\"hands\":\"basic\",\"feet\":\"basic\"}",
            PlayerData.Loadout.class).equals(new PlayerData.Loadout("basic", "basic", "basic", "basic", null)),
            "omitted weapon leaves only the weapon slot empty");
        for (String slot : new String[]{"head", "body", "hands", "feet", "weapon"}) {
            rejects(() -> DataLoader.mapper.readValue("{\"" + slot + "\":\" \"}", PlayerData.Loadout.class), "blank " + slot);
        }
        rejects(() -> new PlayerData("player", 0, 100, loadout, player.animation(), player.sounds(), player.logicalSize(), player.collisionBounds(), player.hitboxBounds()), "zero move speed");
        rejects(() -> new PlayerData("player", Float.NaN, 100, loadout, player.animation(), player.sounds(), player.logicalSize(), player.collisionBounds(), player.hitboxBounds()), "nonfinite move speed");
        rejects(() -> new PlayerData("player", 1, 0, loadout, player.animation(), player.sounds(), player.logicalSize(), player.collisionBounds(), player.hitboxBounds()), "zero health");
        rejects(() -> new PlayerData("player", 1, 100, null, player.animation(), player.sounds(), player.logicalSize(), player.collisionBounds(), player.hitboxBounds()), "missing loadout");
        rejects(() -> new PlayerData.Sounds("hit", null), "missing damage sound");
        rejects(() -> new PlayerData.Sounds("", "hit"), "empty move sound");
    }

    private static void characterBoundsSchema(PlayerData player) {
        for (String section : new String[]{"logical_size", "collision_bounds", "hitbox_bounds"}) {
            String[] members = section.equals("logical_size") ? new String[]{"width", "height"}
                : new String[]{"x1", "y1", "x2", "y2"};
            for (String member : members) {
                rejects(() -> {
                    var tree = DataLoader.mapper.valueToTree(player);
                    ((tools.jackson.databind.node.ObjectNode) tree.get(section)).remove(member);
                    DataLoader.mapper.treeToValue(tree, PlayerData.class);
                }, "JSON missing " + section + "." + member);
                for (String invalid : new String[]{"null", "-1", "1e50", "{}"}) {
                    rejects(() -> {
                        var tree = DataLoader.mapper.valueToTree(player);
                        ((tools.jackson.databind.node.ObjectNode) tree.get(section))
                            .set(member, DataLoader.mapper.readTree(invalid));
                        DataLoader.mapper.treeToValue(tree, PlayerData.class);
                    }, "JSON invalid " + section + "." + member + "=" + invalid);
                }
            }
            rejects(() -> {
                var tree = DataLoader.mapper.valueToTree(player);
                ((tools.jackson.databind.node.ObjectNode) tree).putNull(section);
                DataLoader.mapper.treeToValue(tree, PlayerData.class);
            }, "JSON null " + section);
        }
        for (String section : new String[]{"collision_bounds", "hitbox_bounds"}) {
            for (String invalid : new String[]{"{\"x1\":20,\"y1\":4,\"x2\":20,\"y2\":14}",
                "{\"x1\":20,\"y1\":14,\"x2\":44,\"y2\":4}",
                "{\"x1\":20,\"y1\":4,\"x2\":65,\"y2\":14}",
                "{\"x1\":20,\"y1\":4,\"x2\":44,\"y2\":65}"}) {
                rejects(() -> {
                    var tree = DataLoader.mapper.valueToTree(player);
                    ((tools.jackson.databind.node.ObjectNode) tree).set(section, DataLoader.mapper.readTree(invalid));
                    DataLoader.mapper.treeToValue(tree, PlayerData.class);
                }, "JSON invalid logical-local " + section);
            }
        }
        for (String size : new String[]{"{\"width\":0,\"height\":64}", "{\"width\":64,\"height\":0}",
            "{\"width\":40,\"height\":64}", "{\"width\":64,\"height\":48}"}) {
            rejects(() -> {
                var tree = DataLoader.mapper.valueToTree(player);
                ((tools.jackson.databind.node.ObjectNode) tree).set("logical_size", DataLoader.mapper.readTree(size));
                DataLoader.mapper.treeToValue(tree, PlayerData.class);
            }, "JSON invalid logical size or bounds outside it");
        }
        var tree = DataLoader.mapper.valueToTree(player);
        ((tools.jackson.databind.node.ObjectNode) tree).set("logical_size",
            DataLoader.mapper.readTree("{\"width\":80,\"height\":72}"));
        var larger = DataLoader.mapper.treeToValue(tree, PlayerData.class);
        check(larger.logicalSize().width() == 80 && larger.logicalSize().height() == 72,
            "logical size is configured independently, not hard-coded to 64");
    }

    private static void weaponSchema(WeaponsData definitions) throws Exception {
        check(definitions.atlas().equals("weapons.atlas") && definitions.weapons().size() == 3, "shared catalog");
        var sword = definitions.requireWeapon("basic_sword");
        var bow = definitions.requireWeapon("basic_bow");
        var unarmed = definitions.requireWeapon("unarmed");
        check(sword.atlasKey().equals("melee/basic_sword") && bow.atlasKey().equals("ranged/basic_bow")
            && unarmed.atlasKey().equals("NONE"), "weapon layer prefixes");
        check(sword.attackStats().primary().animation().atlasKey().equals("slash"), "sword primary slash");
        check(sword.attackStats().primary().stats().damage() == 10, "preserved slash damage");
        check(bow.attackStats().primary().animation().atlasKey().equals("shot")
            && bow.attackStats().primary().collisionType() == CollisionType.PROJECTILE, "bow primary projectile metadata");
        var authored = DataLoader.mapper.readTree(Files.readString(Path.of("assets/data/weapons.json"))).get("weapons");
        for (var entry : definitions.weapons().entrySet()) {
            var weapon = entry.getValue();
            check(weapon.attackStats().secondary().animation().atlasKey().equals("jab")
                && weapon.attackStats().secondary().collisionType() == CollisionType.MELEE, "every secondary is a melee jab");
            for (var slot : new String[]{"primary", "secondary"}) {
                var attack = slot.equals("primary") ? weapon.attackStats().primary() : weapon.attackStats().secondary();
                var json = authored.get(entry.getKey()).get("attack_stats").get(slot);
                check(attack.stats().damage() == json.get("stats").get("damage").asInt()
                    && attack.stats().cost() == json.get("stats").get("cost").asInt()
                    && attack.stats().hitFrame() == json.get("stats").get("hit_frame").asInt()
                    && attack.stats().cooldownSeconds() == (float) json.get("stats").get("cooldown_seconds").asDouble(),
                    "authored " + slot + " stats");
                check(attack.animation().speed() == (float) json.get("animation").get("speed").asDouble()
                    && attack.animation().soundFrame() == json.get("animation").get("sound_frame").asInt()
                    && attack.animation().frameInterval() == (float) json.get("animation").get("frame_interval").asDouble(),
                    "authored " + slot + " animation timing");
            }
        }
        check(unarmed.attackStats().primary().animation().atlasKey().equals("jab")
            && unarmed.attackStats().primary().collisionType() == CollisionType.MELEE, "unarmed primary jab");
        check(sword.attackStats().secondary().stats().damage() < sword.attackStats().primary().stats().damage()
            && sword.attackStats().secondary().stats().hitFrame() != sword.attackStats().primary().stats().hitFrame(),
            "jab has distinct lower tuning");
        var stats = DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2,\"cooldown_seconds\":0.375}", Stats.class);
        check(stats.cooldownSeconds() == .375f, "cooldown in seconds");
        var animation = DataLoader.mapper.readValue("{\"speed\":1,\"atlas_key\":\"jab\",\"sound_frame\":3,\"frame_interval\":0.15}", AnimationData.class);
        check(stats.hitFrame() == 2 && animation.soundFrame() == 3, "zero-based JSON cue frames");
    }

    private static void invalidMetadata(PlayerData player, WeaponsData definitions) {
        var sword = definitions.requireWeapon("basic_sword");
        var primary = sword.attackStats().primary();
        rejects(() -> definitions.requireWeapon("missing"), "unknown weapon");
        rejects(() -> new WeaponsData(null, definitions.weapons()), "missing shared atlas");
        rejects(() -> new WeaponsData("NONE", definitions.weapons()), "disabled shared atlas");
        rejects(() -> new WeaponsData("weapons.atlas", Map.of("basic_sword", sword)), "missing unarmed definition");
        var bad = new HashMap<>(definitions.weapons());
        bad.put("", sword);
        rejects(() -> new WeaponsData("weapons.atlas", bad), "empty weapon ID");
        var disabled = new HashMap<>(definitions.weapons());
        disabled.put("basic_sword", new WeaponData(sword.attackStats(), "NONE"));
        rejects(() -> new WeaponsData("weapons.atlas", disabled), "armed weapon without visual prefix");
        rejects(() -> new WeaponData(null, sword.atlasKey()), "missing attack stats");
        rejects(() -> new WeaponData(sword.attackStats(), " "), "blank weapon prefix");
        rejects(() -> new AttackStats(null, primary), "missing primary");
        rejects(() -> new AttackStats(primary, null), "missing secondary");
        rejects(() -> new AttackData(primary.animation(), primary.bounds(), primary.stats(), primary.sounds(), null), "missing collision type");
        rejects(() -> new AttackData(new AnimationData(1, "melee/basic_sword/slash", 0, .125f),
            primary.bounds(), primary.stats(), primary.sounds(), CollisionType.MELEE), "prefixed animation action");
        rejects(() -> new AttackData(new AnimationData(1, "shot", 0, .125f),
            primary.bounds(), primary.stats(), primary.sounds(), CollisionType.MELEE), "shot marked melee");
        rejects(() -> new AttackData(primary.animation(), primary.bounds(), primary.stats(), primary.sounds(), CollisionType.PROJECTILE),
            "slash marked projectile");
        rejects(() -> new Stats(10, 0, -1, .25f), "negative hit frame");
        rejects(() -> new Stats(0, 0, 0, .25f), "zero damage");
        rejects(() -> new Stats(10, -1, 0, .25f), "negative cost");
        for (float invalid : new float[]{0f, -.25f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            rejects(() -> new Stats(10, 0, 0, invalid), "invalid cooldown: " + invalid);
        }
        rejects(() -> new AnimationData(0, "jab", 0, .125f), "zero speed");
        rejects(() -> new AnimationData(1, "jab", -1, .125f), "negative sound frame");
        rejects(() -> new AnimationData(1, "", 0, 0), "zero base interval");
        rejects(() -> new AnimationData(1, "jab", 0, Float.NaN), "nonfinite interval");
        rejects(() -> new Sounds("", "hit.wav"), "empty attack sound");
        for (String invalid : new String[]{" ", "../sword_hit.wav", "/sword_hit.wav", "audio//hit.wav"}) {
            rejects(() -> new Sounds(invalid, "sword_hit.wav"), "invalid attack sound path");
            rejects(() -> new Sounds("sword_swing.wav", invalid), "invalid hit sound path");
        }
        for (String field : new String[]{"attack", "hit"}) {
            for (String invalid : new String[]{"null", "\" \"", "{}", "\"../bad.wav\""}) {
                rejects(() -> {
                    var tree = DataLoader.mapper.valueToTree(definitions);
                    var sounds = (tools.jackson.databind.node.ObjectNode)
                        tree.at("/weapons/basic_sword/attack_stats/secondary/sounds");
                    sounds.set(field, DataLoader.mapper.readTree(invalid));
                    DataLoader.mapper.treeToValue(tree, WeaponsData.class);
                }, "invalid secondary " + field);
            }
        }
        rejects(() -> {
            var tree = DataLoader.mapper.valueToTree(definitions);
            ((tools.jackson.databind.node.ObjectNode)
                tree.at("/weapons/basic_sword/attack_stats/secondary")).remove("sounds");
            DataLoader.mapper.treeToValue(tree, WeaponsData.class);
        }, "missing secondary sounds");
        rejects(() -> new Bounds(BoundsType.RECT, 1, 0, 1, 2, ZERO, ZERO, ZERO, ZERO), "collapsed bounds");
        rejects(() -> new Bounds(BoundsType.RECT, Float.NaN, 0, 2, 2, ZERO, ZERO, ZERO, ZERO), "nonfinite bounds");
        for (var weapon : definitions.weapons().values()) {
            for (var attack : new AttackData[]{weapon.attackStats().primary(), weapon.attackStats().secondary()}) {
                var bounds = attack.bounds();
                check(bounds.offsetUp().equals(ZERO) && bounds.offsetDown().equals(ZERO)
                    && bounds.offsetLeft().equals(ZERO) && bounds.offsetRight().equals(ZERO), "zero authored offsets");
            }
        }
        for (String direction : new String[]{"up", "down", "left", "right"}) {
            String field = "offset_" + direction;
            for (String invalid : new String[]{"null", "{}", "{\"x\":0}", "{\"y\":0}", "{\"x\":null,\"y\":0}"}) {
                rejects(() -> {
                    var tree = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.valueToTree(primary.bounds());
                    tree.set(field, DataLoader.mapper.readTree(invalid));
                    DataLoader.mapper.treeToValue(tree, Bounds.class);
                }, "invalid " + field);
            }
            rejects(() -> {
                var tree = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.valueToTree(primary.bounds());
                tree.remove(field);
                DataLoader.mapper.treeToValue(tree, Bounds.class);
            }, "missing " + field);
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            rejects(() -> new Offset(invalid, 0), "nonfinite offset X");
            rejects(() -> new Offset(0, invalid), "nonfinite offset Y");
        }
        rejects(() -> DataLoader.mapper.readValue("{\"animation\":{},\"bounds\":{},\"stats\":{}}", AttackData.class),
            "incomplete JSON attack");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"cooldown_seconds\":0.25}", Stats.class), "missing hit frame");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":null,\"cooldown_seconds\":0.25}", Stats.class), "null hit frame");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2}", Stats.class), "missing cooldown");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2,\"cooldown_seconds\":null}", Stats.class), "null cooldown");
        rejects(() -> DataLoader.mapper.readValue("{\"speed\":1,\"atlas_key\":\"jab\",\"frame_interval\":0.1}",
            AnimationData.class), "missing sound frame");
        rejects(() -> DataLoader.mapper.readValue("{\"type\":\"RECT\",\"y1\":0,\"x2\":64,\"y2\":64}",
            Bounds.class), "missing bounds coordinate");
    }

    private static void assetIds(WeaponsData definitions) {
        var sounds = WeaponAssets.of("basic_sword", definitions);
        check(sounds.all().size() == 4 && sounds.primary().attack().path().equals("audio/sword_swing.wav")
            && sounds.primary().hit().path().equals("audio/sword_hit.wav"), "weapon sounds");
        check(sounds.all().stream().map(a -> a.id()).distinct().count() == 4, "distinct runtime sound IDs");
        check(sounds.all().stream().allMatch(a -> a.id().startsWith("weapon/basic_sword/")), "selected weapon sound IDs");
        rejects(() -> WeaponAssets.of("missing", definitions), "unknown weapon assets");
        var atlases = CharacterAssets.all(definitions.atlas());
        check(atlases.size() == 7 && atlases.containsAll(java.util.List.of(CharacterAssets.CHARACTER,
            CharacterAssets.ENEMIES, CharacterAssets.HEAD, CharacterAssets.BODY, CharacterAssets.HANDS,
            CharacterAssets.FEET, CharacterAssets.WEAPONS)), "all shared character atlases");
        for (var atlas : atlases) {
            check(atlas.path().equals("atlases/" + atlas.id() + ".atlas"), "shared atlas logical ID and path");
        }
    }

    private static void settingsAtomicity() throws Exception {
        var fixture = Files.createTempFile("weapon-settings", ".json");
        try {
            var settings = Files.readString(Path.of("assets/game.settings.json"));
            Files.writeString(fixture, settings.replace("\"soundPath\": \"audio/\"", "\"soundPath\": \"effects/\""));
            useFile(fixture);
            GameConsts.loadSettings();
            check(Asset.Sound.of("test", "swing.wav").path().equals("effects/swing.wav"), "configured sound prefix");
            check(Asset.Music.of("test", "town.ogg").path().equals("audio/town.ogg"), "independent music prefix");
            float width = GameConsts.VIEW_WIDTH;
            Files.writeString(fixture, settings.replace("21.3", "0"));
            rejects(GameConsts::loadSettings, "invalid settings");
            check(GameConsts.VIEW_WIDTH == width && GameConsts.SOUND_PATH.equals("effects/"), "invalid settings apply nothing");
        } finally {
            Files.deleteIfExists(fixture);
            useAssets();
            GameConsts.loadSettings();
        }
    }

    private static void useAssets() { useResolver(path -> new FileHandle("assets/" + path)); }
    private static void useFile(Path file) { useResolver(path -> new FileHandle(file.toFile())); }
    private static void useResolver(java.util.function.Function<String, FileHandle> resolver) {
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("internal")) { return resolver.apply((String) arguments[0]); }
                throw new UnsupportedOperationException(method.getName());
            });
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Accepted " + message);
    }
}
