package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DataLoaderTest {
    public static void main(String[] args) throws Exception {
        com.badlogic.gdx.Files previousFiles = Gdx.files;
        try {
            Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Files.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("internal")) {
                        return new FileHandle("assets/" + arguments[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

            PlayerData player = DataLoader.parsePlayerData();
            check("player".equals(player.name()), "player name");
            check(player.maxHealth() == 100, "player max_health");
            check(player.moveSpeed() == 4.0f, "player move_speed");
            check("sword".equals(player.equippedWeapon()), "player equipped_weapon");

            WeaponsData weapons = DataLoader.parseWeaponData();
            check(weapons.weapons() != null, "weapons map");
            WeaponsData.WeaponData weapon = weapons.weapons().get("sword");
            check(weapon != null, "sword weapon");
            check(weapon.damage() == 10, "weapon damage");
            check(weapon.attackSpeed() == 1.0f, "weapon attack speed");
            check(weapon.hitDelay() == 0.1f, "weapon hit delay");
            check(weapon.boundsType() == WeaponsData.WeaponData.BoundsType.SEMICIRCLE, "weapon bounds_type");
            check(weapon.bounds() != null, "weapon bounds");
            check(weapon.bounds().x1() == 32 && weapon.bounds().y1() == 0
                && weapon.bounds().x2() == 64 && weapon.bounds().y2() == 64, "weapon bounds values");
            Path fixture = Files.createTempFile("weapons", ".json");
            try {
                Files.writeString(fixture, "{\"sword\":{\"damage\":10},\"axe\":{\"damage\":20}}");
                Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("internal")) {
                            return new FileHandle(fixture.toFile());
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
                var multipleWeapons = DataLoader.parseWeaponData().weapons();
                check(multipleWeapons.size() == 2, "multiple weapon count");
                check(multipleWeapons.get("sword").damage() == 10, "sword map entry");
                check(multipleWeapons.get("axe").damage() == 20, "axe map entry");
            } finally {
                Files.deleteIfExists(fixture);
            }
            System.out.println("DataLoaderTest: passed");
        } finally {
            Gdx.files = previousFiles;
        }
    }

    private static void check(boolean condition, String field) {
        if (!condition) {
            throw new AssertionError("Incorrect " + field);
        }
    }
}
