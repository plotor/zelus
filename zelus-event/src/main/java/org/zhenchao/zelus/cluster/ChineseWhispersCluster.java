package org.zhenchao.zelus.cluster;

import edu.mit.jwi.IDictionary;
import edu.stanford.nlp.trees.Tree;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.cluster.cw.CW;
import org.zhenchao.zelus.cluster.cw.graph.ArrayBackedGraph;
import org.zhenchao.zelus.cluster.cw.graph.Graph;
import org.zhenchao.zelus.cluster.domain.CWEdge;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.GlobalParam;
import org.zhenchao.zelus.common.pojo.EventWithPhrase;
import org.zhenchao.zelus.common.pojo.NumedEventWithPhrase;
import org.zhenchao.zelus.common.pojo.Pair;
import org.zhenchao.zelus.common.pojo.Vector;
import org.zhenchao.zelus.common.pojo.Word;
import org.zhenchao.zelus.common.util.Encipher;
import org.zhenchao.zelus.common.util.SerializeUtils;
import org.zhenchao.zelus.common.util.VectorOperator;
import org.zhenchao.zelus.common.util.WordNetUtil;
import org.zhenchao.zelus.common.util.ZelusUtils;

import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Chinese Whispers algorithmClustering
 *
 * @author zhenchao 2015-11-10 14:23:27
 */
public class ChineseWhispersCluster implements Callable<Boolean>, Constants {

    private final Logger log = Logger.getLogger(this.getClass());

    private static final String CLASS_PREFIX = "classes_";

    /** Current topic路径 */
    private final String topicDir;
    /** node文件所在路径 */
    private final String nodeFilePath;
    /** edge文件所在路径 */
    private final String edgeFilePath;
    /** Word vector dictionary文件所在路径 */
    private final String wordvecDictPath;

    public ChineseWhispersCluster(String topicDir, String nodeFilePath, String edgeFilePath, String wordvecDictPath) {
        super();
        this.topicDir = topicDir;
        this.nodeFilePath = nodeFilePath;
        this.edgeFilePath = edgeFilePath;
        this.wordvecDictPath = wordvecDictPath;
    }

    @Override
    public Boolean call() throws Exception {

        String topicName = this.topicDir.substring(Math.max(this.topicDir.lastIndexOf("/"), this.topicDir.lastIndexOf("\\")));

        // 数字格式化器
        DecimalFormat decimalFormat = new DecimalFormat("0.######");
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

        Map<Integer, List<List<Word>>> clustedSentence = new HashMap<Integer, List<List<Word>>>();

        /**
         * 加载node文件
         */
        this.log.info("Thread " + Thread.currentThread().getId() + " -> loading serilized file:" + this.nodeFilePath);
        @SuppressWarnings("unchecked")
        Map<Integer, NumedEventWithPhrase> eventWithNums = (Map<Integer, NumedEventWithPhrase>) SerializeUtils.readObj(this.nodeFilePath);
        if (MapUtils.isEmpty(eventWithNums)) {
            this.log.error("Can't load any node data from [" + this.nodeFilePath + "]");
            throw new IOException("Can't load any node data from [" + this.nodeFilePath + "]");
        }

        /**
         * 加载edge文件
         */
        this.log.info("Thread " + Thread.currentThread().getId() + " -> loading serilized file:" + this.edgeFilePath);
        @SuppressWarnings("unchecked")
        List<CWEdge> cwEdges = (List<CWEdge>) SerializeUtils.readObj(this.edgeFilePath);
        if (CollectionUtils.isEmpty(cwEdges)) {
            this.log.error("Can't load any edge data from [" + this.edgeFilePath + "]");
            throw new IOException("Can't load any edge data from [" + this.edgeFilePath + "]");
        }

        /**
         * Load word vectors字典文件
         */
        this.log.info("Thread " + Thread.currentThread().getId() + " -> loading serilized file:" + this.wordvecDictPath);
        @SuppressWarnings("unchecked")
        Map<String, Vector> vecDict = (Map<String, Vector>) SerializeUtils.readObj(this.wordvecDictPath);
        if (MapUtils.isEmpty(vecDict)) {
            this.log.error("Can't load any word vector dict data from [" + this.wordvecDictPath + "]");
            throw new IOException("Can't load any word vector dict data from [" + this.wordvecDictPath + "]");
        }

        /**
         * BuildEvent图
         */
        Graph<Integer, Float> graph = new ArrayBackedGraph<Float>(eventWithNums.size(), eventWithNums.size());
        // 添加结点
        for (Entry<Integer, NumedEventWithPhrase> entry : eventWithNums.entrySet()) {
            graph.addNode(entry.getValue().getNum());
        }
        // Calculate所有边权值的均值
        float totalWeight = 0.0f;
        for (CWEdge cwEdge : cwEdges) {
            totalWeight += cwEdge.getWeight();
        }
        float avgWeight = totalWeight / cwEdges.size();
        // 添加边
        for (CWEdge cwEdge : cwEdges) {
            if (cwEdge.getWeight() >= avgWeight * GlobalParam.edgeWeightThresh) {
                graph.addEdgeUndirected(cwEdge.getFrom(), cwEdge.getTo(), cwEdge.getWeight());
            }
        }

        /**
         * 采用Chinese Whispers algorithm进行Clustering
         */
        this.log.info("Thread " + Thread.currentThread().getId() + " -> Chinese Whispers clusting[" + this.topicDir + "]");
        CW<Integer> cw = new CW<Integer>();
        Map<Integer, Set<Integer>> clusterEvents = cw.findClusters(graph);
        this.log.info("Thread " + Thread.currentThread().getId() + " -> Chinese Whispers clust finished[" + this.topicDir + "]");

        /**
         * 加载Current topic下所有的文本
         */
        File objWordsDir = new File(GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_WORDS_OBJ + "/" + topicName);
        File[] objfiles = objWordsDir.listFiles();
        Map<String, List<List<Word>>> texts = new HashMap<String, List<List<Word>>>(25);
        for (File file : objfiles) {
            try {
                this.log.info("Thread " + Thread.currentThread().getId() + " -> loading serilized file:" + file.getAbsolutePath());
                List<List<Word>> words = (List<List<Word>>) SerializeUtils.readObj(file.getAbsolutePath());
                texts.put(file.getName().substring(0, file.getName().lastIndexOf(".")), words);
            } catch (Exception e) {
                this.log.error("Thread " + Thread.currentThread().getId() + " -> load serilized file error [" + this.edgeFilePath + "]", e);
                throw e;
            }
        }

        /**
         * 加载Current topic下所有文本中句子的句法分析树
         */
        File objSyntacticTreesDir = new File(GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_SYNTACTICTREES_OBJ + "/" + topicName);
        File[] objSyntacticTreefiles = objSyntacticTreesDir.listFiles();
        Map<String, List<Tree>> syntacticTrees = new HashMap<String, List<Tree>>(25);
        for (File file : objSyntacticTreefiles) {
            try {
                this.log.info("Thread " + Thread.currentThread().getId() + " -> loading serilized file:" + file.getAbsolutePath());
                List<Tree> syntacticTree = (List<Tree>) SerializeUtils.readObj(file.getAbsolutePath());
                syntacticTrees.put(file.getName().substring(0, file.getName().lastIndexOf(".")), syntacticTree);
            } catch (Exception e) {
                this.log.error("Thread " + Thread.currentThread().getId() + " -> load serilized file error [" + this.edgeFilePath + "]", e);
                throw e;
            }
        }

        /**
         * 子句Map，同义Word替换
         */
        // 同义Word替换，存放当前已经选择的Word的指纹Information
        Set<String> selectedWordsKey = new HashSet<String>();
        Map<Integer, List<List<Word>>> clusterSubSentence = new HashMap<Integer, List<List<Word>>>();
        Map<Integer, List<List<Word>>> clusterSubSentenceAfterSynonymReplacement = new HashMap<Integer, List<List<Word>>>();
        Map<Integer, List<Pair<NumedEventWithPhrase, Double>>> clusterEventWeihts = new HashMap<Integer, List<Pair<NumedEventWithPhrase, Double>>>();

        /*
         * 存放每个Class的权值
         * CalculateMethod：一个cluster中包含的文档的数量之和（每个文档计数为1）
         */
        Map<String, Float> clusterWeights = new HashMap<String, Float>();

        //获取Current sentence子所有的子句Collection
        for (Entry<Integer, Set<Integer>> entry : clusterEvents.entrySet()) {

            if (CollectionUtils.isEmpty(entry.getValue())) {
                continue;
            }

            Set<String> filenames4ClusterWeight = new HashSet<String>();

            List<Double[]> vectorsInCluster = new ArrayList<Double[]>();
            for (Integer eventNum : entry.getValue()) {
                NumedEventWithPhrase numedEventWithPhrase = eventWithNums.get(eventNum);
                vectorsInCluster.add(numedEventWithPhrase.getVec());
                // 以文件名来Calculate一个cluster的包含的来源文件的数目，以此度量一个cluster的主题贡献
                filenames4ClusterWeight.add(numedEventWithPhrase.getEvent().getFilename());
            }

            // 以一个Class包含的总的文件数量来度量该Class的权重
            clusterWeights.put(CLASS_PREFIX + entry.getKey(), (float) filenames4ClusterWeight.size());

            // CalculateVector中心
            Double[] centralVec = VectorOperator.centralVector(vectorsInCluster);
            if (centralVec == null) {
                this.log.error("[" + entry.getKey() + "]Calculate central vector error!");
                continue;
            }

            Integer clusterId = entry.getKey();
            List<List<Word>> subSentences = new ArrayList<List<Word>>();
            List<List<Word>> subSynSentences = new ArrayList<List<Word>>();
            List<Pair<NumedEventWithPhrase, Double>> eventWeights = new ArrayList<Pair<NumedEventWithPhrase, Double>>();
            for (Integer eventId : entry.getValue()) {

                NumedEventWithPhrase numedEventWithPhrase = eventWithNums.get(eventId);

                EventWithPhrase eventWithPhrase = numedEventWithPhrase.getEvent();

                // Calculate当前Event到Vector中心的距离
                double eventWeight = VectorOperator.cosineDistence(centralVec, numedEventWithPhrase.getVec());
                eventWeights.add(new Pair<NumedEventWithPhrase, Double>(numedEventWithPhrase, eventWeight));

                // 获取当前Event所属句子的Syntactic tree
                Tree tree = syntacticTrees.get(eventWithPhrase.getFilename()).get(eventWithPhrase.getSentNum() - 1);
                // 利用Syntactic tree来获得子句Collection
                List<String> subSentList = new ArrayList<String>();
                this.subSentences(tree, subSentList);
                String subSentence = this.eventToSubSentence(eventWithPhrase, subSentList);

                // 获取当前Event所属句子的WordCollection
                List<Word> words = texts.get(eventWithPhrase.getFilename()).get(eventWithPhrase.getSentNum() - 1);
                List<Word> subObjSentence = this.sentenceObjectified(subSentence, words);
                if (CollectionUtils.isNotEmpty(subObjSentence)) {
                    subSentences.add(subObjSentence);
                }

                // Pairs句子进行同义Word替换
                List<Word> subSynObjSentence = this.synonymReplacement(subObjSentence, selectedWordsKey);
                if (CollectionUtils.isNotEmpty(subSynObjSentence)) {
                    subSynSentences.add(subSynObjSentence);
                }

            }

            clusterEventWeihts.put(clusterId, eventWeights);
            clusterSubSentence.put(clusterId, subSentences);
            clusterSubSentenceAfterSynonymReplacement.put(clusterId, subSynSentences);

        }

        String filename = this.nodeFilePath.substring(Math.max(this.nodeFilePath.lastIndexOf("/"), this.nodeFilePath.lastIndexOf("\\"))).replace("node.obj", "txt");

        // Serializingcluster权重
        File clusterWeightsFile = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + '/' + OBJ + "/" + DIR_CLUSTER_WEIGHT, filename.replaceAll("txt", OBJ));
        try {
            this.log.info("Thread " + Thread.currentThread().getId() + " -> serilizing cluster weight to file[" + clusterWeightsFile.getAbsolutePath() + "]");
            SerializeUtils.writeObj(clusterWeights, clusterWeightsFile);
        } catch (IOException e) {
            this.log.error("Thread " + Thread.currentThread().getId() + " -> serilizing cluster weight to file[" + clusterWeightsFile.getAbsolutePath() + "] error!", e);
            throw e;
        }

        /*
         * 持久化Clustering的句子Collection
         */
        StringBuilder sbClustedSentences = new StringBuilder();
        StringBuilder taggedClustedSentences = new StringBuilder();
        StringBuilder taggedWeightedClustedSentences = new StringBuilder();
        for (Entry<Integer, List<List<Word>>> entry : clusterSubSentence.entrySet()) {

            if (CollectionUtils.isEmpty(entry.getValue()) || entry.getValue().size() < 5) {
                // 跳过小于5个句子的Class
                continue;
            }

            List<Pair<NumedEventWithPhrase, Double>> pairs = clusterEventWeihts.get(entry.getKey());
            sbClustedSentences.append(CLASS_PREFIX + entry.getKey() + ":" + LINE_SPLITER);
            taggedClustedSentences.append(CLASS_PREFIX + entry.getKey() + ":" + LINE_SPLITER);
            taggedWeightedClustedSentences.append(CLASS_PREFIX + entry.getKey() + ":" + LINE_SPLITER);
            for (List<Word> words : entry.getValue()) {
                StringBuilder inner = new StringBuilder();
                StringBuilder tagged = new StringBuilder();
                StringBuilder weighted = new StringBuilder();
                for (Word word : words) {
                    /* Calculate当前Word与当前Class别中Event的加权距离
                     * Calculate方式：
                     *     当前Word与每个Event的距离*Event的权值，然后取平均
                     */
                    Vector wordVec = vecDict.get(word.dictKey());
                    double wordWeight = 0.0D;
                    if (wordVec != null) {
                        for (Pair<NumedEventWithPhrase, Double> pair : pairs) {
                            Double[] eventVec = pair.getLeft().getVec();
                            if (null != eventVec) {
                                double distence = VectorOperator.cosineDistence(wordVec.doubleVecs(), eventVec);
                                if (distence > 0) {
                                    wordWeight += distence * pair.getRight();
                                }
                            }
                        }
                        wordWeight /= pairs.size();
                        // 格式化
                        wordWeight = Double.parseDouble(decimalFormat.format(wordWeight));
                    }

                    inner.append(word.getName() + " ");
                    tagged.append(word.getName() + "/" + (word.getPos().equals(word.getName()) ? "PUNCT" : word.getPos()) + " ");
                    weighted.append(word.getName() + "/" + (word.getPos().equals(word.getName()) ? "PUNCT" : word.getPos()) + "/" + wordWeight + " ");
                }
                sbClustedSentences.append(inner.toString().trim() + LINE_SPLITER);
                taggedClustedSentences.append(tagged.toString().trim() + LINE_SPLITER);
                taggedWeightedClustedSentences.append(weighted.toString().trim() + LINE_SPLITER);
            }
        }

        File extractedSentences = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + TEXT + "/" + DIR_SUB_SENTENCES_EXTRACTED, filename);
        File taggedExtractedSentences = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + TEXT + "/" + DIR_SUB_SENTENCES_EXTRACTED + "/tagged/", filename);
        File weightedExtractedSentences = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + TEXT + "/" + DIR_SUB_SENTENCES_EXTRACTED + "/weighted/", filename);

        try {

            this.log.info("Thread " + Thread.currentThread().getId() + " -> saving clusted sentences to file[" + extractedSentences.getAbsolutePath() + "]");
            FileUtils.writeStringToFile(extractedSentences, ZelusUtils.cutLastLineSpliter(sbClustedSentences.toString()), DEFAULT_CHARSET);
            FileUtils.writeStringToFile(taggedExtractedSentences, ZelusUtils.cutLastLineSpliter(taggedClustedSentences.toString()), DEFAULT_CHARSET);
            FileUtils.writeStringToFile(weightedExtractedSentences, ZelusUtils.cutLastLineSpliter(taggedWeightedClustedSentences.toString()), DEFAULT_CHARSET);
            this.log.info("Thread " + Thread.currentThread().getId() + " -> save clusted sentences to file [" + extractedSentences.getAbsolutePath() + "] succeed!");

        } catch (IOException e) {
            this.log.error("Thread " + Thread.currentThread().getId() + " -> save clusted sentences to file[" + extractedSentences.getAbsolutePath() + "] error!", e);
        }

        /**
         * 持久化Event的权值
         */
        /*StringBuilder sbEventWeights = new StringBuilder();
        for (Entry<Integer, List<Pair<NumedEventWithPhrase, Double>>> entry : clusterEventWeihts.entrySet()) {
            if(CollectionUtils.isEmpty(entry.getValue()) || entry.getValue().size() < 5) {
                // 跳过小于5个句子的Class
                continue;
            }
            sbEventWeights.append("classes_" + entry.getKey() + ":" + LINE_SPLITER);
            for (String eventWeight : entry.getValue()) {
                sbEventWeights.append(eventWeight + LINE_SPLITER);
            }
        }

        File eventsWeightFile = FileUtils.getFile(workDir + "/" + DIR_EVENT_WEIGHT, filename);
        try{
            this.log.info("Saving clusted sentences to file[" + eventsWeightFile.getAbsolutePath() + "]");

            FileUtils.writeStringToFile(eventsWeightFile, ZelusUtils.cutLastLineSpliter(sbEventWeights.toString()), DEFAULT_CHARSET);

            this.log.info("Save clusted sentences to file [" + eventsWeightFile.getAbsolutePath() + "] succeed!");

        } catch(IOException e) {

            this.log.error("Save clusted sentences to file[" + eventsWeightFile.getAbsolutePath() + "] error!", e);

        }*/

        return true;
    }

    /**
     * Event到子句的Map
     *
     * @param eventWithPhrase
     * @param subSentList 当前Event所在句子的子句Collection
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
     * 获取一句话中的所有子句Collection
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
     * 将String组织的句子替换成{@link Word}组织的句子
     *
     * @param strSentence
     * @param words
     * @return
     */
    private List<Word> sentenceObjectified(String strSentence, List<Word> words) {
        List<Word> objSentence = new ArrayList<Word>();
        if (StringUtils.isBlank(strSentence)) {
            return objSentence;
        }
        String[] strs = strSentence.split("\\s+");
        int num = 0;
        for (Word word : words) {
            if (strs[num].equals(word.getName())) {
                objSentence.add(word);
                num++;
                if (num == strs.length) {
                    break;
                }
            } else if (num > 0) {
                num = 0;
                objSentence.clear();
            }
        }
        if (num != strs.length) {
            objSentence.clear();
        }
        return objSentence;
    }

    /**
     * 进行同义Word替换
     *
     * @return
     */
    private List<Word> synonymReplacement(List<Word> sentence, Set<String> selectedWordsKey) throws Exception {
        List<Word> outSent = new ArrayList<Word>();
        try {
            IDictionary dict = WordNetUtil.openDictionary(GlobalParam.wordnetDictPath);
            for (Word word : sentence) {
                try {
                    List<Word> synonymsWords = WordNetUtil.getSynonyms(dict, word);
                    if (CollectionUtils.isEmpty(synonymsWords)) {
                        // does not exist同义Word
                        outSent.add((Word) word.clone());
                        selectedWordsKey.add(Encipher.MD5(word.getLemma() + word.getPos()));
                    } else {
                        // 存在同义Word
                        boolean flag = false;
                        for (Word synonymsWord : synonymsWords) {
                            String fingerprint = Encipher.MD5(synonymsWord.getLemma() + synonymsWord.getPos());
                            if (selectedWordsKey.contains(fingerprint)) {
                                outSent.add(synonymsWord);
                                flag = true;
                                break;
                            }
                        }

                        if (!flag) {
                            // Treat self as synonymous word
                            outSent.add((Word) word.clone());
                            selectedWordsKey.add(Encipher.MD5(word.getLemma() + word.getPos()));
                        }

                    }
                } catch (CloneNotSupportedException e) {
                    this.log.error("Synonymr replacement with word[" + word + "] error!", e);
                    throw e;
                }

            }

        } catch (IOException e) {
            this.log.error("Get dictionary[" + GlobalParam.wordnetDictPath + "] error!", e);
            throw e;
        }
        return outSent;
    }

}
