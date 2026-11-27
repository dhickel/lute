package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Transform;

public class PhysicSystem extends IteratingSystem implements EntityListener {
    private final World world;
    private final float interval;
    private final ArenaSystem arena;
    private float accumulator;
    private long completedSteps;

    long completedSteps() { return completedSteps; }


    public PhysicSystem(World world, float interval) { this(world, interval, null); }

    public PhysicSystem(World world, float interval, ArenaSystem arena) {
        super(Family.all(Physic.class, Transform.class).get());
        this.world = world;
        this.interval = interval;
        this.arena = arena;
        this.accumulator = 0f;
    }

    void resetClock() {
        accumulator = 0f;
    }

    @Override
    public void update(float deltaTime) {
        this.accumulator += deltaTime;

        var combat = getEngine().getSystem(CombatSystem.class);
        long boostedPress = 0;
        while (accumulator >= interval) {
            accumulator -= interval;
            super.update(deltaTime);
            if (arena != null) { boostedPress = arena.beforeSprintStep(interval, boostedPress != 0); }
            if (combat != null) { combat.beforeMeleePushStep(); }
            world.step(interval, 6, 2);
            if (combat != null) { combat.finishMeleePushStep(); }
            completedSteps++;
            if (boostedPress != 0) { arena.finishSprintStep(interval, boostedPress); }
            var sensors = getEngine().getSystem(MapSensorSystem.class);
            if (sensors != null) { sensors.afterPhysicsStep(); }
            if (arena != null && arena.isActive()) { arena.afterPhysicsStep(interval); }
            else if (combat != null) { combat.resolveFixedAttackHits(); }
        }
        world.clearForces();

        float alpha = accumulator / interval;
        getEntities().forEach(e -> interpolateEntity(e, alpha));

//        for(int i = 0; i < getEntities().size(); ++i) {
//
//        }

    }


    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        engine.addEntityListener(getFamily(), this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        engine.removeEntityListener(this);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Physic physic = Physic.MAPPER.get(entity);
        physic.getPrevPos().set(physic.getBody().getPosition());
        var combat = getEngine().getSystem(CombatSystem.class);
        if (combat != null) { combat.beforeFixedAttackStep(entity, interval); }
    }

    private void interpolateEntity(Entity entity, float alpha) {
        Physic physic = Physic.MAPPER.get(entity);
        // Static fixtures may use a different origin from their authored render transform.
        if (physic.getBody().getType() == BodyDef.BodyType.StaticBody) { return; }
        Transform transform = Transform.MAPPER.get(entity);

        transform.getPosition().set(
            MathUtils.lerp(physic.getPrevPos().x, physic.getBody().getPosition().x, alpha),
            MathUtils.lerp(physic.getPrevPos().y, physic.getBody().getPosition().y, alpha)
        );

    }

    @Override
    public void entityAdded(Entity entity) {

    }

    @Override
    public void entityRemoved(Entity entity) {
        Physic physic = Physic.MAPPER.get(entity);
        if (physic != null) {
            world.destroyBody(physic.getBody());
        }


    }
}
