package org.zhenchao.zelus.clustering.cw;

import de.tudarmstadt.lt.util.MapUtil;
import org.zhenchao.zelus.clustering.cw.graph.Edge;
import org.zhenchao.zelus.clustering.cw.graph.Graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Implementation of Chinese Whispers graph clustering algorithm.<br/>
 * <br/>
 * <b>Usage:</b><br/>
 * <code>
 * Graph&lt;Integer, Float&gt; graph = new ArrayBackedGraph&lt;Float&gt;(...);<br/>
 * // ...<br/>
 * CW&lt;Integer&gt; cw = new CW&lt;Integer&gt;();<br/>
 * Map&lt;Integer, Set&lt;Integer&gt;&gt; clusters = cw.findClusters(graph);<br/>
 * </code>
 */
public class CW<N> {
    // Copy of node list is used shuffling order of nodes
    protected List<N> nodes;
    protected Graph<N, Float> graph;
    protected Map<N, N> nodeLabels;
    protected boolean changeInPrevStep;
    protected Map<N, Float> labelScores = new HashMap<N, Float>();

    protected void init(Graph<N, Float> graph) {
        this.graph = graph;
        // ArrayList provides linear time random access (used for shuffle in
        // step())
        this.nodes = new ArrayList<N>();

        Iterator<N> nodeIt = graph.iterator();
        while (nodeIt.hasNext()) {
            this.nodes.add(nodeIt.next());
        }

        this.nodeLabels = new HashMap<N, N>();
        for (N node : this.nodes) {
            this.nodeLabels.put(node, node);
        }
    }

    protected void relabelNode(N node) {
        this.labelScores.clear();
        N oldLabel = this.nodeLabels.get(node);
        Iterator<Edge<N, Float>> edgeIt = this.graph.getEdges(node);

        // There's nothing to do if there's no neighbors
        if (!edgeIt.hasNext()) {
            return;
        }

        while (edgeIt.hasNext()) {
            Edge<N, Float> edge = edgeIt.next();
            if (edge == null) {
                break;
            }
            N label = this.nodeLabels.get(edge.getSource());
            MapUtil.addFloatTo(this.labelScores, label, edge.getWeight());
        }
        // isEmpty() check in case e.g. node has no neighbors at all
        // (it will simply keep its own label then)
        if (!this.labelScores.isEmpty()) {
            N newLabel = this.getKeyWithMaxValue(this.labelScores);
            if (!oldLabel.equals(newLabel)) {
                this.nodeLabels.put(node, newLabel);
                this.changeInPrevStep = true;
            }
        }
    }

    protected N getKeyWithMaxValue(Map<N, Float> map) {
        N maxKey = null;
        Float maxVal = -Float.MAX_VALUE;
        for (Entry<N, Float> entry : map.entrySet()) {
            if (entry.getValue() > maxVal) {
                maxKey = entry.getKey();
                maxVal = entry.getValue();
            }
        }
        return maxKey;
    }

    protected void step() {
        Collections.shuffle(this.nodes);
        for (N node : this.nodes) {
            this.relabelNode(node);
        }
    }

    protected N getNodeLabel(N node) {
        return this.nodeLabels.get(node);
    }

    protected Map<N, Set<N>> getClusters() {
        Map<N, Set<N>> clusters = new HashMap<N, Set<N>>();
        for (N node : this.nodes) {
            N label = this.getNodeLabel(node);
            Set<N> cluster = MapUtil.getOrCreate(clusters, label, HashSet.class);
            cluster.add(node);
        }
        return clusters;
    }

    public Map<N, Set<N>> findClusters(Graph<N, Float> graph) {
        this.init(graph);

        int numSteps = 0;
        do {
            if (numSteps > 100) {
                System.out.println("Too many steps!");
            }
            this.changeInPrevStep = false;
            this.step();
            numSteps++;
        } while (this.changeInPrevStep);

        return this.getClusters();
    }
}
