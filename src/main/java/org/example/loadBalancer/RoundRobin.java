package org.example.loadBalancer;

import org.example.Node;

import java.util.List;

// RoundRobin implementation för lastbalanseringen.
// RoundRobin är den mest simpla och mest använda lastbalanseringsalgoritmen.
public class RoundRobin implements RoundRobinInterface {

    private int index = 0;

    @Override
    public Node next(List<Node> nodes) {
        if (nodes.isEmpty()) throw new RuntimeException("No nodes currently active.");

        index++;
        if (index >= nodes.size())
            index = 0;

        return nodes.get(index);
    }

}
