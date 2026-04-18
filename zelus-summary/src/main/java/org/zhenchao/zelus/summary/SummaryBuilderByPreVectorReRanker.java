package org.zhenchao.zelus.summary;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.GlobalParam;
import org.zhenchao.zelus.common.nlp.StanfordNLPTools;
import org.zhenchao.zelus.common.pojo.Pair;
import org.zhenchao.zelus.common.pojo.Vector;
import org.zhenchao.zelus.common.pojo.Word;
import org.zhenchao.zelus.common.util.SerializeUtils;
import org.zhenchao.zelus.common.util.VectorOperator;
import org.zhenchao.zelus.common.util.ZelusUtils;
import org.zhenchao.zelus.domain.ClustItemPlus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-document summarization based on submodular function (using vectors to measure sentence similarity)<br>
 * Vector不实时获取，采用Preprocessing， 并且先Pairsclust按照path得分均值排序
 *
 * @author zhenchao 2016-1-27 20:24:18
 */
public class SummaryBuilderByPreVectorReRanker implements Callable<Boolean>, Constants {

    private static final Logger log = Logger.getLogger(SummaryBuilderByPreVectorReRanker.class);

    /**
     * Classification directory for running multiple tasks simultaneously
     */
    private final String numDir;

    /**
     * Topic filename
     */
    private final String filename;

    /**
     * Topic name
     */
    private final String topicname;

    /**
     * IDF values
     */
    Map<String, Double> idfValues;

    /**
     * topic query
     */
    private final String question;

    /** Word vector retriever */
    /*private final EhCacheUtil ehCacheUtil;*/

    /**
     * 当前duc下所有Word的WordVector
     */
    private final Map<String, Vector> wordVecs;

    /**
     * Alpha parameter
     */
    private final float alpha;

    /**
     * Beta parameter
     */
    private final float beta;

    /**
     * 相似度上限
     */
    private final float similarityThresh;

    /**
     * Number of sentences selected per topic
     */
    private Integer sentCountInClust = 10;

    public SummaryBuilderByPreVectorReRanker(String numDir, String filename, int sentCountInClust, Map<String, Double> idfValues, String question, Map<String, Vector> wordVecs, float alpha, float beta, float similarityThresh) {
        super();
        this.numDir = StringUtils.isEmpty(numDir) ? "" : ("/" + numDir.trim());
        this.filename = filename;
        this.topicname = this.filename.substring(0, this.filename.length() - 4);
        this.sentCountInClust = Integer.valueOf(sentCountInClust);
        this.idfValues = idfValues;
        this.question = question;
        this.wordVecs = wordVecs;
        this.alpha = alpha;
        this.beta = beta;
        this.similarityThresh = similarityThresh;
    }

    @Override
    public Boolean call() throws Exception {

        log.info("[Thread id:" + Thread.currentThread().getId() + "] is building summary for[" + GlobalParam.workDir + "/" + DIR_SUMMARY_RESULTS + "/" + DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");

        // 加载Current topic下面的句子，每个Class别控制句子数量
        List<ClustItemPlus> candidateSentences = this.loadSentences(this.sentCountInClust);

        // 按照clust的权重Pairsclust重排序
        Collections.sort(candidateSentences);

        // 加载每个clust的权值
        String clusterWeightFilepath = GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + OBJ + "/" + DIR_CLUSTER_WEIGHT + "/" + this.filename.substring(0, this.filename.length() - 4) + "." + OBJ;
        log.info("loading serilized file[" + clusterWeightFilepath + "]");
        Map<String, Float> clusterWeights = null;
        try {
            clusterWeights = (Map<String, Float>) SerializeUtils.readObj(clusterWeightFilepath);
        } catch (IOException e) {
            log.error("load serilized file[" + clusterWeightFilepath + "] error!", e);
            throw e;
        }
        log.info("load serilized file[" + clusterWeightFilepath + "] successed!");

        /*
         * 在保证摘要总字数不超过规定字数的前提下， 按照句子的Overall score（主题贡献分，Query覆盖度，多样性得分）循环从候选句子中选取句子
         */
        // Current summary word count
        int summaryWordCount = 0;

        // Current summary sentence count
        int summarySentenceCount = 0;

        // Check if there are still candidate sentences
        boolean isNotEmpty = true;

        // Tokenize the question and calculate sentence vector
        List<Word> questionWords = StanfordNLPTools.segmentWord(this.question.trim());
        Double[] questionVec = this.sentenceToVector(questionWords);

        if (questionVec == null) {
            log.error("The question[" + this.question + "] vector is null!");
            return false;
        }

        /* Intermediate and final summary values, organized by cluster */
        Map<String, List<Pair<Float, String>>> partialSummary = new HashMap<String, List<Pair<Float, String>>>();

        /* Sentence vectors in intermediate summary */
        List<Double[]> psVectors = new ArrayList<Double[]>();

        /* Word frequency in intermediate summary */
        Map<String, Integer> wordFrequencyInPartialSummary = new HashMap<String, Integer>();

        /* Cache diversity score for each cluster in the summary */
        //Map<String, Double> clusterDiversies = new HashMap<String, Double>();

        while (isNotEmpty && summaryWordCount < MAX_SUMMARY_WORDS_COUNT) {

            isNotEmpty = false;

            // Record current maximum overall score
            float maxGeneralScore = Float.NEGATIVE_INFINITY;
            // Calculate cluster name with maximum overall score
            String selectedClustName = null;
            // 记录最大Overall scorePairs应的clust
            ClustItemPlus selectedClust = null;
            // Record sentence index with maximum overall score
            Pair<Float, String> selectedSentence = null;
            // Record new diversity score for the cluster with maximum overall score
            //double selectedClustDiversityScore = -1.0D;

            for (ClustItemPlus clustItem : candidateSentences) {

                // Current cluster name
                String clustname = clustItem.getName();

                // Remaining candidate sentences in current cluster
                List<Pair<Float, String>> pairs = clustItem.getSentences();

                if (CollectionUtils.isEmpty(pairs)) {
                    // No more candidate sentences in current cluster
                    continue;
                }

                // There are still candidate sentences
                isNotEmpty = true;

                // Get current cluster weight
                float currentClusterWeight = clusterWeights.get(clustname);

                /* Historical diversity score */
                float historyDiversityScore = 0.0f;

                // Overall score
                float generalScore = 0.0f;

                // Iterate over sentences in current cluster
                Iterator<Pair<Float, String>> pairItr = pairs.iterator();
                while (pairItr.hasNext()) {
                    Pair<Float, String> pair = pairItr.next();
                    // 1.Calculate topic contribution score for current sentence
                    float topicScore = currentClusterWeight / (pair.getLeft() * clustItem.getSize());
                    // float topicScore = 0.001f / pair.getLeft();

                    // 2.Calculate query coverage for current sentence
                    float queryScore = 0.0f;

                    String sentence = pair.getRight();

                    // CalculateCurrent sentence子与问句的相似度
                    List<Word> words = StanfordNLPTools.segmentWord(sentence.trim());

                    // CalculateCurrent sentence子的长度，Ignore sentences shorter than 8 words
                    if (ZelusUtils.lessThanEight(words)) {
                        pairItr.remove();
                        continue;
                    }

                    Double[] sentVec = this.sentenceToVector(words);

                    if (null == sentVec) {
                        log.info("The sentence[" + sentence + "] vector is null, ingore it!");
                        pairItr.remove();
                        continue;
                    }

                    queryScore = (float) VectorOperator.cosineDistence(sentVec, questionVec);

                    // 3.Calculate diversity score for current sentence
                    //double diversityScore = 0.0;

                    // Similarity score between current sentence and existing summary
                    double similarityScore = 0.0;
                    boolean tooSimilarity = false; // True if a candidate sentence has identical word composition as the current sentence
                    for (Double[] psVec : psVectors) {
                        double sps = VectorOperator.cosineDistence(sentVec, psVec);
                        if (sps > this.similarityThresh) {
                            tooSimilarity = true;
                            break;
                        }
                        if (sps > 0) {
                            similarityScore += sps;
                        }
                    }

                    if (tooSimilarity) {
                        // Current sentence has identical word composition to an already selected sentence, skip
                        pairItr.remove();
                        continue;
                    }

                    if (psVectors.size() > 0) {
                        similarityScore /= psVectors.size();
                    }

                    // CalculateOverall score
                    queryScore = (float) Math.log(queryScore + 1);
                    topicScore = (float) (this.sigmoid(Math.log(topicScore + 1)) - 0.5) * 4 * this.alpha;
                    similarityScore = Math.log(similarityScore + 1) * this.beta;

                    log.debug("[alpha=" + this.alpha + ", beta= " + this.beta + "]query score:" + queryScore + ",\ttopic score:" + topicScore + ",\tsimilarity score:" + similarityScore);
                    generalScore = (float) (queryScore + topicScore - similarityScore);

                    if (generalScore > maxGeneralScore) {
                        maxGeneralScore = generalScore;
                        selectedClustName = clustname;
                        selectedClust = clustItem;
                        selectedSentence = pair;
                        //selectedClustDiversityScore = diversityScore;
                        log.info("[best in clust, alpha=" + this.alpha + ", beta=" + this.beta + "]" + generalScore + "\t" + "query score:" + queryScore + ",\ttopic score:" + topicScore + ",\tsimilarity score:" + similarityScore + "\t" + pair.getRight());
                    }

                }

            }

            // Update selected summary
            if (null == selectedClustName || null == selectedClust || null == selectedSentence) {
                log.warn("[" + this.topicname + "]Selected clust or sentence is illegal[selectedClustName = " + selectedClustName + ", selectedSentence = " + selectedSentence + "]");
                continue;
            }

            // Select the best sentence from candidates and add to summary, then remove from candidates
            List<Pair<Float, String>> sentences = selectedClust.getSentences();
            int num = -1;
            for (int i = 0; i < sentences.size(); i++) {
                Pair<Float, String> sent = sentences.get(i);
                if (selectedSentence.getRight().equals(sent.getRight())) {
                    num = i;
                    break;
                }
            }

            if (num == -1) {
                log.error("The sentence num is illegal:" + num);
                return false;
            }

            Pair<Float, String> ss = sentences.remove(num);
            List<Pair<Float, String>> clustSentencesInSummary = partialSummary.get(selectedClustName);
            if (null == clustSentencesInSummary) {
                clustSentencesInSummary = new ArrayList<Pair<Float, String>>();
                partialSummary.put(selectedClustName, clustSentencesInSummary);
                log.info("-->\t" + ss.getRight());
            }
            clustSentencesInSummary.add(ss);

            // Update相关Data
            List<Word> words = StanfordNLPTools.segmentWord(ss.getRight());
            Double[] sentvec = this.sentenceToVector(words);
            if (sentvec == null) {
                continue;
            }
            psVectors.add(sentvec);

            // 1.Update summary word count
            for (Word word : words) {
                if (ZelusUtils.isPunctuation(word)) {
                    continue;
                }
                if ("'s".equals(word.getName())) {
                    continue;
                }
                ++summaryWordCount;
            }

            // 2.Update summary sentence count
            ++summarySentenceCount;

            // 3.Update word frequency in summary
            for (Word word : words) {
                Integer freq = wordFrequencyInPartialSummary.get(word.getName().toLowerCase());
                if (null == freq) {
                    freq = 0;
                }
                freq += 1;
                wordFrequencyInPartialSummary.put(word.getName(), freq);
            }

            // 4.UpdatepsVectors
            //clusterDiversies.put(selectedClustName, selectedClustDiversityScore);

        }

        // Save summary
        StringBuilder summary = new StringBuilder();
        for (Entry<String, List<Pair<Float, String>>> entry : partialSummary.entrySet()) {
            for (Pair<Float, String> pair : entry.getValue()) {
                String sentence = pair.getRight();
                sentence = sentence.replaceAll("''\\s+", "").replaceAll("``\\s+", "").replaceAll("'\\s+", "").replaceAll("`\\s+", "");
                sentence = sentence.replaceAll("\\s+", " ");
                sentence = sentence.replaceAll("\\s+'s", "'s");
                /*sentence = sentence.replaceAll("-lrb-[\\s\\S]*?-rrb-\\s+", "");*/
                sentence = sentence.replaceAll("-lrb-", "");
                sentence = sentence.replaceAll("-rrb-", "");
                sentence = sentence.endsWith(".") ? (sentence.trim() + "\n") : (sentence.trim() + ".\n");
                summary.append(sentence);
            }
        }

        int indexOfPoint = this.filename.lastIndexOf(".");
        String summaryFilename = this.filename.substring(0, indexOfPoint - 1).toUpperCase() + ".M.250." + this.filename.substring(indexOfPoint - 1, indexOfPoint).toUpperCase() + ".3";
        try {
            File file = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_SUMMARY_RESULTS + "/" + DIR_SUMMARIES_V2 + this.numDir, summaryFilename);
            log.info("Saving summary to file[" + file.getAbsolutePath() + "]");
            FileUtils.writeStringToFile(file, summary.toString().trim(), DEFAULT_CHARSET);
        } catch (IOException e) {
            log.error("Save summary[" + this.filename + "] error!", e);
            throw e;
        }

        log.info("[Thread id:" + Thread.currentThread().getId() + "] build summary for[" + this.topicname + "] finished!");
        return true;
    }

    /**
     * Sigmoid function
     *
     * @param x
     * @return
     */
    private double sigmoid(double x) {
        return 1 / (1 + Math.pow(Math.E, -x));
    }

    /**
     * Calculate vector for input sentence
     *
     * @param words
     * @return
     */
    private Double[] sentenceToVector(List<Word> words) {

        Double[] vector = new Double[DIMENSION];
        Arrays.fill(vector, 0.0D);

        int count = 0;
        boolean flag = false;
        for (Word word : words) {

            if (ZelusUtils.isPunctuation(word)) {
                // Skip punctuation
                continue;
            }

            /*if (STOPWORDS.contains(word.getLemma())) {
                // Skip stopwords
                continue;
            }*/

            try {
                /*Vector vec = this.ehCacheUtil.getMostSimilarVec(word);*/
                Vector vec = this.wordVecs.get(word.getName() + "-" + word.getPos());
                if (vec == null) {
                    // Skip if word vector not found
                    continue;
                }
                Float[] floatVec = vec.floatVecs();
                for (int i = 0; i < DIMENSION; i++) {
                    vector[i] += floatVec[i];
                    if (!flag) {
                        flag = true;
                    }
                }
                count++;
            } catch (Exception e) {
                log.error("Get word[" + word + "] vector error!", e);
            }
        }

        if (count > 0) {
            for (int i = 0; i < DIMENSION; i++) {
                vector[i] /= count;
            }
        }

        if (!flag) {
            return null;
        }

        return vector;
    }

    /**
     * Load compressed sentences, organized by cluster
     *
     * @param count: Number of sentences selected per cluster
     * @return
     * @throws IOException
     */
    private List<ClustItemPlus> loadSentences(int count) throws IOException {

        List<ClustItemPlus> clustItems = new ArrayList<ClustItemPlus>();

        Pattern pattern = Pattern.compile("(classes_\\d+):");

        try {
            log.info("Loading msc file[" + GlobalParam.workDir + "/" + DIR_SUMMARY_RESULTS + "/" + DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");
            LineIterator lineIterator = FileUtils.lineIterator(FileUtils.getFile(GlobalParam.workDir + "/" + DIR_SUMMARY_RESULTS + '/' + DIR_SENTENCES_COMPRESSION, this.filename), DEFAULT_CHARSET.toString());

            String currentKey = "";
            int sentCount = 0; // Store current selected sentence count
            int totalCount = 0; // Total sentence count
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // Current class: 
                    currentKey = matcher.group(1);
                    ClustItemPlus clustItem = new ClustItemPlus();
                    clustItem.setName(currentKey);
                    clustItems.add(clustItem);
                    totalCount += sentCount;
                    sentCount = 0;
                } else {
                    ClustItemPlus ci = clustItems.get(clustItems.size() - 1);
                    ci.setSize(ci.getSize() + 1);
                    if (sentCount >= count) {
                        continue;
                    }
                    List<Pair<Float, String>> sentences = ci.getSentences();
                    if (null == sentences) {
                        sentences = new ArrayList<Pair<Float, String>>();
                        ci.setSentences(sentences);
                    }
                    // Convert score#sentence to(score, sentence)
                    int flagNum = line.indexOf("#");
                    float pathWeight = Float.parseFloat(line.substring(0, flagNum));
                    sentences.add(new Pair<Float, String>(pathWeight, line.substring(flagNum + 1)));
                    ci.setAvgWeight(ci.getAvgWeight() + pathWeight);
                    ++sentCount;
                }
            }

            // 将所有clust的weight取均值
            for (ClustItemPlus clustItemPlus : clustItems) {
                int size = clustItemPlus.getSize() > count ? count : clustItemPlus.getSize();
                clustItemPlus.setAvgWeight(clustItemPlus.getAvgWeight() / size);
            }

            log.info("Load msc file finished[sentence count:" + totalCount + "]");

        } catch (IOException e) {
            log.error("Load msc file[" + GlobalParam.workDir + "/" + DIR_SUMMARY_RESULTS + "/" + DIR_SENTENCES_COMPRESSION + "/" + this.filename + "] error!", e);
            throw e;
        }

        return clustItems;

    }

    public static void main(String[] args) throws Exception {

        String workDir = "E:/dev_workspace/tmp/workspace/duc2007";
        String idfFilename = "duc2007.idf";
        String vecFilename = "duc2007.vec";

        final double TOTAL_PAGE_COUNT = 30000000000.0D;

        Map<String, Double> idfValues = new HashMap<String, Double>();
        File idfFIle = FileUtils.getFile(workDir + "/" + DIR_IDF_FILE, idfFilename);
        log.info("Loading idf value file[" + idfFIle.getAbsolutePath() + "]");
        LineIterator lineIterator = null;
        try {
            lineIterator = FileUtils.lineIterator(idfFIle, DEFAULT_CHARSET.toString());
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                String[] strs = line.split("###");
                if (strs.length != 2) {
                    log.warn("Line[" + line + "] format is illegal, ignore it!");
                    continue;
                }
                idfValues.put(strs[0].trim(), Long.parseLong(strs[1]) / TOTAL_PAGE_COUNT);
            }
            log.info("Load idf value file[" + idfFIle.getAbsolutePath() + "] finished!");
        } catch (IOException e) {
            log.error("Load idf value file[" + idfFIle.getAbsolutePath() + "] error!", e);
            throw e;
        } finally {
            if (lineIterator != null) {
                lineIterator.close();
            }
        }

        String question = "Linda Tripp, role that Linda Tripp play in the Clinton Lewinsky affair and the Ken Starr investigation?";

        File vecFile = FileUtils.getFile(workDir + "/" + DIR_VEC_FILE, vecFilename);
        log.info("Loading word vec[" + vecFile.getAbsolutePath() + "]");
        Map<String, Vector> wordVecs = new HashMap<String, Vector>();
        try {
            wordVecs = (Map<String, Vector>) SerializeUtils.readObj(vecFile.getAbsolutePath());
        } catch (ClassNotFoundException e) {
            log.error("Load word vec[" + vecFile.getAbsolutePath() + "] error!", e);
            throw e;
        }
        log.info("Load word vec[" + vecFile.getAbsolutePath() + "] success!");

        if (MapUtils.isEmpty(wordVecs)) {
            log.error("Can't load any word vec[" + vecFile.getAbsolutePath() + "]");
            throw new Exception("Can't load any word vec[" + vecFile.getAbsolutePath() + "]");
        }

        SummaryBuilderByPreVectorReRanker summaryBuilder = new SummaryBuilderByPreVectorReRanker("0", "D0731G.txt", 10, idfValues, question, wordVecs, 0.2f, 3.6f, 1.19f);
        ExecutorService es = Executors.newSingleThreadExecutor();
        Future<Boolean> future = es.submit(summaryBuilder);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        es.shutdown();

    }

}
