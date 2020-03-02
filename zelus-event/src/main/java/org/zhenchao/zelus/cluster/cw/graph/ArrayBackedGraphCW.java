package org.zhenchao.zelus.cluster.cw.graph;

import it.unimi.dsi.fastutil.ints.Int2FloatMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import org.zhenchao.zelus.cluster.cw.CW;

import java.util.ArrayList;
import java.util.Iterator;

public class ArrayBackedGraphCW extends CW<Integer> {
    ArrayBackedGraph<Float> graph;

    final Integer[] nodeLabels;
    final Int2FloatOpenHashMap labelScores;
    Integer lastAssignedLabel = null;

    public ArrayBackedGraphCW(int arraySize) {
        this.nodeLabels = new Integer[arraySize];
        this.labelScores = new Int2FloatOpenHashMap();
    }

    @Override
    protected void init(Graph<Integer, Float> graph) {
        this.graph = (ArrayBackedGraph<Float>) graph;
        int numNodes = this.graph.nodes.cardinality();
        nodes = new ArrayList<Integer>(numNodes);
        Iterator<Integer> nodeIt = graph.iterator();
        while (nodeIt.hasNext()) {
            Integer node = nodeIt.next();
            nodes.add(node);
            this.nodeLabels[node] = node;
        }
    }

    @Override
    protected Integer getNodeLabel(Integer node) {
        return this.nodeLabels[node];
    }

    @Override
    protected void relabelNode(Integer node) {
        //		System.out.println("+++ relabel " + node);
        //		System.out.println("BEFORE: " + nodeLabels);
        Integer oldLabel = this.nodeLabels[node];
        ArrayList<Integer> edges = this.graph.edgeSources[node];
        ArrayList<Float> weights = this.graph.edgeWeights[node];
        if (edges == null) {
            return;
        }
        this.labelScores.clear();
        for (int i = 0; i < edges.size(); i++) {
            int source = edges.get(i);
            float weight = weights.get(i);
            Integer label = this.nodeLabels[source];
            this.labelScores.addTo(label, weight);
        }

        Integer newLabel = oldLabel;
        Float maxScore = 0.0f;
        for (Entry labelScore : this.labelScores.int2FloatEntrySet()) {
            int n = labelScore.getIntKey();
            float score = labelScore.getFloatValue();
            if (score > maxScore) {
                newLabel = n;
                maxScore = score;
            }
        }
        if (!oldLabel.equals(newLabel)) {
            this.nodeLabels[node] = newLabel;
            changeInPrevStep = true;
        }
    }
}
