package org.zhenchao.zelus.summary;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.nlp.StanfordNLPTools;
import org.zhenchao.zelus.common.pojo.Pair;
import org.zhenchao.zelus.common.pojo.Word;
import org.zhenchao.zelus.common.util.SerializeUtils;
import org.zhenchao.zelus.domain.ClustItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 * Multi-document summarization based on submodular function (using TF-IDF to measure sentence similarity)
 *
 * @author zhenchao 2016-1-17 17:06:39
 */
public class SummaryBuilder implements Callable<Boolean>, Constants {

    private static Logger log = Logger.getLogger(SummaryBuilder.class);

    /** Working directory */
    private final String workDir;

    /** Topic filename */
    private final String filename;

    /** Topic name */
    private final String topicname;

    /** IDF values */
    Map<String, Double> idfValues;

    /** topic query */
    private final String question;

    /** Word vector retriever */
    // private final EhCacheUtil ehCacheUtil;

    /** Alpha parameter */
    private final float alpha;

    /** Beta parameter */
    private final float beta;

    /** Number of sentences selected per topic */
    private Integer sentCountInClust = 10;

    public SummaryBuilder(String workDir, String filename, int sentCountInClust, Map<String, Double> idfValues, String question, float alpha, float beta) {
        super();
        this.workDir = workDir;
        this.filename = filename;
        this.topicname = this.filename.substring(0, this.filename.length() - 4);
        this.sentCountInClust = Integer.valueOf(sentCountInClust);
        this.idfValues = idfValues;
        this.question = question;
        // this.ehCacheUtil = ehCacheUtil;
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public Boolean call() throws Exception {

        log.info("[Thread id:" + Thread.currentThread().getId() + "] is building summary for[" + this.workDir + "/" + Constants.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");

        // 加载Current topic下面的句子，每个Class别控制句子数量
        Map<String, ClustItem> candidateSentences = this.loadSentences(this.sentCountInClust);

        // 加载每个clust的权值
        String clusterWeightFilepath = this.workDir + "/" + Constants.DIR_CLUSTER_WEIGHT + "/" + this.filename.substring(0, this.filename.length() - 4) + "." + Constants.OBJ;
        log.info("Loading serilized file[" + clusterWeightFilepath + "]");
        Map<String, Float> clusterWeights = null;
        try {
            clusterWeights = (Map<String, Float>) SerializeUtils.readObj(clusterWeightFilepath);
        } catch (IOException e) {
            log.error("Load serilized file[" + clusterWeightFilepath + "] error!", e);
            throw e;
        }
        log.info("Load serilized file[" + clusterWeightFilepath + "] successed!");

        /*
         * 在保证摘要总字数不超过规定字数的前提下， 按照句子的Overall score（主题贡献分，Query覆盖度，多样性得分）循环从候选句子中选取句子
         */

        // Current summary word count
        int summaryWordCount = 0;

        // Current summary sentence count
        int summarySentenceCount = 0;

        // Check if there are still candidate sentences
        boolean isNotEmpty = true;

        // Extract keywords from the current question (considering stopwords)
        // String[] questionWords = this.question.trim().split("\\s+");
        List<Word> questionWords = StanfordNLPTools.segmentWord(this.question.trim());

        /* Intermediate and final summary values, organized by cluster */
        Map<String, List<Pair<Float, String>>> partialSummary = new HashMap<String, List<Pair<Float, String>>>();

        /* Word frequency in intermediate summary */
        Map<String, Integer> wordFrequencyInPartialSummary = new HashMap<String, Integer>();

        /* Cache diversity score for each cluster in the summary */
        Map<String, Double> clusterDiversies = new HashMap<String, Double>();

        while (isNotEmpty && summaryWordCount < MAX_SUMMARY_WORDS_COUNT) {

            isNotEmpty = false;

            // Record current maximum overall score
            float maxGeneralScore = Float.NEGATIVE_INFINITY;
            // Calculate cluster name with maximum overall score
            String selectedClustName = null;
            // Record sentence index with maximum overall score
            Pair<Float, String> selectedSentence = null;
            // Record new diversity score for the cluster with maximum overall score
            double selectedClustDiversityScore = -1.0D;

            for (Entry<String, ClustItem> entry : candidateSentences.entrySet()) {

                ClustItem clust = entry.getValue();

                // Current cluster name
                String currentClustKey = clust.getName();

                // Remaining candidate sentences in current cluster
                List<Pair<Float, String>> pairs = clust.getSentences();

                if (CollectionUtils.isEmpty(pairs)) {
                    // No more candidate sentences in current cluster
                    continue;
                }

                // There are still candidate sentences
                isNotEmpty = true;

                // Get current cluster weight
                float currentClusterWeight = clusterWeights.get(currentClustKey);

                /* Historical diversity score */
                float historyDiversityScore = 0.0f;
                /*
                 * for (Entry<String, Double> innerEntry :
                 * clusterDiversies.entrySet()) { historyDiversityScore +=
                 * innerEntry.getValue(); }
                 */

                // Overall score
                float generalScore = 0.0f;

                // Iterate over sentences in current cluster
                Iterator<Pair<Float, String>> pairItr = pairs.iterator();
                while (pairItr.hasNext()) {
                    Pair<Float, String> pair = pairItr.next();
                    // 1.Calculate topic contribution score for current sentence
                    float topicScore = currentClusterWeight / (pair.getLeft() * clust.getSize());
                    //float topicScore = 0.001f / pair.getLeft();

                    // 2.Calculate query coverage for current sentence
                    float queryScore = 0.0f;

                    String sentence = pair.getRight();

                    // Calculate frequency of each word in current sentence
                    // String[] strs = sentence.trim().split("\\s+");
                    List<Word> words = StanfordNLPTools.segmentWord(sentence.trim());

                    // Calculate pure sentence length excluding punctuation
                    int pureSentLen = 0;
                    for (Word word : words) {
                        if (word.getName().equals(word.getPos()) || "-lrb-".equals(word.getName()) || "-rrb-".equals(word.getName())) {
                            continue;
                        }
                        ++pureSentLen;
                    }
                    if (pureSentLen < 8) {
                        // Ignore sentences shorter than 8 words
                        pairItr.remove();
                        continue;
                    }

                    Map<String, Integer> wordFreqInSentence = new HashMap<String, Integer>();
                    for (Word word : words) {
                        String key = word.getName().toLowerCase();
                        Integer freq = wordFreqInSentence.get(key);
                        if (null == freq) {
                            freq = 0;
                        }
                        freq += 1;
                        wordFreqInSentence.put(key, freq);
                    }

                    for (Word questionWord : questionWords) {
                        // Word frequency of question word in current summary
                        String key = questionWord.getName().trim().toLowerCase();
                        Integer frequencyInSummary = wordFrequencyInPartialSummary.get(key);
                        if (null == frequencyInSummary) {
                            frequencyInSummary = 0;
                        }
                        // Word frequency of question word in current sentence
                        Integer frequencyInSentence = wordFreqInSentence.get(key);
                        if (null == frequencyInSentence) {
                            frequencyInSentence = 0;
                        }
                        // Calculate TF-IDF value for current word
                        double tf = (frequencyInSummary + frequencyInSentence) / (double) (summaryWordCount + words.size() + questionWords.size());
                        double idf = this.idfValues.containsKey(key) ? this.idfValues.get(key) : 0.0;
                        queryScore += tf * idf;
                    }

                    // 3.Calculate diversity score for current sentence
                    // Calculate sum of historical diversity scores for non-current clusters
                    // double diversityScore = historyDiversityScore;

                    double diversityScore = 0.0;

                    // Similarity score between current sentence and existing summary
                    double similarityScore = 0.0;

                    // 利用TF-IDF values度量Current sentence子与摘要的相似度
                    for (Word word : words) {
                        // Word frequency in current summary
                        String key = word.getName().trim().toLowerCase();
                        Integer frequencyInSummary = wordFrequencyInPartialSummary.get(key);
                        if (null == frequencyInSummary) {
                            continue;
                        }

                        // Calculate TF-IDF value for current word
                        double tf = frequencyInSummary / (double) (summaryWordCount + words.size());
                        double idf = this.idfValues.containsKey(key) ? this.idfValues.get(key) : 0.0;
                        similarityScore += tf * idf;
                    }

                    // CalculateOverall score
                    // log.info("topic score:" + topicScore + ",\tquery score:" + queryScore + ",\tsimilarity score:" + similarityScore);
                    topicScore = (float) this.sigmoid(topicScore);
                    queryScore = (float) this.sigmoid(queryScore);
                    similarityScore = this.sigmoid(similarityScore);
                    generalScore = (float) (topicScore + this.alpha * queryScore - this.beta * similarityScore);

                    if (generalScore > maxGeneralScore) {
                        maxGeneralScore = generalScore;
                        selectedClustName = entry.getKey();
                        selectedSentence = pair;
                        log.debug(">>\t" + generalScore + "\t" + "topic score:" + topicScore + ",\tquery score:" + queryScore + ",\tsimilarity score:" + similarityScore + "\t" + pair.getRight());
                        selectedClustDiversityScore = diversityScore;
                    }

                }

            }

            // Update selected summary
            if (null == selectedClustName || null == selectedSentence || selectedClustDiversityScore == -1) {
                log.warn("Selected clust or sentence is illegal[selectedClustName = " + selectedClustName + ", selectedSentence = " + selectedSentence + "]");
                continue;
            }

            // Select the best sentence from candidates and add to summary, then remove from candidates
            List<Pair<Float, String>> sentences = candidateSentences.get(selectedClustName).getSentences();
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
            log.debug("!!!" + ss.getRight());
            List<Pair<Float, String>> clustSentencesInSummary = partialSummary.get(selectedClustName);
            if (null == clustSentencesInSummary) {
                clustSentencesInSummary = new ArrayList<Pair<Float, String>>();
                partialSummary.put(selectedClustName, clustSentencesInSummary);
            }
            clustSentencesInSummary.add(ss);

            // Update相关Data
            List<Word> words = StanfordNLPTools.segmentWord(ss.getRight());
            // 1.Update summary word count
            for (Word word : words) {
                if (word.getName().equals(word.getPos())) {
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
            // 4.Update diversity score for the cluster of the selected sentence
            clusterDiversies.put(selectedClustName, selectedClustDiversityScore);

            // log.info("topic name:" + this.topicname + ",\t summary words:" +
            // summaryWordCount);

        }

        // Save summary
        StringBuilder summary = new StringBuilder();
        for (Entry<String, List<Pair<Float, String>>> entry : partialSummary.entrySet()) {
            for (Pair<Float, String> pair : entry.getValue()) {
                String sentence = pair.getRight().trim();
                sentence = sentence.replaceAll("''", "").replaceAll("``", "");
                sentence = sentence.replaceAll("\\s+", " ");
                sentence = sentence.replaceAll("\\s+'s", "'s");
                sentence = sentence.replaceAll("-lrb-[\\s\\S]*?-rrb-\\s+", "");
                sentence = sentence.replaceAll("-lrb-", "");
                sentence = sentence.replaceAll("-rrb-", "");
                sentence = sentence.endsWith(".") ? (sentence + "\n") : (sentence + ".\n");
                summary.append(sentence);
            }
        }

        int indexOfPoint = this.filename.lastIndexOf(".");
        String summaryFilename = this.filename.substring(0, indexOfPoint - 1).toUpperCase() + ".M.250." + this.filename.substring(indexOfPoint - 1, indexOfPoint).toUpperCase() + ".3";
        try {
            File file = FileUtils.getFile(this.workDir + "/" + DIR_SUMMARIES_V2, summaryFilename);
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
     * Calculate cosine similarity between two vectors<br>
     * If less than 0, calculation error occurred
     *
     * @param vec1
     * @param vec2
     * @return
     */
    private double cosineDistence(double[] vec1, double[] vec2) {

        double value = -1;

        if (vec1 == null || vec2 == null) {
            return value;
        }

        // Calculate event similarity using vector cosine
        double scalar = 0; // Inner product of two vectors
        double module_1 = 0, module_2 = 0; // Magnitudes of vec1 and vec2
        for (int i = 0; i < DIMENSION; ++i) {
            scalar += vec1[i] * vec2[i];
            module_1 += vec1[i] * vec1[i];
            module_2 += vec2[i] * vec2[i];
        }

        if (module_1 > 0 && module_2 > 0) {
            value = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)) + 1;
        }

        return value;

    }

    /**
     * Load compressed sentences, organized by cluster
     *
     * @param count: Number of sentences selected per cluster
     * @return
     * @throws IOException
     */
    private Map<String, ClustItem> loadSentences(int count) throws IOException {

        Map<String, ClustItem> clustedSentences = new HashMap<String, ClustItem>();

        Pattern pattern = Pattern.compile("(classes_\\d+):");

        try {
            log.info("Loading msc file[" + this.workDir + "/" + Constants.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");
            LineIterator lineIterator = FileUtils.lineIterator(FileUtils.getFile(this.workDir + '/' + Constants.DIR_SENTENCES_COMPRESSION, this.filename), Constants.DEFAULT_CHARSET.toString());

            String currentKey = "";
            int sentCount = 0; // Store current selected sentence count
            int totalCount = 0; // Total sentence count
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // Current class: 
                    currentKey = matcher.group(1);
                    ClustItem clustItem = new ClustItem();
                    clustItem.setName(currentKey);
                    clustedSentences.put(currentKey, clustItem);
                    totalCount += sentCount;
                    sentCount = 0;
                } else {
                    ClustItem ci = clustedSentences.get(currentKey);
                    ci.setSize(ci.getSize() + 1);
                    if (sentCount > count) {
                        continue;
                    }
                    List<Pair<Float, String>> sentences = ci.getSentences();
                    if (null == sentences) {
                        sentences = new ArrayList<Pair<Float, String>>();
                        ci.setSentences(sentences);
                    }
                    // Convert score#sentence to(score, sentence)
                    int flagNum = line.indexOf("#");
                    sentences.add(new Pair<Float, String>(Float.parseFloat(line.substring(0, flagNum)), line.substring(flagNum + 1)));
                    ++sentCount;
                }
            }

            log.info("Load msc file finished[sentence count:" + totalCount + "]");

        } catch (IOException e) {
            log.error("Load msc file[" + this.workDir + "/" + Constants.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "] error!", e);
            throw e;
        }

        return clustedSentences;

    }

    public static void main(String[] args) throws IOException {

        String workDir = "E:/dev_workspace/tmp/workspace/duc2007";
        String idfFilename = "duc2007.idf";

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

        String question = "Describe the legal battle between various recording artists and members of the record industry and the Internet music site Napster. What support, or lack thereof, have the litigants received?";

        SummaryBuilder summaryBuilder = new SummaryBuilder(workDir, "D0714D.txt", 10, idfValues, question, 1.0f, 1.6f);
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
