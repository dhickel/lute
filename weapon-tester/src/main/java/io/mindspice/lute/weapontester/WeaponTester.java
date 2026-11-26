package io.mindspice.lute.weapontester;

import com.badlogic.gdx.math.Vector2;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.mindspice.lute.weapontester.Geometry.*;
import static io.mindspice.lute.weapontester.WeaponFile.*;

public final class WeaponTester {
    public static void main(String[] args) { Application.launch(Desktop.class, args); }

    public static final class Desktop extends Application {
        @Override public void start(Stage stage) {
            var args = getParameters().getNamed();
            Path raw = Path.of(args.getOrDefault("raw-dir", "raw_assets")).toAbsolutePath().normalize();
            new Editor(stage, raw).open();
        }
    }

    static final class Editor {
        private static final Color SENSOR = Color.web("#ffbb58");
        private static final Color HURT = Color.web("#ed82ff");
        private static final Color SOLID = Color.web("#53e5dc");

        private final Stage stage;
        private final Path raw;
        private final ComboBox<String> weapon = new ComboBox<>();
        private final ComboBox<Slot> slot = new ComboBox<>();
        private final ComboBox<AttackShape> shape = new ComboBox<>();
        private final Map<String, TextField> fields = new LinkedHashMap<>();
        private final Map<Direction, Canvas> views = new EnumMap<>(Direction.class);
        private final CheckBox sensor = overlay("Weapon sensor", SENSOR);
        private final CheckBox hurt = overlay("Player hurtbox", HURT);
        private final CheckBox solid = overlay("Player solid", SOLID);
        private final ToggleButton play = new ToggleButton("Play");
        private final Slider scrub = new Slider(0, 1, 0);
        private final Button previous = new Button("◀ Frame");
        private final Button next = new Button("Frame ▶");
        private final Button save = new Button("Save bounds");
        private final Label frameLabel = new Label();
        private final Label selectionNote = new Label();
        private final Label previewError = new Label();
        private final Label editStatus = new Label();

        private WeaponFile file;
        private RawPreview.Player player;
        private RawPreview.Clip clip;
        private Map<Direction, Vector2[]> sensorPolygons;
        private Map<String, String> original;
        private String selectedId;
        private Slot selectedSlot;
        private boolean updating;
        private boolean movingSlider;
        private int frame;
        private double seconds;
        private long lastPulse;

        private final AnimationTimer timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (clip != null && play.isSelected() && lastPulse != 0) {
                    seconds = (seconds + (now - lastPulse) / 1e9) % (clip.frames() * clip.attack().frameSeconds());
                    showFrame(clip.frameAt(seconds));
                }
                lastPulse = now;
            }
        };

        Editor(Stage stage, Path raw) { this.stage = stage; this.raw = raw; }

        void open() {
            weapon.setId("weapon");
            slot.setId("slot");
            save.setId("save");
            scrub.setId("frame");
            editStatus.setId("edit-status");
            previewError.setId("preview-error");
            play.setId("play");
            weapon.setPrefWidth(190);
            slot.setPrefWidth(115);
            var reload = new Button("Reload raw data");
            reload.setOnAction(e -> { if (confirmDiscard()) { reload(); } });
            var toolbar = new HBox(10, new Label("Weapon"), weapon, new Label("Slot"), slot, reload);
            toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            selectionNote.setWrapText(true);
            previewError.setWrapText(true);
            previewError.setStyle("-fx-text-fill: #9e231b;");
            previewError.managedProperty().bind(previewError.textProperty().isNotEmpty());

            var root = new BorderPane();
            root.setPadding(new Insets(12));
            root.setTop(new VBox(6, toolbar, selectionNote, previewError));
            root.setCenter(previews());
            root.setRight(formPane());
            var playback = new HBox(8, play, previous, next, scrub, frameLabel);
            playback.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox.setHgrow(scrub, Priority.ALWAYS);
            root.setBottom(new VBox(8, new HBox(14, sensor, hurt, solid), playback));

            weapon.valueProperty().addListener((obs, old, value) -> changeSelection());
            slot.valueProperty().addListener((obs, old, value) -> changeSelection());
            shape.valueProperty().addListener((obs, old, value) -> updateBounds());
            for (var checkbox : new CheckBox[]{sensor, hurt, solid}) {
                checkbox.selectedProperty().addListener((obs, old, value) -> draw());
            }
            scrub.setBlockIncrement(1);
            scrub.setMajorTickUnit(1);
            scrub.setMinorTickCount(0);
            scrub.setSnapToTicks(true);
            scrub.valueProperty().addListener((obs, old, value) -> {
                if (!movingSlider && clip != null) { seek((int) Math.round(value.doubleValue())); }
            });
            play.selectedProperty().addListener((obs, old, value) -> {
                play.setText(value ? "Pause" : "Play");
                lastPulse = 0;
            });
            previous.setOnAction(e -> seek(frame - 1));
            next.setOnAction(e -> seek(frame + 1));
            save.setOnAction(e -> save());
            stage.setTitle("Lute · Weapon Bounds Tester");
            stage.setScene(new Scene(root, 1040, 790));
            stage.setMinWidth(720);
            stage.setMinHeight(570);
            stage.setOnCloseRequest(e -> { if (!confirmDiscard()) { e.consume(); } });
            stage.setOnHidden(e -> timer.stop());
            reload();
            stage.show();
            timer.start();
        }

        private GridPane previews() {
            var grid = new GridPane(8, 8);
            grid.setPadding(new Insets(8, 10, 8, 0));
            for (int i = 0; i < 2; i++) {
                var col = new ColumnConstraints();
                col.setPercentWidth(50);
                grid.getColumnConstraints().add(col);
                var row = new RowConstraints();
                row.setPercentHeight(50);
                grid.getRowConstraints().add(row);
            }
            for (var direction : Direction.values()) {
                var canvas = new Canvas();
                canvas.setId("preview-" + direction.key());
                var area = new StackPane(canvas);
                area.setMinSize(0, 0);
                canvas.widthProperty().bind(area.widthProperty());
                canvas.heightProperty().bind(area.heightProperty());
                views.put(direction, canvas);
                canvas.widthProperty().addListener((obs, old, value) -> draw());
                canvas.heightProperty().addListener((obs, old, value) -> draw());
                var tile = new VBox(3, new Label(direction.name()), area);
                VBox.setVgrow(area, Priority.ALWAYS);
                grid.add(tile, direction.ordinal() % 2, direction.ordinal() / 2);
            }
            return grid;
        }

        private ScrollPane formPane() {
            var form = new GridPane(6, 5);
            form.addRow(0, new Label("Shape"), shape);
            shape.getItems().setAll(AttackShape.values());
            for (int i = 0; i < COORDS.size(); i++) {
                String key = COORDS.get(i);
                form.addRow(i + 1, new Label(key), field(key));
            }
            var offsets = new GridPane(6, 5);
            offsets.addRow(0, new Label("Offset"), new Label("X"), new Label("Y"));
            for (var direction : Direction.values()) {
                String key = offset(direction);
                offsets.addRow(direction.ordinal() + 1, new Label(direction.name()), field(key + "/x"), field(key + "/y"));
            }
            var units = new Label("Logical pixels · +X right, +Y up\nOffsets apply after facing rotation.");
            var target = new Label(raw.resolve("data/weapons.json").toString());
            target.setWrapText(true);
            editStatus.setWrapText(true);
            var content = new VBox(10, new Label("Attack bounds"), units, form, offsets, save, editStatus, target);
            content.setPadding(new Insets(8));
            var scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            scroll.setPrefWidth(292);
            return scroll;
        }

        private TextField field(String key) {
            var input = new TextField();
            input.setId(key.replace('/', '-'));
            input.setPrefColumnCount(7);
            input.textProperty().addListener((obs, old, value) -> updateBounds());
            fields.put(key, input);
            return input;
        }

        private void reload() {
            updating = true;
            file = null;
            player = null;
            try {
                file = WeaponFile.load(raw.resolve("data/weapons.json"));
                player = RawPreview.player(raw);
                String id = selectedId == null ? player.defaultWeapon() : selectedId;
                weapon.getItems().setAll(file.ids());
                weapon.setValue(file.ids().contains(id) ? id : file.ids().getFirst());
            } catch (Exception ex) {
                file = null;
                weapon.getItems().clear();
                slot.getItems().clear();
                selectionNote.setText("Unable to load raw asset data from " + raw);
                previewError.setText(message(ex));
            } finally {
                updating = false;
            }
            if (file != null) {
                select(weapon.getValue(), selectedSlot);
            } else {
                clearPreview();
                original = null;
                editStatus.setText("");
                updateBounds();
                draw();
            }
        }

        private void changeSelection() {
            if (updating || file == null || weapon.getValue() == null) { return; }
            if (!confirmDiscard()) {
                updating = true;
                weapon.setValue(selectedId);
                slot.setValue(selectedSlot);
                updating = false;
                return;
            }
            select(weapon.getValue(), slot.getValue());
        }

        private void select(String id, Slot requested) {
            updating = true;
            selectedId = id;
            selectedSlot = null;
            original = null;
            clearPreview();
            selectionNote.setText("");
            previewError.setText("");
            editStatus.setText("");
            try {
                boolean bow = file.secondaryOnly(id);
                if (bow) { slot.getItems().setAll(Slot.SECONDARY); }
                else { slot.getItems().setAll(Slot.values()); }
                selectedSlot = bow ? Slot.SECONDARY : requested == null ? Slot.PRIMARY : requested;
                slot.setValue(selectedSlot);
                slot.setDisable(bow);
                selectionNote.setText(bow ? "Projectile primary · Secondary melee only" : "Melee attack · same frame in all four directions");
                original = file.form(id, selectedSlot);
                shape.setValue(AttackShape.valueOf(original.get("type")));
                fields.forEach((key, input) -> input.setText(original.get(key)));
                clip = RawPreview.load(raw, player, file.atlas(id), file.attack(id, selectedSlot));
            } catch (Exception ex) {
                previewError.setText(message(ex));
            } finally {
                updating = false;
            }
            boolean ready = clip != null;
            scrub.setDisable(!ready);
            play.setDisable(!ready);
            previous.setDisable(!ready);
            next.setDisable(!ready);
            movingSlider = true;
            scrub.setMax(ready ? clip.frames() - 1 : 1);
            movingSlider = false;
            updateBounds();
            showFrame(0);
            play.setSelected(ready);
        }

        private void clearPreview() {
            play.setSelected(false);
            play.setDisable(true);
            previous.setDisable(true);
            next.setDisable(true);
            scrub.setDisable(true);
            frameLabel.setText("");
            clip = null;
            seconds = 0;
            lastPulse = 0;
            frame = -1;
        }

        private Map<String, String> values() {
            var values = new LinkedHashMap<String, String>();
            values.put("type", shape.getValue().name());
            fields.forEach((key, input) -> values.put(key, input.getText()));
            return values;
        }

        private boolean dirty() { return original != null && !original.equals(values()); }

        private boolean confirmDiscard() {
            if (!dirty()) { return true; }
            var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Discard unsaved bounds edits?", ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(stage);
            return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
        }

        private void updateBounds() {
            if (updating) { return; }
            sensorPolygons = null;
            boolean editable = original != null && clip != null;
            shape.setDisable(!editable);
            fields.values().forEach(input -> input.setDisable(!editable));
            save.setDisable(true);
            if (editable) {
                try {
                    sensorPolygons = player.geometry().weaponPixels(decode(values()), player.size());
                    save.setDisable(!dirty());
                    editStatus.setText(dirty() ? "Unsaved bounds edits" : "");
                } catch (IllegalArgumentException ex) {
                    editStatus.setText(message(ex));
                }
            }
            draw();
        }

        private void save() {
            try {
                file = file.save(selectedId, selectedSlot, values(), player.geometry(), player.size());
                original = values();
                save.setDisable(true);
                editStatus.setText("Saved " + selectedId + " / " + selectedSlot.key + " bounds.");
            } catch (Exception ex) {
                editStatus.setText("Could not save: " + message(ex));
            }
        }

        private void seek(int index) {
            play.setSelected(false);
            int target = Math.clamp(index, 0, clip.frames() - 1);
            seconds = target * clip.attack().frameSeconds();
            showFrame(target);
        }

        private void showFrame(int index) {
            if (clip == null || index == frame) { return; }
            frame = index;
            movingSlider = true;
            scrub.setValue(index);
            movingSlider = false;
            var attack = clip.attack();
            frameLabel.setText(String.format(java.util.Locale.ROOT, "%02d / %02d  ·  %.1f ms/frame  ·  Hit %02d (%s)",
                index, clip.frames() - 1, attack.frameSeconds() * 1000, attack.hitFrame(),
                attack.sensorActive(index) ? "active" : "pending"));
            draw();
        }

        private void draw() {
            double halfWidth = clip == null ? 1 : clip.width() * .5;
            double halfHeight = clip == null ? 1 : clip.height() * .5;
            if (clip != null && sensor.isSelected() && sensorPolygons != null) {
                for (var points : sensorPolygons.values()) {
                    for (var p : points) {
                        halfWidth = Math.max(halfWidth, Math.abs(p.x - player.size().x() * .5));
                        halfHeight = Math.max(halfHeight, Math.abs(p.y - player.size().y() * .5));
                    }
                }
            }
            double fitWidth = halfWidth * 2, fitHeight = halfHeight * 2;
            views.forEach((direction, canvas) -> {
                double width = canvas.getWidth(), height = canvas.getHeight();
                var g = canvas.getGraphicsContext2D();
                g.setFill(Color.web("#202631"));
                g.fillRect(0, 0, width, height);
                if (clip == null || frame < 0 || width < 20 || height < 20) { return; }
                double scale = Math.min(3, Math.min((width - 14) / fitWidth, (height - 14) / fitHeight));
                double w = clip.width() * scale, h = clip.height() * scale;
                double left = (width - w) / 2, top = (height - h) / 2;
                double x = (width - player.size().x() * scale) / 2;
                double y = (height + player.size().y() * scale) / 2;
                g.setFill(Color.web("#303947"));
                g.fillRect(left, top, w, h);
                g.setImageSmoothing(false);
                for (var image : clip.poses().get(direction).get(frame).layers()) { g.drawImage(image, left, top, w, h); }
                if (hurt.isSelected()) { polygon(g, player.hurtbox(), HURT, true, x, y, scale); }
                if (solid.isSelected()) {
                    if (player.solidCircle() != null) { circle(g, player.solidCircle(), x, y, scale); }
                    else { polygon(g, player.solid(), SOLID, true, x, y, scale); }
                }
                if (sensor.isSelected() && sensorPolygons != null) {
                    polygon(g, sensorPolygons.get(direction), SENSOR, clip.attack().sensorActive(frame), x, y, scale);
                }
            });
        }

        private static void circle(GraphicsContext g, Box b, double x, double y, double scale) {
            g.setFill(SOLID.deriveColor(0, 1, 1, .14));
            g.setStroke(SOLID);
            g.setLineWidth(1.5);
            g.setLineDashes();
            g.fillOval(x + b.x1() * scale, y - b.y2() * scale, b.width() * scale, b.height() * scale);
            g.strokeOval(x + b.x1() * scale, y - b.y2() * scale, b.width() * scale, b.height() * scale);
        }

        private static void polygon(GraphicsContext g, Vector2[] points, Color color, boolean active,
                                    double x, double y, double scale) {
            var xs = new double[points.length];
            var ys = new double[points.length];
            for (int i = 0; i < points.length; i++) {
                xs[i] = x + points[i].x * scale;
                ys[i] = y - points[i].y * scale;
            }
            g.setStroke(color);
            g.setLineWidth(1.5);
            if (active) {
                g.setLineDashes();
                g.setFill(color.deriveColor(0, 1, 1, .14));
                g.fillPolygon(xs, ys, points.length);
            } else {
                g.setLineDashes(4, 4);
            }
            g.strokePolygon(xs, ys, points.length);
        }

        private static CheckBox overlay(String text, Color color) {
            var toggle = new CheckBox(text);
            var swatch = new javafx.scene.shape.Rectangle(10, 10, color);
            swatch.setStroke(color.darker());
            toggle.setGraphic(swatch);
            toggle.setSelected(true);
            return toggle;
        }

        private static String message(Exception ex) {
            return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }
}
