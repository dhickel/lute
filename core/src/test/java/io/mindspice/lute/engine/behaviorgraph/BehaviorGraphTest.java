package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;
import java.util.List;

public final class BehaviorGraphTest {
    private record Visit(Entity expected, boolean enabled, StringBuilder trace) {
        void log(Entity self, char step) {
            if (self != expected) { throw new AssertionError("Lost traversal self at " + step); }
            trace.append(step);
        }
    }

    public static void main(String[] args) {
        var root = new RootNode<Visit>();
        var branch = DecisionNode.<Visit>of("enabled", List.of(
            (self, v) -> { v.log(self, '1'); return v.enabled(); },
            (self, v) -> { v.log(self, '2'); return true; }));
        root.addEdgeNode(branch);
        branch.addEdgeNode(new RootNode<>()); // An unsuccessful child must allow its sibling to run.
        Action<Visit> a = (self, v) -> v.log(self, 'a');
        Action<Visit> b = (self, v) -> v.log(self, 'b');
        branch.addEdgeNode(ActionNode.of("sequence", a.multiAction(List.of(b, a)).andThen(b)));
        branch.addEdgeNode(ActionNode.of("unreached", a));
        root.addEdgeNode(ActionNode.of("fallback", (self, v) -> v.log(self, 'f')));

        check(root, true, "12bab");
        check(root, false, "1f");
        check(root, true, "12bab");
    }

    private static void check(RootNode<Visit> root, boolean enabled, String expected) {
        var self = new Entity();
        var visit = new Visit(self, enabled, new StringBuilder());
        if (!root.travel(self, visit) || !expected.contentEquals(visit.trace())) {
            throw new AssertionError("Expected " + expected + ", got " + visit.trace());
        }
    }
}
