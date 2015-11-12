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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import edu.mit.jwi.IDictionary;
import edu.stanford.nlp.trees.Tree;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.NumedEventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
import edu.whu.cs.nlp.mts.base.utils.Encipher;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import edu.whu.cs.nlp.mts.base.utils.WordNetUtil;
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
public class ChineseWhispersCluster implements Callable<Map<Integer, List<List<Word>>>>, SystemConstant {

    private final Logger log                   = Logger.getLogger(this.getClass());

    /** 当前主题路径 */
    private final String topicDir;
    /** node文件所在路径 */
    private final String nodeFilePath;
    /** edge文件所在路径 */
    private final String edgeFilePath;
    /**wordnet词典文件所在路径*/
    private final String wordnetDataPath;
    /** 允许入边的阈值 */
    private final Float  EDGE_WEIGHT_THRESHOLD = 1.39f;

    public ChineseWhispersCluster(String topicDir, String nodeFilePath, String edgeFilePath, String wordnetDataPath) {
        super();
        this.topicDir = topicDir;
        this.nodeFilePath = nodeFilePath;
        this.edgeFilePath = edgeFilePath;
        this.wordnetDataPath = wordnetDataPath;
    }

    @Override
    public Map<Integer, List<List<Word>>> call() throws Exception {

        Map<Integer, List<List<Word>>> clustedSentence = new HashMap<Integer, List<List<Word>>>();

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
        Map<String, List<List<Word>>> texts = new HashMap<String, List<List<Word>>>(25);
        for (File file : objfiles) {
            try{
                this.log.info("[" + Thread.currentThread().getName() + "]Loading serilized file:" + file.getAbsolutePath());
                List<List<Word>> words = (List<List<Word>>) SerializeUtil.readObj(file.getAbsolutePath());
                texts.put(file.getName().substring(0, file.getName().lastIndexOf(".")), words);
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

        // 同义词替换中，存放当前已经选择的词的指纹信息
        Set<String> selectedWordsKey = new HashSet<String>();

        Map<Integer, List<List<Word>>> clusterSubSentence = new HashMap<Integer, List<List<Word>>>();
        Map<Integer, List<List<Word>>> clusterSubSentenceAfterSynonymReplacement = new HashMap<Integer, List<List<Word>>>();
        //获取当前句子所有的子句集合
        for (Entry<Integer, Set<Integer>> entry : clusterEvents.entrySet()) {

            Integer clusterId = entry.getKey();
            List<List<Word>> subSentences = new ArrayList<List<Word>>();
            List<List<Word>> subSynSentences = new ArrayList<List<Word>>();

            for (Integer eventId : entry.getValue()) {

                EventWithPhrase eventWithPhrase = eventWithNums.get(eventId).getEvent();

                // 获取当前事件所属句子的句法树
                Tree tree = syntacticTrees.get(eventWithPhrase.getFilename()).get(eventWithPhrase.getSentNum() - 1);

                // 利用句法树来获得子句集合
                List<String> subSentList = new ArrayList<String>();
                this.subSentences(tree, subSentList);
                String subSentence = this.eventToSubSentence(eventWithPhrase, subSentList);

                // 获取当前事件所属句子的词集合
                List<Word> words = texts.get(eventWithPhrase.getFilename()).get(eventWithPhrase.getSentNum() - 1);
                List<Word> subObjSentence = this.sentenceObjectified(subSentence, words);
                if(CollectionUtils.isNotEmpty(subObjSentence)) {
                    subSentences.add(subObjSentence);
                }

                // 对句子进行同义词替换
                List<Word> subSynObjSentence  = this.synonymReplacement(subObjSentence, selectedWordsKey);
                if(CollectionUtils.isNotEmpty(subSynObjSentence)) {
                    subSynSentences.add(subSynObjSentence);
                }

            }

            clusterSubSentence.put(clusterId, subSentences);
            clusterSubSentenceAfterSynonymReplacement.put(clusterId, subSynSentences);

        }


        // 持久化聚类的句子文件
        StringBuilder sbClustedSentences = new StringBuilder();
        StringBuilder taggedClustedSentences = new StringBuilder();
        for (Entry<Integer, List<List<Word>>> entry : clusterSubSentence.entrySet()) {
            if(CollectionUtils.isEmpty(entry.getValue()) || entry.getValue().size() < 5) {
                continue;
            }
            sbClustedSentences.append("classes_" + entry.getKey() + ":" + LINE_SPLITER);
            taggedClustedSentences.append("classes_" + entry.getKey() + ":" + LINE_SPLITER);
            for (List<Word> words : entry.getValue()) {
                StringBuilder inner = new StringBuilder();
                StringBuilder tagged = new StringBuilder();
                for (Word word : words) {
                    inner.append(word.getName() + " ");
                    tagged.append(word.getName() + "/" + (word.getPos().equals(word.getName()) ? "PUNCT" : word.getPos()) + " ");
                }
                sbClustedSentences.append(inner.toString().trim() + LINE_SPLITER);
                taggedClustedSentences.append(tagged.toString().trim() + LINE_SPLITER);
            }
        }
        String workDir = this.nodeFilePath.substring(0, this.nodeFilePath.indexOf(DIR_NODES));
        String filename = this.nodeFilePath.substring(Math.max(this.nodeFilePath.lastIndexOf("/"), this.nodeFilePath.lastIndexOf("\\"))).replace("node.obj", "txt");
        File extractedSentences = FileUtils.getFile(workDir + "/" + DIR_SUB_SENTENCES_EXTRACTED, filename);
        File taggedExtractedSentences = FileUtils.getFile(workDir + "/" + DIR_SUB_SENTENCES_EXTRACTED + "/tagged/", filename);
        try{
            this.log.info("Saving clusted sentences to file[" + extractedSentences.getAbsolutePath() + "]");

            FileUtils.writeStringToFile(extractedSentences, CommonUtil.cutLastLineSpliter(sbClustedSentences.toString()), DEFAULT_CHARSET);

            FileUtils.writeStringToFile(taggedExtractedSentences, CommonUtil.cutLastLineSpliter(taggedClustedSentences.toString()), DEFAULT_CHARSET);

            this.log.info("Save clusted sentences to file [" + extractedSentences.getAbsolutePath() + "] succeed!");

        } catch(IOException e) {
            this.log.error("Save clusted sentences to file[" + extractedSentences.getAbsolutePath() + "] error!", e);
        }

        // FIXME 暂时不返回同义词替换的结果，替换的效果不好，需要优化，2015-11-11 16:34:36
        return clusterSubSentence;
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
            if (count >= 2) {
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
            if (StringUtils.isNotBlank(strTmp) && strTmp.split("\\s+").length >= 8) {
                // 长度大于8的子句进入候选
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
     * 将字符串组织的句子替换成{@link Word}组织的句子
     *
     * @param strSentence
     * @param words
     * @return
     */
    public List<Word> sentenceObjectified(String strSentence, List<Word> words) {
        List<Word> objSentence = new ArrayList<Word>();
        if(StringUtils.isBlank(strSentence)) {
            return objSentence;
        }
        String[] strs = strSentence.split("\\s+");
        int num = 0;
        for (Word word : words) {
            if(strs[num].equals(word.getName())) {
                objSentence.add(word);
                num++;
                if(num == strs.length) {
                    break;
                }
            } else if(num > 0) {
                num = 0;
                objSentence.clear();
            }
        }
        if(num != strs.length) {
            objSentence.clear();
        }
        return objSentence;
    }

    /**
     * 进行同义词替换
     *
     * @param inSent
     * @return
     */
    private List<Word> synonymReplacement(List<Word> sentence, Set<String> selectedWordsKey) throws Exception{
        List<Word> outSent = new ArrayList<Word>();
        try {
            IDictionary dict = WordNetUtil.openDictionary(this.wordnetDataPath);
            for (Word word : sentence) {
                try {
                    List<Word> synonymsWords = WordNetUtil.getSynonyms(dict, word);
                    if(CollectionUtils.isEmpty(synonymsWords)) {
                        // 不存在同义词
                        outSent.add((Word)word.clone());
                        selectedWordsKey.add(Encipher.MD5(word.getLemma() + word.getPos()));
                    } else {
                        // 存在同义词
                        boolean flag = false;
                        for (Word synonymsWord : synonymsWords) {
                            String fingerprint = Encipher.MD5(synonymsWord.getLemma() + synonymsWord.getPos());
                            if(selectedWordsKey.contains(fingerprint)) {
                                outSent.add(synonymsWord);
                                flag = true;
                                break;
                            }
                        }

                        if(!flag) {
                            // 将自己作为同义词
                            outSent.add((Word)word.clone());
                            selectedWordsKey.add(Encipher.MD5(word.getLemma() + word.getPos()));
                        }

                    }
                } catch(CloneNotSupportedException e) {
                    this.log.error("Synonymr replacement with word[" + word + "] error!", e);
                    throw e;
                }

            }

        } catch (IOException e) {
            this.log.error("Get dictionary[" + this.wordnetDataPath + "] error!", e);
            throw e;
        }
        return outSent;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService es = Executors.newSingleThreadExecutor();
        Future<Map<Integer, List<List<Word>>>> future = es.submit(new ChineseWhispersCluster("E:/workspace/test/corpus/D0732H", "E:/workspace/test/nodes/D0732H.node.obj", "E:/workspace/test/edges/D0732H.edge.obj", "D:/Program Files (x86)/WordNet/2.1/dict"));
        if(future.get() != null) {
            System.out.println("success");
        } else {
            System.out.println("false");
        }
        EhCacheUtil.close();
        es.shutdown();
    }

}
