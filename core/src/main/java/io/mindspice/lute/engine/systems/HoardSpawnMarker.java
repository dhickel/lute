package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.behaviorgraph.ActionNode;
import io.mindspice.lute.engine.behaviorgraph.RootNode;
import io.mindspice.lute.engine.tiled.MapTriggers.SpawnArea;

import java.util.Map;
import java.util.random.RandomGenerator;

/** A staged polygon has an identity for graph traversal, but no body or engine systems to update. */
final class HoardSpawnMarker {
    static final String AGRO_WANDERING_HOARD = "agroWanderingHoard";
    private static final Map<String, RootNode<SpawnContext>> GRAPHS = Map.of(AGRO_WANDERING_HOARD, placementGraph());
    private record Zone(SpawnArea area) implements Component { }
    private final Entity self;
    private final RootNode<SpawnContext> graph;

    HoardSpawnMarker(SpawnArea area) {
        var key = area.behaviorKey();
        graph = key == null ? null : GRAPHS.get(key);
        if (graph == null) { throw new IllegalArgumentException(area.name() + ": unknown Hoard behaviorKey: " + key); }
        self = new Entity().add(new Zone(area));
    }

    private static RootNode<SpawnContext> placementGraph() {
        var root = new RootNode<SpawnContext>();
        root.addEdgeNode(ActionNode.of("Place wave enemy", (self, context) -> {
            var area = self.getComponent(Zone.class).area();
            context.enemy = context.tiled.trySpawnAreaEnemy(area, context.key, context.random);
        }));
        return root;
    }

    Entity trySpawn(TiledSystem tiled, String key, RandomGenerator random) {
        var context = new SpawnContext(tiled, key, random);
        graph.travel(self, context);
        return context.enemy;
    }

    private static final class SpawnContext {
        final TiledSystem tiled;
        final String key;
        final RandomGenerator random;
        Entity enemy;

        SpawnContext(TiledSystem tiled, String key, RandomGenerator random) {
            this.tiled = tiled;
            this.key = key;
            this.random = random;
        }
    }
}
