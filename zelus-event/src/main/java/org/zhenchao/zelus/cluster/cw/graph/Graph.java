package org.zhenchao.zelus.cluster.cw.graph;

import de.tudarmstadt.lt.util.IndexUtil.Index;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;

public interface Graph<N, E> extends Iterable<N> {

    int getSize();

    void addNode(N node);

    void addEdgeUndirected(N from, N to, E weight);

    void addEdge(N from, N to, E weight);

    Iterator<N> getNeighbors(N node);

    Iterator<Edge<N, E>> getEdges(N node);

    E getEdge(N target, N source);

    boolean hasNode(N node);

    /**
     * Returns a non-modifiable undirected subgraph of this graph.<br>
     *
     * <b>NOTE: The behaviour of this graph when nodes are added or removed is undefined!</b>
     */
    Graph<N, E> undirectedSubgraph(Collection<N> subgraphNodes);

    /**
     * Returns a non-modifiable subgraph of this graph.<br>
     *
     * <b>NOTE: The behaviour of this graph when nodes are added or removed is undefined!</b>
     *
     * @param maxEdgesPerNode Maximum number of outgoing edges a node is allowed to have in this
     * subgraph, remaining edges will not be added. Note that this will
     * <i>not</i> take the top <code>maxEdgesPerNode</code> edges and pick
     * only those to nodes in the subgraph, but instead will first pick
     * those edges that point to nodes in the subgraph, and <i>then</i> take
     * only the top <code>maxEdgePerNode</code> edges of these.
     */
    Graph<N, E> subgraph(Collection<N> subgraphNodes, int numEdgesPerNode);

    void writeDot(Writer writer) throws IOException;

    void writeDot(Writer writer, Index<?, N> index) throws IOException;

    void writeDotUndirected(Writer writer) throws IOException;

    void writeDotUndirected(Writer writer, Index<?, N> index) throws IOException;
}
