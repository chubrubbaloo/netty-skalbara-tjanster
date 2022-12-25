package org.example.loadBalancer;

import org.example.Node;

import java.util.List;

// Interface för vår RoundRobinAlgoritm.
public interface RoundRobinInterface {

    Node next(List<Node> nodes);
}
