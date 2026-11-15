package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.lang.reflect.Proxy;
import java.nio.file.Files;

public final class DataLoaderTest {
    public static void main(String[] args) throws Exception {
        var previousFiles = Gdx.files;
        try {
            useAssets();
            GameConsts.loadSettings();
            check(GameConsts.VIEW_WIDTH == 21.3f && GameConsts.UNIT_SCALE == 1f / 32f, "settings constants");
            var player = DataLoader.parsePlayerData();
            check(player.maxHealth() == 100 && player.moveSpeed() == 4, "player stats");
            var definitions = DataLoader.parseWeaponData();
            var weapon = definitions.requireWeapon(player.equippedWeapon());
            check("NONE".equals(definitions.atlas()) && "NONE".equals(weapon.animation().atlasKey()), "disabled atlas");
            var authored = DataLoader.mapper.readTree(Files.readString(java.nio.file.Path.of("assets/data/weapons.json")))
                .get("weapons").get(player.equippedWeapon());
            check(weapon.stats().damage() == authored.get("stats").get("damage").asInt()
                && weapon.stats().cost() == authored.get("stats").get("cost").asInt()
                && weapon.stats().hitFrame() == authored.get("stats").get("hit_frame").asInt(), "authored stats");
            check(weapon.animation().speed() == (float) authored.get("animation").get("speed").asDouble()
                && weapon.animation().soundFrame() == authored.get("animation").get("sound_frame").asInt(), "authored animation timing");
            check(weapon.bounds().type() == WeaponsData.WeaponData.BoundsType.SEMICIRCLE, "bounds type");
            var selected = WeaponAssets.of("sword", definitions);
            check(selected.all().size() == 2, "only selected weapon assets");
            check(selected.attack().path().equals("audio/sword_swing.wav"), "attack path");
            check(selected.hit().path().equals("audio/sword_hit.wav"), "hit path");
            check(!selected.attack().id().equals(selected.hit().id()), "distinct runtime keys");
            rejects(() -> WeaponAssets.of("missing", definitions), "unknown weapon");
            rejects(() -> WeaponAssets.of("sword", new WeaponsData("weapons.atlas", definitions.weapons())), "unsupported atlas");
            var visual = new WeaponsData.WeaponData(weapon.stats(),
                new WeaponsData.WeaponData.WeaponAnimation(1, "sword", 3), weapon.sounds(), weapon.bounds());
            rejects(() -> new WeaponsData(null, java.util.Map.of("sword", visual)).requireWeapon("sword"), "unsupported region");
            check("NONE".equals(new WeaponsData.WeaponData.WeaponAnimation(1, null, 0).atlasKey()), "default region");
            var parsedStats = DataLoader.mapper.readValue("{\"damage\":1,\"cost\":0,\"hit_frame\":2}", WeaponsData.WeaponData.Stats.class);
            var parsedAnimation = DataLoader.mapper.readValue("{\"speed\":1,\"sound_frame\":3}", WeaponsData.WeaponData.WeaponAnimation.class);
            check(parsedStats.hitFrame() == 2 && parsedAnimation.soundFrame() == 3, "zero-based frame JSON mapping");
            rejects(() -> new WeaponsData.WeaponData.Stats(10, 0, -1), "negative hit frame");
            rejects(() -> new WeaponsData.WeaponData.WeaponAnimation(0, null, 0), "zero speed");
            rejects(() -> new WeaponsData.WeaponData.Sounds("", "hit.wav"), "empty sound");

            var fixture = Files.createTempFile("weapon-settings", ".json");
            try {
                var json = Files.readString(java.nio.file.Path.of("assets/data/weapons.json"));
                // An unequipped weapon's files must not be queued or resolved.
                var two = new WeaponsData(null, java.util.Map.of("sword", weapon, "unused", weapon));
                check(WeaponAssets.of("sword", two).all().stream().allMatch(a -> a.id().startsWith("weapon/sword/")), "selection");
                Files.writeString(fixture, json.replace("\"atlas\": \"NONE\",", ""));
                useFile(fixture);
                check("NONE".equals(DataLoader.parseWeaponData().atlas()), "default shared atlas");
                var settings = Files.readString(java.nio.file.Path.of("assets/game.settings.json"));
                Files.writeString(fixture, settings.replace("\"soundPath\": \"audio/\"", "\"soundPath\": \"effects/\""));
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
            System.out.println("DataLoaderTest: passed");
        } finally { Gdx.files = previousFiles; }
    }

    private static void useAssets() { useResolver(path -> new FileHandle("assets/" + path)); }
    private static void useFile(java.nio.file.Path file) { useResolver(path -> new FileHandle(file.toFile())); }
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
