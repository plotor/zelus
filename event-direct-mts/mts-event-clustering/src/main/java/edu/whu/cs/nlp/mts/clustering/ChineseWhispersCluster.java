package edu.whu.cs.nlp.mts.clustering;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import edu.stanford.nlp.trees.Tree;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.NumedEventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
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
public class ChineseWhispersCluster implements Callable<Boolean>, SystemConstant {

    private final Logger log                   = Logger.getLogger(this.getClass());

    /** 当前主题路径 */
    private final String topicDir;
    /** node文件所在路径 */
    private final String nodeFilePath;
    /** edge文件所在路径 */
    private final String edgeFilePath;
    /** 允许入边的阈值 */
    private final Float  EDGE_WEIGHT_THRESHOLD = 1.0f;

    public ChineseWhispersCluster(String topicDir, String nodeFilePath, String edgeFilePath) {
        super();
        this.topicDir = topicDir;
        this.nodeFilePath = nodeFilePath;
        this.edgeFilePath = edgeFilePath;
    }

    /**
     * 事件到子句的映射
     *
     * @param eventWithPhrase
     * @param subSentences
     *            当前事件所在句子的子句集合
     * @return
     */
    private String eventToSubSentence(EventWithPhrase eventWithPhrase, List<String> subSentList) {

        String subSentence = null;

        if (eventWithPhrase == null || CollectionUtils.isEmpty(subSentList)) {
            return subSentence;
        }

        List<Word> leftphrase = eventWithPhrase.getLeftPhrases();
        List<Word> middlephrase = eventWithPhrase.getMiddlePhrases();
        List<Word> rightphrase = eventWithPhrase.getRightPhrases();

        int i = subSentList.size() - 1;

        for (; i >= 0; i--) {
            String sentStr = subSentList.get(i);
            int count = 0;
            Set<String> wordSet = new HashSet<String>(Arrays.asList(sentStr.split("\\s+")));
            if (CollectionUtils.isNotEmpty(leftphrase)) {
                for (Word word : leftphrase) {
                    if (!word.getName().equals(word.getPos()) && !STOPWORDS.contains(word.getLemma()) && wordSet.contains(word.getName())) {
                        ++count;
                        break;
                    }
                }

            }
            if (CollectionUtils.isNotEmpty(middlephrase)) {
                for (Word word : middlephrase) {
                    if (!word.getName().equals(word.getPos()) && !STOPWORDS.contains(word.getLemma()) && wordSet.contains(word.getName())) {
                        ++count;
                        break;
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(rightphrase)) {
                for (Word word : rightphrase) {
                    if (!word.getName().equals(word.getPos()) && !STOPWORDS.contains(word.getLemma()) && wordSet.contains(word.getName())) {
                        ++count;
                        break;
                    }
                }
            }
            if (count >= 2 && sentStr.length() >= 8) {
                subSentence = sentStr;
                break;
            }
        }

        return subSentence;
    }

    /**
     * 获取一句话中的所有子句集合
     *
     * @param tree
     * @param subSentList
     */
    private void subSentences(Tree tree, List<String> subSentList) {

        if ("S".equals(tree.label().toString()) || "SINV".equals(tree.label().toString())) {
            StringBuilder sb = new StringBuilder();
            this.subSentence(tree, sb);
            String strTmp = sb.toString().trim();
            if (StringUtils.isNotBlank(strTmp)) {
                subSentList.add(strTmp);
            }
        }

        List<Tree> childTrees = tree.getChildrenAsList();
        for (Tree childTree : childTrees) {
            this.subSentences(childTree, subSentList);
        }

    }

    /**
     * 返回一个s节点下面的完整子句
     *
     * @param tree
     * @param sb
     */
    private void subSentence(Tree tree, StringBuilder sb) {
        if (tree.isLeaf()) {
            sb.append(tree.nodeString() + " ");
            return;
        } else {
            List<Tree> childTrees = tree.getChildrenAsList();
            for (Tree child : childTrees) {
                this.subSentence(child, sb);
            }
        }
    }

    /**
     * 进行同义词替换
     *
     * @param inSent
     * @return
     */
    private String synonymReplacement(String inSent) {
        String outSent = null;

        return outSent;
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
        this.log.info("Chinese Whispers clusting...[" + this.topicDir + "]");
        CW<Integer> cw = new CW<Integer>();
        Map<Integer, Set<Integer>> clusterEvents = cw.findClusters(graph);
        this.log.info("Chinese Whispers clust finished[" + this.topicDir + "]");

        // 加载当前主题下所有的文本
        File objWordsDir = new File(this.topicDir + "/" + OBJ + "/" + DIR_WORDS_OBJ);
        File[] objfiles = objWordsDir.listFiles();
        Map<String, List<List<Word>>> text = new HashMap<String, List<List<Word>>>(25);
        for (File file : objfiles) {
            try{
                this.log.info("[" + Thread.currentThread().getName() + "]Loading serilized file:" + file.getAbsolutePath());
                List<List<Word>> words = (List<List<Word>>) SerializeUtil.readObj(file.getAbsolutePath());
                text.put(file.getName().substring(0, file.getName().lastIndexOf(".")), words);
            } catch (Exception e) {
                this.log.error("Load serilized file error [" + this.edgeFilePath + "]", e);
                throw e;
            }
        }

        // 加载当前主题下所有文本中句子的句法分析树
        File objSyntacticTreesDir = new File(this.topicDir + "/" + OBJ + "/" + DIR_SYNTACTICTREES_OBJ);
        File[] objSyntacticTreefiles = objSyntacticTreesDir.listFiles();
        Map<String, List<Tree>> syntacticTrees = new HashMap<String, List<Tree>>(25);
        for (File file : objSyntacticTreefiles) {
            try{
                this.log.info("[" + Thread.currentThread().getName() + "]Loading serilized file:" + file.getAbsolutePath());
                List<Tree> syntacticTree = (List<Tree>) SerializeUtil.readObj(file.getAbsolutePath());
                syntacticTrees.put(file.getName().substring(0, file.getName().lastIndexOf(".")), syntacticTree);
            } catch (Exception e) {
                this.log.error("Load serilized file error [" + this.edgeFilePath + "]", e);
                throw e;
            }
        }

        Map<Integer, List<String>> clusterSubSentence = new HashMap<Integer, List<String>>();
        //获取当前句子所有的子句集合
        for (Entry<Integer, Set<Integer>> entry : clusterEvents.entrySet()) {
            Integer clusterId = entry.getKey();
            List<String> subSentences = new ArrayList<String>();
            for (Integer eventId : entry.getValue()) {
                EventWithPhrase eventWithPhrase = eventWithNums.get(eventId).getEvent();
                // 获取当前事件所在句子的子句集合
                int sentNum = eventWithPhrase.getSentNum();
                Tree tree = syntacticTrees.get(eventWithPhrase.getFilename()).get(sentNum - 1);
                List<String> subSentList = new ArrayList<String>();
                this.subSentences(tree, subSentList);
                // 将当前事件映射成为子句
                String subSentence = this.eventToSubSentence(eventWithPhrase, subSentList);
                // TODO 对子句进行同义词转换 2015-11-10 21:40:33

                if(StringUtils.isNotBlank(subSentence)) {
                    subSentences.add(subSentence);
                }
            }
            clusterSubSentence.put(clusterId, subSentences);
        }


        return true;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService es = Executors.newSingleThreadExecutor();
        Future<Boolean> future = es.submit(new ChineseWhispersCluster("E:/workspace/test/corpus/D0732H", "E:/workspace/test/nodes/D0732H.node.obj", "E:/workspace/test/edges/D0732H.edge.obj"));
        if(future.get()) {
            System.out.println("success");
        } else {
            System.out.println("false");
        }
        EhCacheUtil.close();
        es.shutdown();
    }

}
