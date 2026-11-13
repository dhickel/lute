package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.singletons.GameConsts;

public class TiledAshleyLoader {
    private final World physicWorld;

    public TiledAshleyLoader(World physicWorld) {
        this.physicWorld = physicWorld;
    }

    public void loadTile(TiledMapTile tile, float x, float y) {
        createBody(tile.getObjects(), new Vector2(x, y));
    }

    private void createBody(MapObjects mapObjects, Vector2 position) {
        var bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(position);
        bodyDef.fixedRotation = true;

        var body = physicWorld.createBody(bodyDef);
        body.setUserData("environment");

        for (var mapObject : mapObjects) {
            var fixtureDef = TiledPhysics.fixtureDefOf(mapObject, GameConsts.PHYSIC_SCALING, Vector2.Zero);
            var fixture = body.createFixture(fixtureDef);
            fixture.setUserData(mapObject.getName());
            fixtureDef.shape.dispose();
        }
    }
}
