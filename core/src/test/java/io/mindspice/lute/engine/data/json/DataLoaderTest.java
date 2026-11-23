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
            var settings = DataLoader.mapper.readTree(Path.of("assets/game.settings.json").toFile());
            check(GameConsts.VIEW_WIDTH == settings.required("viewWidth").floatValue()
                && GameConsts.UNIT_SCALE == settings.required("unitScale").floatValue(), "settings constants match authored values");
            check(GameConsts.NIGHT_AMBIENT.tint().equals(settings.at("/nightAmbient/tint").stringValue())
                && GameConsts.NIGHT_AMBIENT.brightness() == settings.at("/nightAmbient/brightness").floatValue(),
                "ambient settings match authored values");
            var debug = settings.required("debugLayers");
            check(GameConsts.DEBUG_LAYERS.equals(new GameConsts.DebugLayers(debug.required("physics").booleanValue(),
                debug.required("triggers").booleanValue(), debug.required("rawLightMap").booleanValue(),
                debug.required("lightingOutlines").booleanValue())), "debug layers match authored values");
            check(Files.mismatch(Path.of("raw_assets/game.settings.json"), Path.of("assets/game.settings.json")) == -1,
                "generated settings match source");
            var player = DataLoader.parsePlayerData();
            var definitions = DataLoader.parseWeaponData();
            playerSchema(player, definitions);
            characterBoundsSchema(player);
            weaponSchema(definitions);
            rejectsFractionalCombatData();
            projectileSchema();
            missingProjectileData();
            actorsSchema();
            objectsSchema();
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

    private static void rejectsFractionalCombatData() throws Exception {
        var player = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.readTree(Path.of("assets/data/player.json").toFile());
        var weapons = DataLoader.mapper.readTree(Path.of("assets/data/weapons.json").toFile());
        var stats = (tools.jackson.databind.node.ObjectNode) weapons.at("/weapons/basic_sword/attack_stats/primary/stats");
        var fixture = Files.createTempFile("fractional-combat-data", ".json");
        try {
            useFile(fixture);
            Files.writeString(fixture, player.put("max_health", 50.5).toString());
            rejects(DataLoader::parsePlayerData, "fractional player max_health");
            stats.put("damage", 10.5);
            Files.writeString(fixture, weapons.toString());
            rejects(DataLoader::parseWeaponData, "fractional weapon damage");
        } finally {
            useAssets();
            Files.delete(fixture);
        }
    }

    private static void projectileSchema() {
        var arrow = DataLoader.loadGameData().projectilesData().ironArrow();
        rejects(() -> DataLoader.mapper.readValue("{\"projectiles\":{}}", ProjectilesData.class),
            "missing iron arrow definition");
        check(arrow.atlasKey().equals("projectiles/IronArrow") && arrow.speed() == 8 && arrow.lifetime() == 5,
            "iron arrow binds the atlas region and flight settings");
        check(arrow.collision().equals(CollisionData.ellipse(new CharacterBounds(0, 0, 10, 8.5f))),
            "iron arrow binds the authored tip dimensions");
        for (String key : new String[]{"atlas_key", "speed", "lifetime", "collision"}) {
            rejects(() -> {
                var tree = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.valueToTree(arrow);
                tree.remove(key);
                DataLoader.mapper.treeToValue(tree, ProjectilesData.Projectile.class);
            }, "missing projectile " + key);
        }
        var bounds = "{\"x1\":0,\"y1\":0,\"x2\":10,\"y2\":10}";
        for (var shape : CollisionData.Shape.values()) {
            var json = "{\"shape\":\"" + shape + "\",\"bounds\":" + bounds + ",\"rotation\":-45}";
            var collision = DataLoader.mapper.readValue(json, CollisionData.class);
            check(collision.shape() == shape && collision.rotation() == -45f, "Collision shape and rotation bind");
            for (String field : new String[]{"shape", "bounds", "rotation"}) {
                var tree = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.readTree(json);
                tree.remove(field);
                rejects(() -> DataLoader.mapper.treeToValue(tree, CollisionData.class), "Missing collision " + field);
                tree.putNull(field);
                rejects(() -> DataLoader.mapper.treeToValue(tree, CollisionData.class), "Null collision " + field);
            }
        }
        for (String json : new String[]{
            "{\"shape\":\"UNKNOWN\",\"bounds\":" + bounds + ",\"rotation\":0}",
            "{\"shape\":\"ELLIPSE\",\"bounds\":" + bounds + ",\"rotation\":1e100}",
            "{\"shape\":\"CIRCLE\",\"bounds\":" + bounds.replace("10}", "8}") + ",\"rotation\":0}"
        }) {
            rejects(() -> DataLoader.mapper.readValue(json, CollisionData.class), "Invalid collision: " + json);
        }
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

    private static void actorsSchema() throws Exception {
        var directory = Files.createTempDirectory("actor-data");
        var characters = directory.resolve("characters.json");
        var enemies = directory.resolve("enemies.json");
        for (String filename : new String[]{"characters.json", "enemies.json"}) {
            check(Files.mismatch(Path.of("raw_assets/data", filename), Path.of("assets/data", filename)) == -1,
                "Generated actor catalogue matches source: " + filename);
        }
        String common = """
            "max_health": 80, "move_speed": 3.5,
            "logical_size": {"width": 72, "height": 64},
            "collision": {"shape": "ELLIPSE", "bounds": {"x1": 18, "y1": 4, "x2": 45, "y2": 15}, "rotation": 0},
            "hitbox_bounds": {"x1": 14, "y1": 6, "x2": 50, "y2": 55},
            "animation": {"atlas_key": "basic_npc", "speed": 1, "frame_interval": 0.12, "sound_frame": 0},
            "sounds": {"move": "step.wav", "damage": "hurt.wav"},
            "detection_bounds": {"radius": 72}
            """;
        String npc = "{\"name\":\"Town Guard\"," + common + ",\"decision_graph\":\"wander\",\"loadout\":{\"body\":\"basic\",\"weapon\":\"basic_bow\"}}";
        String enemy = "{\"name\":\"Undead Guard\"," + common.replace("72}", "192.5}") + ",\"decision_graph\":\"wander_attack\",\"weapon\":\"basic_sword\"}";
        String npcs = "{\"guard\":" + npc + ",\"merchant\":" + npc.replace("Town Guard", "Merchant").replace("80", "60") + "}";
        String entry = "{\"guard\":%s}";
        try {
            useResolver(path -> new FileHandle(switch (path) {
                case "data/characters.json" -> characters.toFile();
                case "data/enemies.json" -> enemies.toFile();
                case "data/actors.json" -> throw new AssertionError("Legacy actors.json must not be loaded");
                default -> Path.of("assets", path).toFile();
            }));
            Files.writeString(enemies, entry.formatted(enemy));
            rejects(DataLoader::loadGameData, "Missing characters file");
            Files.delete(enemies);
            Files.writeString(characters, npcs);
            rejects(DataLoader::loadGameData, "Missing enemies file");
            Files.writeString(enemies, entry.formatted(enemy));
            var data = DataLoader.loadGameData().actorsData();
            var merchant = data.npc().get("merchant");
            check(merchant.maxHealth() == 60 && merchant.name().equals("Merchant"), "Multiple NPC IDs resolve independently");
            var guard = data.npc().get("guard");
            check(guard.moveSpeed() == 3.5f && guard.loadout().body().equals("basic") && guard.weapon().equals("basic_bow")
                && guard.logicalSize().width() == 72 && guard.collision().bounds().x1() == 18 && guard.hitboxBounds().x1() == 14,
                "NPC definitions mirror player gameplay and loadout fields");
            var undead = data.enemies().get("guard");
            check(undead.weapon().equals("basic_sword") && guard.detectionBounds().radius() == 72
                && undead.detectionBounds().radius() == 192.5f && guard.decisionGraph().equals("wander")
                && undead.decisionGraph().equals("wander_attack"), "Role-scoped IDs bind distinct radii and graph keys");
            check(guard.name().equals("Town Guard") && undead.name().equals("Undead Guard")
                && guard.maxHealth() == 80 && undead.maxHealth() == 80, "Both catalogues bind name and maximum health");
            Files.writeString(characters, "{}");
            Files.writeString(enemies, entry.formatted(enemy.replace("\"basic_sword\"", "null")));
            check(DataLoader.loadGameData().actorsData().enemies().get("guard").weapon() == null,
                "Character catalogue may be empty and enemy explicitly unarmed");
            Files.writeString(enemies, "{}");
            check(DataLoader.loadGameData().actorsData().enemies().isEmpty(), "Enemy catalogue may be empty");
            Files.writeString(characters, npcs);
            for (String invalid : new String[]{npc, enemy.replace(",\"weapon\":\"basic_sword\"", ""),
                    enemy.replace("basic_sword", "unknown_weapon"), enemy.replace("192.5", "0"),
                    enemy.replace("192.5", "1e100"), enemy.replace("\"radius\": 192.5", ""),
                    enemy.replace("\"x2\": 50", "\"x2\": 100"), "null",
                    enemy.replace(",\"decision_graph\":\"wander_attack\"", ""),
                    enemy.replace("wander_attack", ""), enemy.replace("wander_attack", "unknown_graph"),
                    enemy.replace("\"wander_attack\"", "null")}) {
                Files.writeString(enemies, entry.formatted(invalid));
                rejects(DataLoader::loadGameData, "Invalid enemy definition: " + invalid);
            }
            Files.writeString(enemies, entry.formatted(enemy));
            for (String invalid : new String[]{"null", "{\"\":" + npc + "}", "{} {}",
                    "{\"guard\":" + npc + ",\"guard\":" + npc + "}", entry.formatted(enemy)}) {
                Files.writeString(characters, invalid);
                rejects(DataLoader::loadGameData, "Invalid character catalogue: " + invalid);
            }
            Files.writeString(characters, npcs);
            for (var target : Map.of(characters, npc, enemies, enemy).entrySet()) {
                var definition = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.readTree(target.getValue());
                for (String field : new String[]{"name", "max_health"}) {
                    var missing = definition.deepCopy();
                    missing.remove(field);
                    Files.writeString(target.getKey(), entry.formatted(missing));
                    rejects(DataLoader::loadGameData, "Missing " + field + " in " + target.getKey());
                }
                for (String invalid : new String[]{definition.deepCopy().put("name", "   ").toString(),
                        target.getValue().replace("\"max_health\": 80", "\"max_health\": 0"),
                        target.getValue().replace("\"max_health\": 80", "\"max_health\": 80.5"),
                        target.getValue().replace("\"max_health\"", "\"base_health\"")}) {
                    Files.writeString(target.getKey(), entry.formatted(invalid));
                    rejects(DataLoader::loadGameData, "Invalid name or health in " + target.getKey());
                }
                Files.writeString(target.getKey(), entry.formatted(target.getValue()));
            }
        } finally {
            useAssets();
            Files.deleteIfExists(characters);
            Files.deleteIfExists(enemies);
            Files.delete(directory);
        }
    }

    private static void objectsSchema() throws Exception {
        var campfire = DataLoader.loadGameData().objectsData().get("campfire1");
        check(campfire.animation().orElseThrow().atlasKey().equals("campfire1/campfire1")
            && campfire.collision().orElseThrow().equals(CollisionData.ellipse(new CharacterBounds(4, 2, 28, 14))),
            "Campfire binds animation and collision");
        check(campfire.emissive().orElseThrow().pulse().isPresent(), "Campfire binds pulsing emission");
        check(Files.mismatch(Path.of("raw_assets/data/objects.json"), Path.of("assets/data/objects.json")) == -1,
            "Generated objects catalogue matches source");
        var directory = Files.createTempDirectory("object-data");
        var objects = directory.resolve("objects.json");
        try {
            useResolver(path -> new FileHandle(path.equals("data/objects.json") ? objects.toFile() : Path.of("assets", path).toFile()));
            rejects(DataLoader::loadGameData, "Missing objects catalogue");
            Files.writeString(objects, "{\"sign\":{\"animation\":null,\"collision\":null,\"emissive\":null}}");
            var sign = DataLoader.loadGameData().objectsData().get("sign");
            check(sign.animation().isEmpty() && sign.collision().isEmpty() && sign.emissive().isEmpty(),
                "Object fields accept explicit null");
            for (String invalid : new String[]{"{\"animation\":null,\"emissive\":null}", "{\"collision\":null,\"emissive\":null}",
                    "{\"animation\":null,\"collision\":null}", "{\"animation\":null,\"collision_bounds\":null,\"emissive\":null}", "null"}) {
                Files.writeString(objects, "{\"sign\":" + invalid + "}");
                rejects(DataLoader::loadGameData, "Invalid object definition: " + invalid);
            }
        } finally {
            useAssets();
            Files.deleteIfExists(objects);
            Files.delete(directory);
        }
    }

    private static void playerSchema(PlayerData player, WeaponsData definitions) {
        check(player.maxHealth() > 0 && player.moveSpeed() == 4, "player stats");
        check(player.animation().frameInterval() == .125f && player.animation().speed() == 1
            && player.animation().atlasKey().isEmpty() && player.animation().soundFrame() == 0, "flat base animation");
        check(player.logicalSize().width() == 64 && player.logicalSize().height() == 64, "logical character size");
        check(player.collision().equals(CollisionData.ellipse(new CharacterBounds(20, 4, 44, 14))), "authored feet ellipse");
        check(player.hitboxBounds().equals(new CharacterBounds(16, 6, 48, 56)), "authored standing hurtbox");
        for (String key : new String[]{"logical_size", "collision", "hitbox_bounds"}) {
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
        var unequipped = new PlayerData.Loadout(null, null, null, null, null, null);
        for (String json : new String[]{"{}", "{\"head\":null,\"body\":null,\"hands\":null,\"feet\":null,\"weapon\":null}"}) {
            check(DataLoader.mapper.readValue(json, PlayerData.Loadout.class).equals(unequipped),
                "omitted and null IDs both leave slots empty");
        }
        check(DataLoader.mapper.readValue("{\"head\":\"basic\",\"body\":\"basic\",\"hands\":\"basic\",\"weapon\":\"basic_sword\"}",
            PlayerData.Loadout.class).equals(new PlayerData.Loadout("basic", "basic", "basic", null, "basic_sword", null)),
            "omitted feet leave only the feet slot empty");
        check(DataLoader.mapper.readValue("{\"head\":\"basic\",\"body\":\"basic\",\"hands\":\"basic\",\"feet\":\"basic\"}",
            PlayerData.Loadout.class).equals(new PlayerData.Loadout("basic", "basic", "basic", "basic", null, null)),
            "omitted weapon leaves only the weapon slot empty");
        var cycle = DataLoader.mapper.readValue("{\"weapon_cycle\":[\"basic_sword\",\"basic_bow\"]}", PlayerData.Loadout.class);
        check(cycle.weaponCycle().equals(java.util.List.of("basic_sword", "basic_bow")), "test weapon cycle preserves order");
        for (String invalid : new String[]{"[\" \" ]", "[null]", "[\"basic_sword\",\"basic_sword\"]"}) {
            rejects(() -> DataLoader.mapper.readValue("{\"weapon_cycle\":" + invalid + "}", PlayerData.Loadout.class),
                "invalid weapon cycle: " + invalid);
        }
        for (String slot : new String[]{"head", "body", "hands", "feet", "weapon"}) {
            rejects(() -> DataLoader.mapper.readValue("{\"" + slot + "\":\" \"}", PlayerData.Loadout.class), "blank " + slot);
        }
        rejects(() -> new PlayerData("player", 0, 100, loadout, player.animation(), player.sounds(), player.logicalSize(), player.collision(), player.hitboxBounds()), "zero move speed");
        rejects(() -> new PlayerData("player", Float.NaN, 100, loadout, player.animation(), player.sounds(), player.logicalSize(), player.collision(), player.hitboxBounds()), "nonfinite move speed");
        rejects(() -> new PlayerData("player", 1, 0, loadout, player.animation(), player.sounds(), player.logicalSize(), player.collision(), player.hitboxBounds()), "zero health");
        rejects(() -> new PlayerData("player", 1, 100, null, player.animation(), player.sounds(), player.logicalSize(), player.collision(), player.hitboxBounds()), "missing loadout");
        rejects(() -> new PlayerData.Sounds("hit", null), "missing damage sound");
        rejects(() -> new PlayerData.Sounds("", "hit"), "empty move sound");
    }

    private static tools.jackson.databind.node.ObjectNode boundsNode(tools.jackson.databind.JsonNode tree, String section) {
        var node = tree.get(section);
        return (tools.jackson.databind.node.ObjectNode) (section.equals("collision") ? node.get("bounds") : node);
    }

    private static void characterBoundsSchema(PlayerData player) {
        for (String section : new String[]{"logical_size", "collision", "hitbox_bounds"}) {
            String[] members = section.equals("logical_size") ? new String[]{"width", "height"}
                : new String[]{"x1", "y1", "x2", "y2"};
            for (String member : members) {
                rejects(() -> {
                    var tree = DataLoader.mapper.valueToTree(player);
                    boundsNode(tree, section).remove(member);
                    DataLoader.mapper.treeToValue(tree, PlayerData.class);
                }, "JSON missing " + section + "." + member);
                for (String invalid : new String[]{"null", "-1", "1e50", "{}"}) {
                    rejects(() -> {
                        var tree = DataLoader.mapper.valueToTree(player);
                        boundsNode(tree, section).set(member, DataLoader.mapper.readTree(invalid));
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
        for (String section : new String[]{"collision", "hitbox_bounds"}) {
            for (String invalid : new String[]{"{\"x1\":20,\"y1\":4,\"x2\":20,\"y2\":14}",
                "{\"x1\":20,\"y1\":14,\"x2\":44,\"y2\":4}",
                "{\"x1\":20,\"y1\":4,\"x2\":65,\"y2\":14}",
                "{\"x1\":20,\"y1\":4,\"x2\":44,\"y2\":65}"}) {
                rejects(() -> {
                    var tree = DataLoader.mapper.valueToTree(player);
                    if (section.equals("collision")) {
                        ((tools.jackson.databind.node.ObjectNode) tree.get(section)).set("bounds", DataLoader.mapper.readTree(invalid));
                    } else {
                        ((tools.jackson.databind.node.ObjectNode) tree).set(section, DataLoader.mapper.readTree(invalid));
                    }
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
            var configured = (tools.jackson.databind.node.ObjectNode) DataLoader.mapper.readTree(settings);
            configured.put("soundPath", "effects/").put("startupMap", "first_village").put("projectPath", "world.tiled-project");
            Files.writeString(fixture, configured.toString());
            useFile(fixture);
            GameConsts.loadSettings();
            check(Asset.Sound.of("test", "swing.wav").path().equals("effects/swing.wav"), "configured sound prefix");
            check(Asset.Music.of("test", "town.ogg").path().equals("audio/town.ogg"), "independent music prefix");
            check(GameConsts.STARTUP_MAP.equals("first_village") && GameConsts.PROJECT_PATH.equals("world.tiled-project"),
                "Settings publish authored startup map and project");
            for (var key : new String[]{"startupMap", "projectPath"}) {
                var missing = configured.deepCopy();
                missing.remove(key);
                rejects(() -> GameConsts.parseSettings(missing.toString()), "Missing " + key);
                var invalid = configured.deepCopy().put(key, "../outside.tiled-project");
                rejects(() -> GameConsts.parseSettings(invalid.toString()), "Invalid " + key + " path");
            }
            float width = GameConsts.VIEW_WIDTH;
            Files.writeString(fixture, configured.deepCopy().put("viewWidth", 0).toString());
            rejects(GameConsts::loadSettings, "invalid settings");
            check(GameConsts.VIEW_WIDTH == width && GameConsts.SOUND_PATH.equals("effects/"), "invalid settings apply nothing");
            var ambient = GameConsts.NIGHT_AMBIENT;
            var debug = GameConsts.DEBUG_LAYERS;
            String changed = configured.deepCopy().put("viewWidth", width + 1).toString();
            for (var duplicate : new String[]{changed.replace("\"nightAmbient\":", "\"nightAmbient\":null,\"nightAmbient\":"),
                changed.replace("\"brightness\":", "\"brightness\":0,\"brightness\":"),
                changed.replace("\"debugLayers\":", "\"debugLayers\":null,\"debugLayers\":"),
                changed.replace("\"triggers\":", "\"triggers\":false,\"triggers\":")}) {
                Files.writeString(fixture, duplicate);
                rejects(GameConsts::loadSettings, "duplicate setting at startup");
                check(GameConsts.VIEW_WIDTH == width && GameConsts.NIGHT_AMBIENT == ambient && GameConsts.DEBUG_LAYERS == debug,
                    "invalid settings publish nothing");
            }
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
