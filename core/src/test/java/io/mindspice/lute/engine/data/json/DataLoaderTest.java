package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.data.json.MapActorData.EnemyDetectionBounds.Shape;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataLoaderTest {
    private static final Offset ZERO = new Offset(0, 0);
    public static void main(String[] args) throws Exception {
        var previousFiles = Gdx.files;
        try {
            useFixtures();
            GameConsts.loadSettings();
            if (List.of(args).equals(List.of("--actors-only"))) {
                actorsSchema();
                System.out.println("DataLoaderTest: actor schema passed");
                return;
            }
            if (List.of(args).equals(List.of("--settings-only"))) {
                debugLayerLaunchModes();
                System.out.println("DataLoaderTest: settings launch modes passed");
                return;
            }
            check(GameConsts.VIEW_WIDTH == 26f && GameConsts.UNIT_SCALE == .0625f
                && GameConsts.NIGHT_AMBIENT.equals(new GameConsts.NightAmbient("#124578", .4f)),
                "Settings publish fixture dimensions, scale, and ambient light");
            var game = DataLoader.loadGameData();
            var player = game.playerData();
            var definitions = game.weaponsData();
            playerSchema(player);
            playerWeaponReferences();
            characterBoundsSchema(player);
            weaponSchema(definitions);
            attackKeys();
            if (List.of(args).contains("--attack-keys")) {
                System.out.println("DataLoaderTest: attack keys passed");
                return;
            }
            rejectsInvalidCombatData();
            projectileSchema();
            missingProjectileData();
            actorsSchema();
            objectsSchema();
            invalidMetadata(definitions);
            assetIds(definitions);
            debugLayerLaunchModes();
            settingsAtomicity();
            System.out.println("DataLoaderTest: passed");
        } finally { Gdx.files = previousFiles; }
    }

    private static void rejectsInvalidCombatData() throws Exception {
        var player = fixtureTree("data/player.json");
        var weapons = fixtureTree("data/weapons.json");
        var stats = (ObjectNode) weapons.at("/weapons/fixture_blade/attack_stats/primary/stats");
        var file = Files.createTempFile("invalid-combat-data", ".json");
        try {
            useFile(GameConsts.DATA_PATH + "player.json", file);
            Files.writeString(file, player.put("max_health", 50.5).toString());
            rejects(DataLoader::parsePlayerData, "fractional player max_health");

            useFile(GameConsts.DATA_PATH + "weapons.json", file);
            stats.put("damage", 10.5);
            Files.writeString(file, weapons.toString());
            rejects(DataLoader::parseWeaponData, "fractional weapon damage");

            stats.putObject("damage").put("min", 2).put("max", 3);
            String validRange = weapons.toString();
            Files.writeString(file, validRange);
            DataLoader.parseWeaponData();
            String duplicateMin = validRange.replace("\"min\":2,\"max\":3", "\"min\":1,\"min\":2,\"max\":3");
            Files.writeString(file, duplicateMin);
            rejects(DataLoader::parseWeaponData, "duplicate damage bound");
        } finally {
            useFixtures();
            Files.delete(file);
        }
    }

    private static void projectileSchema() {
        var arrow = DataLoader.loadGameData().projectilesData().ironArrow();
        rejects(() -> DataLoader.mapper.readValue("{\"projectiles\":{}}", ProjectilesData.class),
            "missing iron arrow definition");
        check(arrow.atlasKey().equals("fixture_arrow") && arrow.speed() == 13 && arrow.lifetime() == 2.5f
            && arrow.collision().shape() == CollisionData.Shape.RECT
            && arrow.collision().bounds().equals(new CharacterBounds(1, 2, 7, 5))
            && arrow.tipX() == 5 && arrow.tipY() == 9 && arrow.artDegrees() == 90,
            "Required projectile resolves from the fixture with its configured flight and collision");
        var definition = (ObjectNode) fixtureTree("data/projectiles.json").at("/projectiles/iron_arrow");
        for (String key : new String[]{"atlas_key", "speed", "lifetime", "collision", "tip_x", "tip_y", "art_degrees"}) {
            var missing = definition.deepCopy();
            missing.remove(key);
            rejects(() -> DataLoader.mapper.treeToValue(missing, ProjectilesData.Projectile.class), "missing projectile " + key);
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
        var projectiles = emptyDirectory.resolve("projectiles.json");
        try {
            useFile(GameConsts.DATA_PATH + "projectiles.json", projectiles);
            rejectsMissingFile(projectiles);
        } finally {
            useFixtures();
            Files.delete(emptyDirectory);
        }
    }

    private static void actorsSchema() throws Exception {
        var directory = Files.createTempDirectory("actor-data");
        var characters = directory.resolve("characters.json");
        var enemies = directory.resolve("enemies.json");
        String common = """
            "max_health": 80, "move_speed": 3.5,
            "logical_size": {"width": 72, "height": 64},
            "collision": {"shape": "ELLIPSE", "bounds": {"x1": 18, "y1": 4, "x2": 45, "y2": 15}, "rotation": 0},
            "hitbox_bounds": {"x1": 14, "y1": 6, "x2": 50, "y2": 55},
            "animation": {"atlas_key": "fixture_actor", "speed": 1, "frame_interval": 0.12, "sound_frame": 0},
            "sounds": {"move": "step.wav", "damage": "hurt.wav"},
            "detection_bounds": {"radius": 72}
            """;
        String npc = "{\"name\":\"Town Guard\"," + common + ",\"behavior_graph\":\"wander\",\"loadout\":{\"body\":\"fixture_body\",\"weapon\":\"fixture_bow\"}}";
        String enemy = "{\"name\":\"Undead Guard\"," + common.replace("72}", "192.5, \"shape\": \"FORWARD_SEMICIRCLE\"}")
            + ",\"behavior_graph\":\"wander_attack\",\"weapon\":\"fixture_blade\",\"infight_chance\":37}";
        String npcs = "{\"guard\":" + npc + ",\"merchant\":" + npc.replace("Town Guard", "Merchant").replace("80", "60") + "}";
        String entry = "{\"guard\":%s}";
        try {
            useResolver(path -> switch (path) {
                case "data/characters.json" -> new FileHandle(characters.toFile());
                case "data/enemies.json" -> new FileHandle(enemies.toFile());
                case "data/actors.json" -> throw new AssertionError("Legacy actors.json must not be loaded");
                default -> fixture(path);
            });
            Files.writeString(enemies, entry.formatted(enemy));
            rejectsMissingFile(characters);
            Files.delete(enemies);
            Files.writeString(characters, npcs);
            rejectsMissingFile(enemies);
            Files.writeString(enemies, "{\"guard\":" + enemy + ",\"sentinel\":"
                + enemy.replace("FORWARD_SEMICIRCLE", "FULL_CIRCLE") + "}");
            var data = DataLoader.loadGameData().actorsData();
            var merchant = data.npc().get("merchant");
            check(merchant.maxHealth() == 60 && merchant.name().equals("Merchant"), "Multiple NPC IDs resolve independently");
            var guard = data.npc().get("guard");
            var undead = data.enemies().get("guard");
            check(guard.loadout().body().equals("fixture_body") && guard.moveSpeed() == 3.5f
                && guard.logicalSize().width() == 72 && guard.collision().bounds().x1() == 18 && guard.hitboxBounds().x1() == 14,
                "NPC loadout, movement and local bounds bind from the character catalogue");
            check(undead.weapon().equals("fixture_blade") && guard.weapon().equals("fixture_bow") && guard.detectionBounds().radius() == 72
                && undead.detectionBounds().radius() == 192.5f && guard.behaviorGraph().equals("wander")
                && undead.behaviorGraph().equals("wander_attack") && undead.infightChance() == 37,
                "Role-scoped IDs bind radii, graph keys, and enemy infight chance");
            check(undead.detectionBounds().shape() == Shape.FORWARD_SEMICIRCLE
                && data.enemies().get("sentinel").detectionBounds().shape() == Shape.FULL_CIRCLE,
                "Both authored shapes bind independently of spawn key");
            Files.writeString(characters, "{}");
            Files.writeString(enemies, entry.formatted(enemy.replace("\"fixture_blade\"", "null")));
            check(DataLoader.loadGameData().actorsData().enemies().get("guard").weapon() == null,
                "Character catalogue may be empty and enemy explicitly unarmed");
            Files.writeString(enemies, "{}");
            check(DataLoader.loadGameData().actorsData().enemies().isEmpty(), "Enemy catalogue may be empty");
            Files.writeString(enemies, entry.formatted(enemy));
            Files.writeString(characters, npcs.replace("fixture_bow", "missing_npc_weapon"));
            var npcReference = rejects(DataLoader::loadGameData, "unknown NPC weapon");
            check(npcReference.getMessage().contains("Unknown weapon: missing_npc_weapon"), "Loader checks the NPC weapon reference");
            Files.writeString(characters, npcs);
            Files.writeString(enemies, entry.formatted(enemy.replace("fixture_blade", "missing_enemy_weapon")));
            var enemyReference = rejects(DataLoader::loadGameData, "unknown enemy weapon");
            check(enemyReference.getMessage().contains("Unknown weapon: missing_enemy_weapon"), "Loader checks the enemy weapon reference");
            for (String invalid : new String[]{npc, enemy.replace(",\"weapon\":\"fixture_blade\"", ""), enemy.replace("192.5", "0"),
                    enemy.replace("192.5", "1e100"), enemy.replace("\"radius\": 192.5, ", ""),
                    enemy.replace(", \"shape\": \"FORWARD_SEMICIRCLE\"", ""),
                    enemy.replace("\"FORWARD_SEMICIRCLE\"", "null"),
                    enemy.replace("\"FORWARD_SEMICIRCLE\"", "0"),
                    enemy.replace("FORWARD_SEMICIRCLE", ""), enemy.replace("FORWARD_SEMICIRCLE", "TRIANGLE"),
                    enemy.replace("\"x2\": 50", "\"x2\": 100"), "null",
                    enemy.replace(",\"behavior_graph\":\"wander_attack\"", ""),
                    enemy.replace("wander_attack", ""), enemy.replace("wander_attack", "unknown_graph"),
                    enemy.replace("\"wander_attack\"", "null")}) {
                Files.writeString(enemies, entry.formatted(invalid));
                rejects(DataLoader::loadGameData, "Invalid enemy definition: " + invalid);
            }
            Files.writeString(enemies, entry.formatted(enemy));
            for (String invalid : new String[]{"null", "{\"\":" + npc + "}", "{} {}",
                    "{\"guard\":" + npc + ",\"guard\":" + npc + "}", entry.formatted(enemy),
                    entry.formatted(npc.replace("\"radius\": 72", "\"radius\": 72, \"shape\": \"FULL_CIRCLE\""))}) {
                Files.writeString(characters, invalid);
                rejects(DataLoader::loadGameData, "Invalid character catalogue: " + invalid);
            }
            Files.writeString(characters, npcs);
            for (var target : Map.of(characters, npc, enemies, enemy).entrySet()) {
                Files.writeString(target.getKey(), entry.formatted(target.getValue().replace("\"behavior_graph\"", "\"decision_graph\"")));
                rejects(DataLoader::loadGameData, "Legacy decision_graph is not accepted in " + target.getKey());
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
            useFixtures();
            Files.deleteIfExists(characters);
            Files.deleteIfExists(enemies);
            Files.delete(directory);
        }
    }

    private static void objectsSchema() throws Exception {
        var lamp = DataLoader.loadGameData().objectsData().get("fixture_lamp");
        check(lamp.animation().orElseThrow().atlasKey().equals("fixture_lamp")
            && lamp.collision().orElseThrow().equals(new CollisionData(CollisionData.Shape.RECT, new CharacterBounds(1, 3, 12, 7), -15f)),
            "Object catalogue binds animation and collision");
        var light = lamp.emissive().orElseThrow();
        check(light.radiusPixels() == 39 && light.pulse().orElseThrow().equals(new EmissiveData.Pulse(1.2, .12f, .18f)),
            "Object catalogue binds the light radius and pulse settings");
        var directory = Files.createTempDirectory("object-data");
        var objects = directory.resolve("objects.json");
        try {
            useFile(GameConsts.DATA_PATH + "objects.json", objects);
            rejectsMissingFile(objects);
            Files.writeString(objects, "{\"sign\":{\"animation\":null,\"collision\":null,\"emissive\":null}}");
            var sign = DataLoader.loadGameData().objectsData().get("sign");
            check(sign.animation().isEmpty() && sign.collision().isEmpty() && sign.emissive().isEmpty(),
                "Object fields accept explicit null");
            for (String invalid : new String[]{"{\"pickup\":{\"type\":\"HEAL\"}}", "{\"pickup\":{\"type\":\"WEAPON_BAG\",\"amount\":2}}",  "{\"animation\":null,\"collision_bounds\":null,\"emissive\":null}", "null"}) {
                Files.writeString(objects, "{\"sign\":" + invalid + "}");
                rejects(DataLoader::loadGameData, "Invalid object definition: " + invalid);
            }
        } finally {
            useFixtures();
            Files.deleteIfExists(objects);
            Files.delete(directory);
        }
    }

    private static void playerSchema(PlayerData player) {
        check(player.name().equals("Fixture Player") && player.maxHealth() == 72 && player.moveSpeed() == 2.5f
            && player.animation().speed() == 1.25f && player.animation().soundFrame() == 1 && player.animation().frameInterval() == .12f,
            "Nondefault player stats and animation survive JSON binding");
        check(player.logicalSize().equals(new PlayerData.LogicalSize(62, 63))
            && player.collision().equals(new CollisionData(CollisionData.Shape.ELLIPSE, new CharacterBounds(19, 3, 43, 12), 6f))
            && player.hitboxBounds().equals(new CharacterBounds(15, 8, 46, 52)),
            "Player logical size, feet collision, and hurtbox bind independently");
        check(player.sounds().equals(new PlayerData.Sounds("step.wav", "hurt.wav")), "Player movement and damage sounds bind separately");
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
        var unequipped = new PlayerData.Loadout(null, null, null, null, null, null);
        for (String json : new String[]{"{}", "{\"head\":null,\"body\":null,\"hands\":null,\"feet\":null,\"weapon\":null}"}) {
            check(DataLoader.mapper.readValue(json, PlayerData.Loadout.class).equals(unequipped),
                "omitted and null IDs both leave slots empty");
        }
        check(DataLoader.mapper.readValue("{\"head\":\"cap\",\"body\":\"vest\",\"hands\":\"gloves\",\"weapon\":\"fixture_blade\"}",
            PlayerData.Loadout.class).equals(new PlayerData.Loadout("cap", "vest", "gloves", null, "fixture_blade", null)),
            "omitted feet leave only the feet slot empty");
        check(DataLoader.mapper.readValue("{\"head\":\"cap\",\"body\":\"vest\",\"hands\":\"gloves\",\"feet\":\"boots\"}",
            PlayerData.Loadout.class).equals(new PlayerData.Loadout("cap", "vest", "gloves", "boots", null, null)),
            "omitted weapon leaves only the weapon slot empty");
        for (String invalid : new String[]{"[\" \" ]", "[null]", "[\"fixture_blade\",\"fixture_blade\"]"}) {
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

    private static void weaponSchema(WeaponsData definitions) {
        var weapon = definitions.requireWeapon("fixture_blade");
        var primary = weapon.attackStats().primary();
        var secondary = weapon.attackStats().secondary();
        check(primary.stats().damage().equals(new AttackDamage.Scalar(7))
            && secondary.stats().damage().equals(new AttackDamage.Range(11, 19)), "Scalar and range damage bind to separate slots");
        check(primary.animation().equals(new AttackAnimation(2, AttackAnimKey.SLASH, 1, .12f))
            && secondary.animation().equals(new AttackAnimation(3, AttackAnimKey.LUNGE, 4, .15f)),
            "Each slot binds its own animation and sound cue");
        check(primary.stats().cost() == 0 && primary.stats().hitFrame() == 2 && primary.stats().cooldownSeconds() == .25f
            && secondary.stats().cost() == 1 && secondary.stats().hitFrame() == 5 && secondary.stats().cooldownSeconds() == .375f,
            "Each slot binds its own cost, hit cue, and cooldown");
        check(primary.bounds().equals(new Bounds(BoundsType.RECT, 2, 4, 12, 16,
                new Offset(1, -2), new Offset(2, 3), new Offset(-3, 4), new Offset(4, -1)))
            && secondary.bounds().equals(new Bounds(BoundsType.RECT, 6, 8, 31, 21,
                new Offset(-5, 6), new Offset(6, -7), new Offset(-7, 8), new Offset(8, -9))),
            "Signed offsets and geometry bind by slot and facing");
        var bow = definitions.requireWeapon("fixture_bow").attackStats();
        check(bow.primary().collisionType() == CollisionType.PROJECTILE && bow.primary().animation().animKey() == AttackAnimKey.SHOT
            && bow.secondary().collisionType() == CollisionType.MELEE && bow.secondary().animation().animKey() == AttackAnimKey.TWIRL,
            "Loader binds a projectile primary and a melee twirl independently");
    }

    private static void playerWeaponReferences() throws Exception {
        var player = fixtureTree("data/player.json");
        var file = Files.createTempFile("player-weapon-reference", ".json");
        try {
            useFile(GameConsts.DATA_PATH + "player.json", file);
            var loadout = (ObjectNode) player.required("loadout");
            Files.writeString(file, player.toString());
            var loaded = DataLoader.loadGameData().playerData().loadout();
            check(loaded.weapon().equals("fixture_blade") && loaded.weaponCycle().equals(List.of("fixture_bow", "fixture_blade")),
                "Loader retains the authored cycle order without putting the equipped weapon first");
            loadout.putArray("weapon_cycle").add("fixture_bow").add("missing_cycle_weapon");
            Files.writeString(file, player.toString());
            var failure = rejects(DataLoader::loadGameData, "unknown weapon in the player cycle");
            check(failure.getMessage().contains("Unknown weapon: missing_cycle_weapon"),
                "Player cycle validation identifies the unresolved weapon");
        } finally {
            useFixtures();
            Files.delete(file);
        }
    }

    private static void attackKeys() {
        var atlasKeys = Map.of(AttackAnimKey.SLASH, "slash", AttackAnimKey.JAB, "jab", AttackAnimKey.SHOT, "shot",
            AttackAnimKey.LUNGE, "slash", AttackAnimKey.TWIRL, "jab");
        for (var key : AttackAnimKey.values()) {
            check(key.atlasKey().equals(atlasKeys.get(key)), "atlas mapping for " + key);
            String json = "\"" + key.name().toLowerCase(java.util.Locale.ROOT) + "\"";
            check(DataLoader.mapper.readValue(json, AttackAnimKey.class) == key
                && DataLoader.mapper.writeValueAsString(key).equals(json), "lower-case wire key " + key);
        }
        var fixture = fixtureTree("data/weapons.json");
        for (String slot : List.of("primary", "secondary")) {
            String path = "/weapons/fixture_blade/attack_stats/" + slot + "/animation";
            for (String invalid : List.of("null", "0", "\"0\"", "\"\"", "\"Lunge\"", "\"unknown\"", "\"melee/fixture_blade/slash\"")) {
                var tree = fixture.deepCopy();
                ((ObjectNode) tree.at(path)).set("anim_key", DataLoader.mapper.readTree(invalid));
                rejects(() -> DataLoader.mapper.treeToValue(tree, WeaponsData.class), "invalid " + slot + " anim_key: " + invalid);
            }
            for (boolean legacy : List.of(false, true)) {
                var tree = fixture.deepCopy();
                var fields = (ObjectNode) tree.at(path);
                var old = fields.remove("anim_key");
                if (legacy) { fields.set("atlas_key", old); }
                rejects(() -> DataLoader.mapper.treeToValue(tree, WeaponsData.class), "missing " + slot + " anim_key (legacy=" + legacy + ")");
            }
        }
    }

    private static void invalidMetadata(WeaponsData definitions) {
        var sword = definitions.requireWeapon("fixture_blade");
        var primary = sword.attackStats().primary();
        rejects(() -> definitions.requireWeapon("missing"), "unknown weapon");
        rejects(() -> new WeaponsData(null, definitions.weapons()), "missing shared atlas");
        rejects(() -> new WeaponsData("NONE", definitions.weapons()), "disabled shared atlas");
        rejects(() -> new WeaponsData(definitions.atlas(), Map.of("fixture_blade", sword)), "missing unarmed definition");
        var bad = new HashMap<>(definitions.weapons());
        bad.put("", sword);
        rejects(() -> new WeaponsData(definitions.atlas(), bad), "empty weapon ID");
        var disabled = new HashMap<>(definitions.weapons());
        disabled.put("fixture_blade", new WeaponData(sword.attackStats(), "NONE"));
        rejects(() -> new WeaponsData(definitions.atlas(), disabled), "armed weapon without visual prefix");
        rejectsNull(() -> new WeaponData(null, sword.atlasKey()), "attack_stats");
        rejects(() -> new WeaponData(sword.attackStats(), " "), "blank weapon prefix");
        rejectsNull(() -> new AttackStats(null, primary), "attack_stats.primary");
        rejectsNull(() -> new AttackStats(primary, null), "attack_stats.secondary");
        rejectsNull(() -> new AttackData(primary.animation(), primary.bounds(), primary.stats(), primary.sounds(), null), "collision_type");
        rejects(() -> new AttackData(new AttackAnimation(1, AttackAnimKey.SHOT, 0, .125f),
            primary.bounds(), primary.stats(), primary.sounds(), CollisionType.MELEE), "shot marked melee");
        rejects(() -> new AttackData(primary.animation(), primary.bounds(), primary.stats(), primary.sounds(), CollisionType.PROJECTILE),
            "slash marked projectile");
        var damage = primary.stats().damage();
        rejects(() -> new Stats(damage, 0, -1, .25f), "negative hit frame");
        rejects(() -> new Stats(damage, -1, 0, .25f), "negative cost");
        String fields = "\"cost\":0,\"hit_frame\":0,\"cooldown_seconds\":0.25}";
        rejects(() -> DataLoader.mapper.readValue("{" + fields, Stats.class), "missing damage");
        for (String invalid : List.of("0", "-1", "null", "\"10\"", "[]", "{}", "{\"min\":1}", "{\"max\":1}",
            "{\"min\":null,\"max\":2}", "{\"min\":1,\"max\":null}", "{\"min\":5,\"max\":4}", "{\"min\":0,\"max\":1}",
            "{\"min\":1.5,\"max\":3}", "{\"min\":1,\"max\":\"2\"}", "{\"min\":1,\"max\":2147483648}", "{\"min\":1,\"max\":2,\"extra\":1}")) {
            rejects(() -> DataLoader.mapper.readValue("{\"damage\":" + invalid + "," + fields, Stats.class), "invalid damage: " + invalid);
        }
        for (float invalid : new float[]{0f, -.25f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            rejects(() -> new Stats(damage, 0, 0, invalid), "invalid cooldown: " + invalid);
        }
        rejects(() -> new AnimationData(0, "jab", 0, .125f), "zero speed");
        rejects(() -> new AnimationData(1, "jab", -1, .125f), "negative sound frame");
        rejects(() -> new AnimationData(1, "fixture_actor", 0, 0), "zero base interval");
        rejects(() -> new AnimationData(1, "jab", 0, Float.NaN), "nonfinite interval");
        rejects(() -> new Sounds("", "hit.wav"), "empty attack sound");
        for (String invalid : new String[]{" ", "../bad.wav", "/bad.wav", "effects//bad.wav"}) {
            rejects(() -> new Sounds(invalid, "cut.wav"), "invalid attack sound path");
            rejects(() -> new Sounds("slash.wav", invalid), "invalid hit sound path");
        }
        var fixture = fixtureTree("data/weapons.json");
        String secondaryPath = "/weapons/fixture_blade/attack_stats/secondary";
        for (String field : new String[]{"attack", "hit"}) {
            for (String invalid : new String[]{"null", "\" \"", "{}", "\"../bad.wav\""}) {
                var tree = fixture.deepCopy();
                var sounds = (ObjectNode) tree.at(secondaryPath + "/sounds");
                sounds.set(field, DataLoader.mapper.readTree(invalid));
                rejects(() -> DataLoader.mapper.treeToValue(tree, WeaponsData.class), "invalid secondary " + field);
            }
        }
        var missingSounds = fixture.deepCopy();
        ((ObjectNode) missingSounds.at(secondaryPath)).remove("sounds");
        rejects(() -> DataLoader.mapper.treeToValue(missingSounds, WeaponsData.class), "missing secondary sounds");
        rejects(() -> new Bounds(BoundsType.RECT, 1, 0, 1, 2, ZERO, ZERO, ZERO, ZERO), "collapsed bounds");
        rejects(() -> new Bounds(BoundsType.RECT, Float.NaN, 0, 2, 2, ZERO, ZERO, ZERO, ZERO), "nonfinite bounds");
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
        var attack = (ObjectNode) fixtureTree("data/weapons.json").at("/weapons/fixture_blade/attack_stats/primary");
        attack.remove("collision_type");
        rejects(() -> DataLoader.mapper.treeToValue(attack, AttackData.class), "missing JSON collision_type");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"cooldown_seconds\":0.25}", Stats.class), "missing hit frame");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":null,\"cooldown_seconds\":0.25}", Stats.class), "null hit frame");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2}", Stats.class), "missing cooldown");
        rejects(() -> DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2,\"cooldown_seconds\":null}", Stats.class), "null cooldown");
        rejects(() -> DataLoader.mapper.readValue("{\"speed\":1,\"anim_key\":\"jab\",\"frame_interval\":0.1}",
            AttackAnimation.class), "missing attack sound frame");
        var bounds = (ObjectNode) attack.required("bounds");
        bounds.remove("x1");
        rejects(() -> DataLoader.mapper.treeToValue(bounds, Bounds.class), "missing bounds coordinate");
    }

    private static void assetIds(WeaponsData definitions) {
        var sounds = WeaponAssets.of("fixture_blade", definitions);
        check(sounds.primary().equals(new WeaponAssets.AttackSounds(
                new Asset.Sound("weapon/fixture_blade/primary/attack", "sounds/slash.wav"),
                new Asset.Sound("weapon/fixture_blade/primary/hit", "sounds/cut.wav")))
            && sounds.secondary().equals(new WeaponAssets.AttackSounds(
                new Asset.Sound("weapon/fixture_blade/secondary/attack", "sounds/lunge.wav"),
                new Asset.Sound("weapon/fixture_blade/secondary/hit", "sounds/thud.wav"))),
            "Attack and hit sounds keep distinct paths and IDs for each slot");
        rejects(() -> WeaponAssets.of("missing", definitions), "unknown weapon assets");
        var weaponAtlas = CharacterAssets.all(definitions.atlas()).stream()
            .filter(atlas -> atlas.id().equals("weapons")).findFirst().orElseThrow();
        check(weaponAtlas.path().equals("atlases/fixture_weapons.atlas"),
            "Weapon atlas keeps its logical ID and uses the configured filename");
    }

    private static void debugLayerLaunchModes() throws Exception {
        var fixture = Files.createTempFile("debug-layer-settings", ".json");
        var previous = System.clearProperty("luteDebugLayers");
        try {
            var settings = fixtureTree("game.settings.json");
            var layers = settings.putObject("debugLayers");
            layers.put("physics", false).put("triggers", true).put("rawLightMap", true).put("lightingOutlines", true);
            useFile("game.settings.json", fixture);
            for (boolean validate : List.of(true, false)) {
                layers.put("assetValidation", validate);
                Files.writeString(fixture, settings.toString());
                System.clearProperty("luteDebugLayers");
                GameConsts.loadSettings();
                check(GameConsts.DEBUG_LAYERS.equals(new GameConsts.DebugLayers(false, true, true, true, validate)),
                    "absent mode uses JSON");
                System.setProperty("luteDebugLayers", "off");
                GameConsts.loadSettings();
                check(GameConsts.DEBUG_LAYERS.equals(new GameConsts.DebugLayers(false, false, false, false, validate)),
                    "off disables visual layers only");
                System.setProperty("luteDebugLayers", "on");
                GameConsts.loadSettings();
                check(GameConsts.DEBUG_LAYERS.equals(new GameConsts.DebugLayers(true, true, false, false, validate)),
                    "on enables physics and triggers without changing asset validation");
            }

            var width = GameConsts.VIEW_WIDTH;
            settings.put("viewWidth", width + 1);
            Files.writeString(fixture, settings.toString());
            System.setProperty("luteDebugLayers", "invalid");
            rejectsSettings("luteDebugLayers must be on or off");
            check(GameConsts.VIEW_WIDTH == width, "invalid mode publishes nothing");
            System.setProperty("luteDebugLayers", "on");
            layers.remove("assetValidation");
            Files.writeString(fixture, settings.toString());
            rejectsSettings("debugLayers requires exactly");
        } finally {
            if (previous == null) System.clearProperty("luteDebugLayers");
            else System.setProperty("luteDebugLayers", previous);
            Files.deleteIfExists(fixture);
            useFixtures();
            GameConsts.loadSettings();
        }
    }

    private static void settingsAtomicity() throws Exception {
        var fixture = Files.createTempFile("weapon-settings", ".json");
        try {
            var configured = fixtureTree("game.settings.json");
            configured.put("soundPath", "effects/").put("startupMap", "fixture_start").put("projectPath", "other-fixture.tiled-project");
            Files.writeString(fixture, configured.toString());
            useFile("game.settings.json", fixture);
            GameConsts.loadSettings();
            check(Asset.Sound.of("test", "swing.wav").path().equals("effects/swing.wav"), "configured sound prefix");
            check(Asset.Music.of("test", "town.ogg").path().equals("music/town.ogg"), "independent music prefix");
            check(GameConsts.STARTUP_MAP.equals("fixture_start") && GameConsts.PROJECT_PATH.equals("other-fixture.tiled-project"),
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
            rejectsSettings("viewWidth must be finite and positive");
            check(GameConsts.VIEW_WIDTH == width && GameConsts.SOUND_PATH.equals("effects/"), "invalid settings apply nothing");
            var ambient = GameConsts.NIGHT_AMBIENT;
            var debug = GameConsts.DEBUG_LAYERS;
            String changed = configured.deepCopy().put("viewWidth", width + 1).toString();
            for (var field : List.of("nightAmbient", "brightness", "debugLayers", "triggers")) {
                String key = "\"" + field + "\":";
                Files.writeString(fixture, changed.replace(key, key + "null," + key));
                String expected = switch (field) {
                    case "nightAmbient", "debugLayers" -> "Duplicate " + field + " setting";
                    default -> "Duplicate property \"" + field + "\"";
                };
                rejectsSettings(expected);
                check(GameConsts.VIEW_WIDTH == width && GameConsts.NIGHT_AMBIENT == ambient && GameConsts.DEBUG_LAYERS == debug,
                    "invalid settings publish nothing");
            }
        } finally {
            Files.deleteIfExists(fixture);
            useFixtures();
            GameConsts.loadSettings();
        }
    }

    private static void useFixtures() { useResolver(DataLoaderTest::fixture); }

    private static FileHandle fixture(String path) {
        String resource = "/data-loader/" + path;
        return new FileHandle(resource, com.badlogic.gdx.Files.FileType.Classpath) {
            // Resolve test resources with the test class loader, not LibGDX's.
            @Override public InputStream read() {
                var stream = DataLoaderTest.class.getResourceAsStream(resource);
                if (stream == null) { throw new AssertionError("Missing test fixture: " + resource); }
                return stream;
            }
        };
    }

    private static ObjectNode fixtureTree(String path) {
        return (ObjectNode) DataLoader.mapper.readTree(fixture(path).readString("UTF-8"));
    }

    private static void useFile(String target, Path file) {
        useResolver(path -> path.equals(target) ? new FileHandle(file.toFile()) : fixture(path));
    }

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
    private static RuntimeException rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException | JacksonException expected) { return expected; }
        throw new AssertionError("Accepted " + message);
    }

    private static void rejectsNull(Runnable action, String field) {
        try {
            action.run();
        } catch (NullPointerException expected) {
            check(field.equals(expected.getMessage()), "Expected required field rejection for " + field);
            return;
        }
        throw new AssertionError("Accepted null " + field);
    }

    private static void rejectsSettings(String message) {
        try {
            GameConsts.loadSettings();
        } catch (GdxRuntimeException expected) {
            var cause = expected.getCause();
            if ((cause instanceof IllegalArgumentException || cause instanceof JacksonException)
                && cause.getMessage().contains(message)) { return; }
            throw new AssertionError("Expected settings rejection: " + message, expected);
        }
        throw new AssertionError("Loaded settings without rejecting: " + message);
    }

    private static void rejectsMissingFile(Path file) {
        try {
            DataLoader.loadGameData();
        } catch (GdxRuntimeException expected) {
            var cause = expected.getCause();
            if (cause instanceof FileNotFoundException && cause.getMessage().contains(file.toString())) { return; }
            throw new AssertionError("Expected missing file rejection for " + file, expected);
        }
        throw new AssertionError("Loaded game data without " + file);
    }
}
