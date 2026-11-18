package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Entity;

/** Session-owned live components; the map player uses these same instances. */
public record PlayerState(Health health, Loadout loadout, Facing facing) {
    public static PlayerState of(Entity player) {
        return new PlayerState(Health.MAPPER.get(player), Loadout.MAPPER.get(player), Facing.MAPPER.get(player));
    }

    public void attachTo(Entity player) {
        player.add(health).add(loadout).add(facing);
    }
}
