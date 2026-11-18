package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.Vector2;

public final class BowAim implements Component {
    public static final ComponentMapper<BowAim> MAPPER = ComponentMapper.getFor(BowAim.class);
    // shot_04 has the greatest bow bend; shot_05 begins the recoil.
    public static final int FULL_DRAW_FRAME = 4;
    private static final float TILES_PER_SECOND = 6f;
    private final float tileWidth, tileHeight;
    private final Vector2 target = new Vector2();
    private CombatWeapon.Weapon weapon;
    private boolean firstFrame;

    public BowAim(float tileWidth, float tileHeight) {
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    public boolean isAiming() { return weapon != null; }
    public CombatWeapon.Weapon getWeapon() { return weapon; }
    public float getTargetX() { return target.x; }
    public float getTargetY() { return target.y; }

    public void start(CombatWeapon.Weapon weapon, Transform transform, Facing.Direction facing) {
        this.weapon = weapon;
        firstFrame = true;
        target.set(transform.getPosition()).mulAdd(transform.getSize(), 0.5f);
        switch (facing) {
            case UP -> target.y += 2f * tileHeight;
            case DOWN -> target.y -= 2f * tileHeight;
            case LEFT -> target.x -= 2f * tileWidth;
            case RIGHT -> target.x += 2f * tileWidth;
        }
    }

    public void steer(Vector2 direction, float dt) {
        if (firstFrame) { firstFrame = false; return; }
        if (direction.isZero()) { return; }
        float dx = Math.signum(direction.x), dy = Math.signum(direction.y);
        float step = TILES_PER_SECOND * dt / Vector2.len(dx, dy);
        target.add(dx * tileWidth * step, dy * tileHeight * step);
    }

    public void cancel() { weapon = null; }
}
