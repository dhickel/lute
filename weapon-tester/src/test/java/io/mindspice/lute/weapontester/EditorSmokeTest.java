package io.mindspice.lute.weapontester;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static io.mindspice.lute.weapontester.WeaponFile.Slot.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("display")
class EditorSmokeTest {
    @BeforeAll static void startFx() { Platform.startup(() -> Platform.setImplicitExit(false)); }
    @AfterAll static void stopFx() { Platform.exit(); }
    @TempDir Path temp;

    @Test void previewsCurrentRawCatalogAndSavesOnlySelectedBoundsOnCopy() throws Exception {
        Path input = Path.of(System.getProperty("tester.rawInput"));
        try (var files = Files.walk(input.resolve("sprites"))) {
            for (Path path : files.toList()) {
                Path dest = temp.resolve(input.relativize(path));
                if (Files.isDirectory(path)) { Files.createDirectories(dest); }
                else if (path.toString().endsWith(".png")) { Files.copy(path, dest); }
            }
        }
        Files.copy(input.resolve("game.settings.json"), temp.resolve("game.settings.json"));
        Files.createDirectories(temp.resolve("data"));
        Files.copy(input.resolve("data/player.json"), temp.resolve("data/player.json"));
        var json = Files.copy(input.resolve("data/weapons.json"), temp.resolve("data/weapons.json"));
        var baseline = WeaponFile.load(json);
        String before = Files.readString(json);
        var test = new FutureTask<Void>(() -> {
            var stage = new Stage();
            try {
                new WeaponTester.Editor(stage, temp).open();
                var scene = stage.getScene();
                @SuppressWarnings("unchecked") var weapon = (ComboBox<String>) scene.lookup("#weapon");
                @SuppressWarnings("unchecked") var slot = (ComboBox<WeaponFile.Slot>) scene.lookup("#slot");
                var error = (Label) scene.lookup("#preview-error");
                var frame = (Slider) scene.lookup("#frame");
                var play = (ToggleButton) scene.lookup("#play");
                var status = (Label) scene.lookup("#edit-status");
                var save = (Button) scene.lookup("#save");
                var x2 = (TextField) scene.lookup("#x2");
                for (String id : baseline.ids()) {
                    weapon.setValue(id);
                    assertEquals("", error.getText(), id);
                    assertEquals(baseline.secondaryOnly(id), slot.isDisabled(), id);
                    if (slot.isDisabled()) {
                        assertEquals(SECONDARY, slot.getValue());
                        assertEquals(1, slot.getItems().size());
                    } else {
                        slot.setValue(PRIMARY);
                        assertEquals("", error.getText(), id + "/primary");
                        slot.setValue(SECONDARY);
                        assertEquals("", error.getText(), id + "/secondary");
                    }
                }
                weapon.setValue("basic_sword");
                slot.setValue(SECONDARY);
                frame.setValue(2);
                assertFalse(play.isSelected());
                assertEquals(2.0, frame.getValue());
                scene.getRoot().layout();
                for (var direction : Geometry.Direction.values()) {
                    var canvas = (Canvas) scene.lookup("#preview-" + direction.key());
                    assertTrue(canvas.getWidth() > 100 && canvas.getHeight() > 100);
                }
                var right = (Canvas) scene.lookup("#preview-right");
                var up = (Canvas) scene.lookup("#preview-up");
                int overlayCount = 0;
                for (var node : scene.getRoot().lookupAll(".check-box")) {
                    var overlay = (CheckBox) node;
                    var withOverlay = pixels(right);
                    overlay.fire();
                    assertFalse(java.util.Arrays.equals(withOverlay, pixels(right)), overlay.getText());
                    overlay.fire();
                    overlayCount++;
                }
                assertEquals(3, overlayCount);
                var offset = (TextField) scene.lookup("#offset_right-x");
                String originalOffset = offset.getText();
                var beforeRight = pixels(right);
                var beforeUp = pixels(up);
                offset.setText(Float.toString(Float.parseFloat(originalOffset) + 8));
                assertFalse(java.util.Arrays.equals(beforeRight, pixels(right)), "Right offset updates the canvas immediately");
                assertArrayEquals(beforeUp, pixels(up), "Right offset does not move the up-facing overlay");
                offset.setText(originalOffset);
                x2.setText("NaN");
                assertTrue(save.isDisabled());
                assertTrue(status.getText().contains("finite"));
                x2.setText("77.25");
                assertFalse(save.isDisabled());
                save.fire();
                assertTrue(status.getText().startsWith("Saved"), status.getText());
                var edited = WeaponFile.load(json);
                for (String id : edited.ids()) {
                    for (var attackSlot : WeaponFile.Slot.values()) {
                        if (attackSlot == PRIMARY && edited.secondaryOnly(id)) { continue; }
                        var expected = baseline.form(id, attackSlot);
                        if (id.equals("basic_sword") && attackSlot == SECONDARY) { expected.put("x2", "77.25"); }
                        assertEquals(expected, edited.form(id, attackSlot));
                    }
                }
                String externallyEdited = Files.readString(json) + "\n";
                Files.writeString(json, externallyEdited);
                x2.setText("80");
                save.fire();
                assertTrue(status.getText().contains("changed on disk"), status.getText());
                assertEquals(externallyEdited, Files.readString(json));
                assertEquals(before, Files.readString(input.resolve("data/weapons.json")));

                x2.setText("77.25"); // Return to the saved draft so selection can change without a dialog.
                weapon.setValue("unarmed");
                assertEquals("", error.getText());
                var sprites = temp.resolve("sprites");
                var sword = sprites.resolve("weapons/melee/basic_sword");
                var head = sprites.resolve("equipment/basic/head");
                for (var direction : Geometry.Direction.values()) {
                    String frameZero = "jab_" + direction.key() + "_00.png";
                    marker(sword.resolve(frameZero), 0xffff0000);
                    marker(head.resolve(frameZero), 0xff0000ff);
                }
                var player = RawPreview.player(temp);
                var clip = RawPreview.load(temp, player, "melee/basic_sword", baseline.attack("basic_sword", SECONDARY));
                for (var direction : Geometry.Direction.values()) {
                    var layers = clip.poses().get(direction).getFirst().layers();
                    assertEquals(6, layers.size());
                    assertEquals(direction == Geometry.Direction.UP ? 0xff0000ff : 0xffff0000,
                        layers.getLast().getPixelReader().getArgb(64, 64));
                    if (direction == Geometry.Direction.UP) {
                        assertEquals(0xffff0000, layers.getFirst().getPixelReader().getArgb(64, 64));
                    }
                }
                for (var node : scene.getRoot().lookupAll(".check-box")) { ((CheckBox) node).setSelected(false); }
                weapon.setValue("basic_sword");
                assertEquals("", error.getText());
                play.setSelected(false);
                for (var direction : Geometry.Direction.values()) {
                    var canvas = (Canvas) scene.lookup("#preview-" + direction.key());
                    var image = canvas.snapshot(null, null);
                    int center = image.getPixelReader().getArgb((int) image.getWidth() / 2, (int) image.getHeight() / 2);
                    assertEquals(direction == Geometry.Direction.UP ? 0xff0000ff : 0xffff0000, center, direction.name());
                }
                weapon.setValue("unarmed");

                // Remove the last frame across every layer and facing: still contiguous and mutually aligned,
                // but not playable by the runtime compositor, which requires exactly eight frames.
                for (var dir : java.util.List.of(sword, sprites.resolve("character/player"), head,
                    sprites.resolve("equipment/basic/body"), sprites.resolve("equipment/basic/feet"),
                    sprites.resolve("equipment/basic/hands"))) {
                    for (var direction : Geometry.Direction.values()) {
                        Files.delete(dir.resolve("jab_" + direction.key() + "_07.png"));
                    }
                }
                weapon.setValue("basic_sword");
                assertTrue(error.getText().contains("Expected eight frames"), error.getText());
                assertTrue(play.isDisabled());
                x2.setText("82");
                assertTrue(save.isDisabled());
                save.fire();
                assertEquals(externallyEdited, Files.readString(json));
            } finally {
                stage.hide();
            }
            return null;
        });
        Platform.runLater(test);
        test.get(40, TimeUnit.SECONDS);
    }

    @Test void playbackAdvancesOnJavaFxPulses() throws Exception {
        var advanced = new CountDownLatch(1);
        var setup = new FutureTask<>(() -> {
            var stage = new Stage();
            new WeaponTester.Editor(stage, Path.of(System.getProperty("tester.rawInput"))).open();
            var scene = stage.getScene();
            var frame = (Slider) scene.lookup("#frame");
            frame.valueProperty().addListener((obs, old, current) -> {
                if (current.intValue() > 0) { advanced.countDown(); }
            });
            return stage;
        });
        Platform.runLater(setup);
        var stage = setup.get(10, TimeUnit.SECONDS);
        try {
            assertTrue(advanced.await(5, TimeUnit.SECONDS), "Playing clip did not advance from frame zero");
        } finally {
            var close = new FutureTask<>(stage::hide, null);
            Platform.runLater(close);
            close.get(10, TimeUnit.SECONDS);
        }
    }

    private static int[] pixels(Canvas canvas) {
        var image = canvas.snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        var result = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(),
            result, 0, width);
        return result;
    }

    private static void marker(Path path, int pixel) throws java.io.IOException {
        var image = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(64, 64, pixel);
        javax.imageio.ImageIO.write(image, "PNG", path.toFile());
    }
}
