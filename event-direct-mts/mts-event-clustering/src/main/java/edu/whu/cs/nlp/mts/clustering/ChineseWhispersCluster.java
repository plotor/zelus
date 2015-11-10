package edu.whu.cs.nlp.mts.clustering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.NumedEventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import edu.whu.cs.nlp.mts.clustering.cw.CW;
import edu.whu.cs.nlp.mts.clustering.cw.graph.ArrayBackedGraph;
import edu.whu.cs.nlp.mts.clustering.cw.graph.Graph;
import edu.whu.cs.nlp.mts.clustering.domain.CWEdge;

/**
 * 口哨算法聚类
 *
 * @author ZhenchaoWang 2015-11-10 14:23:27
 *
 */
public class ChineseWhispersCluster implements Callable<Boolean> {

    private final Logger log                   = Logger.getLogger(this.getClass());

    /** node文件所在路径 */
    private final String nodeFilePath;
    /** edge文件所在路径 */
    private final String edgeFilePath;
    /** 允许入边的阈值 */
    private final Float  EDGE_WEIGHT_THRESHOLD = 1.0f;

    public ChineseWhispersCluster(String nodeFilePath, String edgeFilePath) {
        super();
        this.nodeFilePath = nodeFilePath;
        this.edgeFilePath = edgeFilePath;
    }

    /**
     * 事件到子句的映射
     *
     * @param eventWithPhrase
     * @param wordsInText 事件所在文件的所有的词，按句子组织
     * @return
     */
    private List<Word> eventToSubSentence(EventWithPhrase eventWithPhrase, List<List<Word>> wordsInText) {
        List<Word> subSentence = new ArrayList<Word>();
        if(eventWithPhrase == null) {
            return subSentence;
        }

        return subSentence;
    }

    @Override
    public Boolean call() throws Exception {

        // 加载node文件
        this.log.info("[" + Thread.currentThread().getName() + "]Loading serilized file:" + this.nodeFilePath);
        @SuppressWarnings("unchecked")
        Map<Integer, NumedEventWithPhrase> eventWithNums = (Map<Integer, NumedEventWithPhrase>) SerializeUtil.readObj(this.nodeFilePath);
        if (MapUtils.isEmpty(eventWithNums)) {
            this.log.error("Can't load any node data from [" + this.nodeFilePath + "]");
            throw new IOException("Can't load any node data from [" + this.nodeFilePath + "]");
        }

        // 加载edge文件
        this.log.info("[" + Thread.currentThread().getName() + "]Loading serilized file:" + this.edgeFilePath);
        @SuppressWarnings("unchecked")
        List<CWEdge> cwEdges = (List<CWEdge>) SerializeUtil.readObj(this.edgeFilePath);
        if (CollectionUtils.isEmpty(cwEdges)) {
            this.log.error("Can't load any edge data from [" + this.edgeFilePath + "]");
            throw new IOException("Can't load any edge data from [" + this.edgeFilePath + "]");
        }

        // 构建事件图
        Graph<Integer, Float> graph = new ArrayBackedGraph<Float>(eventWithNums.size(), eventWithNums.size());
        // 添加结点
        for (Entry<Integer, NumedEventWithPhrase> entry : eventWithNums.entrySet()) {
            graph.addNode(entry.getValue().getNum());
        }
        // 计算所有边权值的均值
        float totalWeight = 0.0f;
        for (CWEdge cwEdge : cwEdges) {
            totalWeight += cwEdge.getWeight();
        }
        float avgWeight = totalWeight / cwEdges.size();
        // 添加边
        for (CWEdge cwEdge : cwEdges) {
            if(cwEdge.getWeight() >= avgWeight * this.EDGE_WEIGHT_THRESHOLD) {
                graph.addEdgeUndirected(cwEdge.getFrom(), cwEdge.getTo(), cwEdge.getWeight());
            }
        }

        // 采用口哨算法进行聚类
        CW<Integer> cw = new CW<Integer>();
        Map<Integer, Set<Integer>> clusters = cw.findClusters(graph);

        return null;
    }

}
