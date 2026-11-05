package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;

public class Transform implements Component, Comparable<Transform> {
    public static final ComponentMapper<Transform> MAPPER = ComponentMapper.getFor(Transform.class);

    private final Vector2 position;
    private final int z;
    private final Vector2 size;
    private final Vector2 scaling;
    private float rotationDeg;
    private float sortOffsetY;


    public Transform(Vector2 position, int z, Vector2 size, Vector2 scaling, float rotationDeg, float sortOffsetY) {
        this.position = position;
        this.z = z;
        this.size = size;
        this.scaling = scaling;
        this.rotationDeg = rotationDeg;
        this.sortOffsetY = sortOffsetY;
    }

    @Override
    public int compareTo(Transform other) {
        if (this.z != other.z) {
            return Float.compare(this.z, other.z);
        }
        if (this.position.y  != other.position.y) {
            return Float.compare(other.position.y, this.position.y);
        }
        return Float.compare(this.position.x, other.position.x);
    }

    public Vector2 getPosition() {
        return position;
    }

    public static Transform forTiledObject(TiledMapTile tile, TextureRegion texRegion, TiledMapTileMapObject tileMapObj) {
        Vector2 pos = new Vector2(tileMapObj.getX(), tileMapObj.getY());
        Vector2 size = new Vector2(texRegion.getRegionWidth(), texRegion.getRegionHeight());
        Vector2 scale = new Vector2(tileMapObj.getScaleX(), tileMapObj.getScaleY());
        int z =2;// tile.getProperties().get("z", 1, Integer.class);

        pos.scl(GameConsts.UNIT_SCALE);
        size.scl(GameConsts.UNIT_SCALE);

        return new Transform(pos, z, size, scale, 0f, 0f);
    }

    public Vector2 getSize() {
        return size;
    }

    public Vector2 getScaling() {
        return scaling;
    }

    public float getRotationDeg() {
        return rotationDeg;
    }
}
