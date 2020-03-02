package org.zhenchao.zelus.cluster.cw.graph;

import de.tudarmstadt.lt.util.IndexUtil;
import de.tudarmstadt.lt.util.IndexUtil.StringIndex;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StringIndexGraphWrapper<E> extends StringIndex {
    protected Graph<Integer, E> base;

    public StringIndexGraphWrapper(Graph<Integer, E> base) {
        this.base = base;
    }

    public Graph<Integer, E> getGraph() {
        return base;
    }

    public void addNode(String node) {
        Integer index = this.getIndex(node);
        base.addNode(index);
    }

    public void addNode(String node, List<String> targets, List<E> weights, boolean undirected) {
        Integer from = this.getIndex(node);
        Iterator<String> targetIt = targets.iterator();
        Iterator<E> weightIt = weights.iterator();
        while (targetIt.hasNext()) {
            Integer to = this.getIndex(targetIt.next());
            E weight = weightIt.next();

            if (undirected) {
                base.addEdgeUndirected(to, from, weight);
            } else {
                base.addEdge(from, to, weight);
            }
        }

    }

    public void addEdge(String from, String to, E weight) {
        Integer fromIndex = this.getIndex(from);
        Integer toIndex = this.getIndex(to);
        base.addEdge(fromIndex, toIndex, weight);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object other) {
        if (!(other instanceof StringIndexGraphWrapper<?>)) {
            return false;
        }
        StringIndexGraphWrapper<E> otherGraph = (StringIndexGraphWrapper<E>) other;
        List<Integer> intNodes = IteratorUtils.toList(base.iterator());
        List<String> nodes = IndexUtil.map(intNodes, this);
        List<String> nodesOther = IndexUtil.map(IteratorUtils.toList(otherGraph.base.iterator()), this);
        if (!nodes.containsAll(nodesOther)) {
            return false;
        }
        if (!nodesOther.containsAll(nodes)) {
            return false;
        }

        for (String node : nodes) {
            Integer intNode = this.getIndex(node);
            Integer intNodeOther = otherGraph.getIndex(node);
            List<Edge<Integer, E>> intEdges = IteratorUtils.toList(base.getEdges(intNode));
            List<Edge<Integer, E>> intEdgesOther = IteratorUtils.toList(otherGraph.base.getEdges(intNodeOther));
            List<Edge<String, E>> edges = new ArrayList<Edge<String, E>>(intEdges.size());
            List<Edge<String, E>> edgesOther = new ArrayList<Edge<String, E>>(intEdgesOther.size());
            for (Edge<Integer, E> edge : intEdges) {
                edges.add(new Edge<String, E>(this.get(edge.source), edge.weight));
            }
            for (Edge<Integer, E> edge : intEdgesOther) {
                edgesOther.add(new Edge<String, E>(otherGraph.get(edge.source), edge.weight));
            }
            if (!edges.containsAll(edgesOther)) {
                return false;
            }
            if (!edgesOther.containsAll(edges)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph {\n");
        Iterator<Integer> it = base.iterator();
        while (it.hasNext()) {
            Integer node = it.next();
            sb.append("\t" + this.get(node) + ": ");
            @SuppressWarnings("unchecked")
            List<String> neighbors = IndexUtil.map(IteratorUtils.toList(base.getNeighbors(node)), this);
            sb.append(StringUtils.join(neighbors, ','));
            sb.append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
