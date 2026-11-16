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
    public static void main(String[] args) throws Exception {
        var previousFiles = Gdx.files;
        try {
            useAssets();
            GameConsts.loadSettings();
            check(GameConsts.VIEW_WIDTH == 21.3f && GameConsts.UNIT_SCALE == 1f / 32f, "settings constants");
            var player = DataLoader.parsePlayerData();
            var definitions = DataLoader.parseWeaponData();
            playerSchema(player);
            weaponSchema(player, definitions);
            invalidMetadata(player, definitions);
            assetIds(definitions);
            settingsAtomicity();
            check(Files.mismatch(Path.of("raw_assets/data/player.json"), Path.of("assets/data/player.json")) == -1,
                "generated player matches source");
            check(Files.mismatch(Path.of("raw_assets/data/weapons.json"), Path.of("assets/data/weapons.json")) == -1,
                "generated weapons match source");
            System.out.println("DataLoaderTest: passed");
        } finally { Gdx.files = previousFiles; }
    }

    private static void playerSchema(PlayerData player) {
        check(player.maxHealth() == 100 && player.moveSpeed() == 4, "player stats");
        check(player.animation().frameInterval() == .125f && player.animation().speed() == 1
            && player.animation().atlasKey().isEmpty() && player.animation().soundFrame() == 0, "flat base animation");
        var loadout = player.loadout();
        check(loadout.head().equals("basic") && loadout.body().equals("basic")
            && loadout.hands().equals("basic") && loadout.feet().equals("basic")
            && loadout.weapon().equals("basic_sword"), "default loadout");
        check(player.sounds().move().equals("sword_hit.wav") && player.sounds().damage().equals("sword_hit.wav"),
            "player sound definitions");
        var unarmed = new PlayerData.Loadout("basic", "basic", "basic", "basic", null);
        check(unarmed.weapon() == null, "null weapon is explicitly unarmed");
        rejects(() -> new PlayerData.Loadout(null, "basic", "basic", "basic", null), "missing head");
        rejects(() -> new PlayerData.Loadout("basic", " ", "basic", "basic", null), "blank body");
        rejects(() -> new PlayerData.Loadout("basic", "basic", "", "basic", null), "empty hands");
        rejects(() -> new PlayerData.Loadout("basic", "basic", "basic", null, null), "missing feet");
        rejects(() -> new PlayerData.Loadout("basic", "basic", "basic", "basic", " "), "blank weapon");
        rejects(() -> new PlayerData("player", 0, 100, loadout, player.animation(), player.sounds()), "zero move speed");
        rejects(() -> new PlayerData("player", Float.NaN, 100, loadout, player.animation(), player.sounds()), "nonfinite move speed");
        rejects(() -> new PlayerData("player", 1, 0, loadout, player.animation(), player.sounds()), "zero health");
        rejects(() -> new PlayerData("player", 1, 100, null, player.animation(), player.sounds()), "missing loadout");
        rejects(() -> new PlayerData.Sounds("hit", null), "missing damage sound");
        rejects(() -> new PlayerData.Sounds("", "hit"), "empty move sound");
    }

    private static void weaponSchema(PlayerData player, WeaponsData definitions) throws Exception {
        check(definitions.atlas().equals("weapons.atlas") && definitions.weapons().size() == 3, "shared catalog");
        var sword = definitions.requireWeapon(player.loadout().weapon());
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
                    && attack.stats().hitFrame() == json.get("stats").get("hit_frame").asInt(), "authored " + slot + " stats");
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
        var stats = DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2}", Stats.class);
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
        rejects(() -> new Stats(10, 0, -1), "negative hit frame");
        rejects(() -> new Stats(0, 0, 0), "zero damage");
        rejects(() -> new Stats(10, -1, 0), "negative cost");
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
        rejects(() -> new Bounds(BoundsType.RECT, 1, 0, 1, 2), "collapsed bounds");
        rejects(() -> new Bounds(BoundsType.RECT, Float.NaN, 0, 2, 2), "nonfinite bounds");
        rejects(() -> DataLoader.mapper.readValue("{\"head\":\"basic\",\"body\":\"basic\",\"hands\":\"basic\"}",
            PlayerData.Loadout.class), "JSON missing feet");
        rejects(() -> DataLoader.mapper.readValue("{\"animation\":{},\"bounds\":{},\"stats\":{}}", AttackData.class),
            "incomplete JSON attack");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0}", Stats.class), "missing hit frame");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":null}", Stats.class), "null hit frame");
        rejects(() -> DataLoader.mapper.readValue("{\"speed\":1,\"atlas_key\":\"jab\",\"frame_interval\":0.1}",
            AnimationData.class), "missing sound frame");
        rejects(() -> DataLoader.mapper.readValue("{\"type\":\"RECT\",\"y1\":0,\"x2\":64,\"y2\":64}",
            Bounds.class), "missing bounds coordinate");
        rejects(() -> DataLoader.mapper.readValue("{\"head\":\"basic\",\"body\":\"basic\",\"hands\":\"basic\",\"feet\":\"basic\"}",
            PlayerData.Loadout.class), "missing explicit weapon selection");
    }

    private static void assetIds(WeaponsData definitions) {
        var sounds = WeaponAssets.of("basic_sword", definitions);
        check(sounds.all().size() == 4 && sounds.primary().attack().path().equals("audio/sword_swing.wav")
            && sounds.primary().hit().path().equals("audio/sword_hit.wav"), "weapon sounds");
        check(sounds.all().stream().map(a -> a.id()).distinct().count() == 4, "distinct runtime sound IDs");
        check(sounds.all().stream().allMatch(a -> a.id().startsWith("weapon/basic_sword/")), "selected weapon sound IDs");
        rejects(() -> WeaponAssets.of("missing", definitions), "unknown weapon assets");
        var atlases = CharacterAssets.all(definitions.atlas());
        check(atlases.size() == 6 && atlases.containsAll(java.util.List.of(CharacterAssets.CHARACTER,
            CharacterAssets.HEAD, CharacterAssets.BODY, CharacterAssets.HANDS, CharacterAssets.FEET, CharacterAssets.WEAPONS)),
            "all shared character atlases");
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
