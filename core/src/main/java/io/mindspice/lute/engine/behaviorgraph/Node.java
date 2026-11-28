package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;

import java.util.ArrayList;
import java.util.List;

public abstract sealed class Node<C> permits ActionNode, DecisionNode, RootNode {
    protected final String name;
    protected final NodeType nodeType;
    protected final List<Node<C>> edgeNodes;

    public Node(NodeType nodeType, String name) {
        this.name = name;
        this.nodeType = nodeType;
        this.edgeNodes = new ArrayList<>();
    }

    public abstract boolean travel(Entity self, C context);

    public List<Node<C>> getEdgeNodes() {
        return edgeNodes;
    }

    public String getName() {
        return name;
    }

    public NodeType getType() {
        return nodeType;
    }

    public void addEdgeNode(Node<C> node) {
        if (nodeType == NodeType.ACTION) {
            throw new IllegalStateException("Cannot add edges to action nodes");
        }
        if (edgeNodes.contains(node)) {
            throw new IllegalStateException("Cannot add same edge twice");
        }
        edgeNodes.add(node);
    }

    @Override
    public String toString() {
        return name;
    }

    public enum NodeType {ROOT, DECISION, ACTION}

}
