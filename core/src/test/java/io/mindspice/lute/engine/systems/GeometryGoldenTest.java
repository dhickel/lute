package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.data.json.CollisionData;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.Offset;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GeometryGoldenTest {
    private static final float PIXEL_TOLERANCE = .02f;

    record Cases(float unitScale, List<AttackCase> attacks, List<FixtureCase> fixtures) { }

    record AttackCase(String name, BoundsType shape, float[] bounds, float[] size, Map<String, float[]> offsets,
                      Map<String, float[][]> verticesPixels) {
        Offset offset(String direction) {
            var point = offsets.get(direction);
            return point == null ? new Offset(0, 0) : new Offset(point[0], point[1]);
        }
    }

    record FixtureCase(String name, CollisionData.Shape shape, float[] bounds, float rotation,
                       float[][] verticesPixels) { }

    public static void main(String[] args) throws IOException {
        Cases cases;
        try (var input = Objects.requireNonNull(GeometryGoldenTest.class.getResourceAsStream("/geometry-golden.json"))) {
            cases = JsonMapper.builder().build().readValue(input, Cases.class);
        }
        GdxNativesLoader.load();
        Box2D.init();
        float priorUnit = GameConsts.UNIT_SCALE;
        GameConsts.UNIT_SCALE = cases.unitScale();
        var world = new World(Vector2.Zero, true);
        try {
            for (var test : cases.attacks()) { verifyAttack(test); }
            var body = world.createBody(new BodyDef());
            for (var test : cases.fixtures()) { verifyFixture(body, test); }
        } finally {
            world.dispose();
            GameConsts.UNIT_SCALE = priorUnit;
        }
        System.out.printf("Geometry golden cases passed: %d attacks, %d fixtures%n",
            cases.attacks().size(), cases.fixtures().size());
    }

    private static void verifyAttack(AttackCase test) {
        var b = test.bounds();
        var bounds = new CombatWeapon.Bounds(b[0], b[1], b[2], b[3],
            test.offset("UP"), test.offset("DOWN"), test.offset("LEFT"), test.offset("RIGHT"));
        var attack = new CombatWeapon.Attack("golden", null, 0, 0, null, null, test.shape(), bounds, CollisionType.MELEE);
        var anchor = new Vector2(test.size()[0], test.size()[1]).scl(.5f * GameConsts.UNIT_SCALE);
        if (test.verticesPixels() == null) {
            rejectsWelded(() -> attack.validateGeometry(anchor), test.name());
            return;
        }
        attack.validateGeometry(anchor);
        test.verticesPixels().forEach((direction, expected) -> {
            var actual = attack.vertices(anchor, Facing.Direction.valueOf(direction));
            if (actual.length != expected.length || !matchesFrom(actual, expected, 0)) {
                throw new AssertionError(test.name() + ": unexpected vertices for " + direction);
            }
        });
    }

    private static void verifyFixture(Body body, FixtureCase test) {
        var b = test.bounds();
        var collision = new CollisionData(test.shape(), new CharacterBounds(b[0], b[1], b[2], b[3]), test.rotation());
        var expected = test.verticesPixels();
        if (expected == null) {
            rejectsWelded(() -> CharacterFixtures.create(body, collision, false, 0, 0), test.name());
            return;
        }
        var fixture = CharacterFixtures.create(body, collision, false, 0, 0);
        try {
            var shape = (PolygonShape) fixture.getShape();
            var actual = new Vector2[shape.getVertexCount()];
            if (actual.length != expected.length) { throw new AssertionError(test.name() + ": wrong vertex count"); }
            for (int i = 0; i < actual.length; i++) {
                actual[i] = new Vector2();
                shape.getVertex(i, actual[i]);
            }
            // Box2D chooses the starting vertex of its counterclockwise hull.
            for (int start = 0; start < actual.length; start++) {
                if (matchesFrom(actual, expected, start)) { return; }
            }
            throw new AssertionError(test.name() + ": unexpected native fixture vertices");
        } finally {
            body.destroyFixture(fixture);
        }
    }

    private static boolean matchesFrom(Vector2[] actual, float[][] expected, int start) {
        float unit = GameConsts.UNIT_SCALE;
        for (int i = 0; i < expected.length; i++) {
            var point = actual[(start + i) % actual.length];
            if (!(Math.abs(point.x / unit - expected[i][0]) <= PIXEL_TOLERANCE
                && Math.abs(point.y / unit - expected[i][1]) <= PIXEL_TOLERANCE)) { return false; }
        }
        return true;
    }

    private static void rejectsWelded(Runnable build, String name) {
        try {
            build.run();
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("edge limits")) { return; }
            throw new AssertionError(name + ": wrong rejection", ex);
        }
        throw new AssertionError(name + ": expected rejection");
    }
}
