package org.zhenchao.zelus.summary;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.global.Constants;
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
 * 基于子模函数生成多文档摘要（利用TF-IDF度量句子之间的相似度）
 *
 * @author zhenchao.wang 2016-1-17 17:06:39
 */
public class SummaryBuilder implements Callable<Boolean>, Constants {

    private static Logger log = Logger.getLogger(SummaryBuilder.class);

    /** 工作目录 */
    private final String workDir;

    /** 主题文件名 */
    private final String filename;

    /** 主题名称 */
    private final String topicname;

    /** IDF值 */
    Map<String, Double> idfValues;

    /** topic query */
    private final String question;

    /** 词向量获取器 */
    // private final EhCacheUtil ehCacheUtil;

    /** alpha 参数 */
    private final float alpha;

    /** beta 参数 */
    private final float beta;

    /** 每个主题下面选取的句子的数量 */
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

        // 加载当前主题下面的句子，每个类别控制句子数量
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
         * 在保证摘要总字数不超过规定字数的前提下， 按照句子的综合得分（主题贡献分，查询覆盖度，多样性得分）循环从候选句子中选取句子
         */

        // 当前摘要字数
        int summaryWordCount = 0;

        // 当前摘要包含的句子数
        int summarySentenceCount = 0;

        // 判断候选集合中是否还有句子
        boolean isNotEmpty = true;

        // 提取当前问题的关键字（考虑停用词）
        // String[] questionWords = this.question.trim().split("\\s+");
        List<Word> questionWords = StanfordNLPTools.segmentWord(this.question.trim());

        /* 存放摘要的中间值，以及最终的摘要，按照clust进行组织 */
        Map<String, List<Pair<Float, String>>> partialSummary = new HashMap<String, List<Pair<Float, String>>>();

        /* 摘要中间值中各词的词频 */
        Map<String, Integer> wordFrequencyInPartialSummary = new HashMap<String, Integer>();

        /* 缓存摘要中每个类的多样性得分 */
        Map<String, Double> clusterDiversies = new HashMap<String, Double>();

        while (isNotEmpty && summaryWordCount < MAX_SUMMARY_WORDS_COUNT) {

            isNotEmpty = false;

            // 记录当前最大的综合得分
            float maxGeneralScore = Float.NEGATIVE_INFINITY;
            // 计算最大综合得分对应的clust名称
            String selectedClustName = null;
            // 记录最大综合得分对应的句子的序号
            Pair<Float, String> selectedSentence = null;
            // 记录最大综合得分对应类别的新的多样性得分
            double selectedClustDiversityScore = -1.0D;

            for (Entry<String, ClustItem> entry : candidateSentences.entrySet()) {

                ClustItem clust = entry.getValue();

                // 当前类别名称
                String currentClustKey = clust.getName();

                // 当前类别下剩余的候选句子集合
                List<Pair<Float, String>> pairs = clust.getSentences();

                if (CollectionUtils.isEmpty(pairs)) {
                    // 当前类别下已没有候选句子
                    continue;
                }

                // 说明还有候选句子
                isNotEmpty = true;

                // 获取当前cluster的权值
                float currentClusterWeight = clusterWeights.get(currentClustKey);

                /* 历史多样性得分 */
                float historyDiversityScore = 0.0f;
                /*
                 * for (Entry<String, Double> innerEntry :
                 * clusterDiversies.entrySet()) { historyDiversityScore +=
                 * innerEntry.getValue(); }
                 */

                // 综合得分
                float generalScore = 0.0f;

                // 遍历处理当前类别下的句子
                Iterator<Pair<Float, String>> pairItr = pairs.iterator();
                while (pairItr.hasNext()) {
                    Pair<Float, String> pair = pairItr.next();
                    // 1.计算当前句子的主题贡献分
                    float topicScore = currentClusterWeight / (pair.getLeft() * clust.getSize());
                    //float topicScore = 0.001f / pair.getLeft();

                    // 2.计算当前句子的查询覆盖度
                    float queryScore = 0.0f;

                    String sentence = pair.getRight();

                    // 计算当前句子中每个词的频率
                    // String[] strs = sentence.trim().split("\\s+");
                    List<Word> words = StanfordNLPTools.segmentWord(sentence.trim());

                    // 计算纯句子的长度，不考虑标点
                    int pureSentLen = 0;
                    for (Word word : words) {
                        if (word.getName().equals(word.getPos()) || "-lrb-".equals(word.getName()) || "-rrb-".equals(word.getName())) {
                            continue;
                        }
                        ++pureSentLen;
                    }
                    if (pureSentLen < 8) {
                        // 忽略长度小于8的句子
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
                        // 问句中的词在当前摘要中的词频
                        String key = questionWord.getName().trim().toLowerCase();
                        Integer frequencyInSummary = wordFrequencyInPartialSummary.get(key);
                        if (null == frequencyInSummary) {
                            frequencyInSummary = 0;
                        }
                        // 问句中的词在当前句子中的词频
                        Integer frequencyInSentence = wordFreqInSentence.get(key);
                        if (null == frequencyInSentence) {
                            frequencyInSentence = 0;
                        }
                        // 计算当前词的tf-idf值
                        double tf = (frequencyInSummary + frequencyInSentence) / (double) (summaryWordCount + words.size() + questionWords.size());
                        double idf = this.idfValues.containsKey(key) ? this.idfValues.get(key) : 0.0;
                        queryScore += tf * idf;
                    }

                    // 3.计算当前句子的多样性得分
                    // 计算非当前clust的历史多样性分值之和
                    // double diversityScore = historyDiversityScore;

                    double diversityScore = 0.0;

                    // 当前句子与已有摘要的相似度得分
                    double similarityScore = 0.0;

                    // 利用TF-IDF值度量当前句子与摘要的相似度
                    for (Word word : words) {
                        // 句中词在当前摘要中的词频
                        String key = word.getName().trim().toLowerCase();
                        Integer frequencyInSummary = wordFrequencyInPartialSummary.get(key);
                        if (null == frequencyInSummary) {
                            continue;
                        }

                        // 计算当前词的tf-idf值
                        double tf = frequencyInSummary / (double) (summaryWordCount + words.size());
                        double idf = this.idfValues.containsKey(key) ? this.idfValues.get(key) : 0.0;
                        similarityScore += tf * idf;
                    }

                    // 计算综合得分
                    // log.info("topic score:" + topicScore + ",\tquery score:" + queryScore + ",\tsimilarity score:" + similarityScore);
                    topicScore = (float) this.sigmoid(topicScore);
                    queryScore = (float) this.sigmoid(queryScore);
                    similarityScore = this.sigmoid(similarityScore);
                    generalScore = (float) (topicScore + this.alpha * queryScore - this.beta * similarityScore);

                    if (generalScore > maxGeneralScore) {
                        maxGeneralScore = generalScore;
                        selectedClustName = entry.getKey();
                        selectedSentence = pair;
                        System.out.println(">>\t" + generalScore + "\t" + "topic score:" + topicScore + ",\tquery score:" + queryScore + ",\tsimilarity score:" + similarityScore + "\t" + pair.getRight());
                        selectedClustDiversityScore = diversityScore;
                    }

                }

            }

            // 更新已经选择的摘要
            if (null == selectedClustName || null == selectedSentence || selectedClustDiversityScore == -1) {
                log.warn("Selected clust or sentence is illegal[selectedClustName = " + selectedClustName + ", selectedSentence = " + selectedSentence + "]");
                continue;
            }

            // 从候选集合中选择最佳句子加入摘要集合中，同时将其从候选集合中删除
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
            System.out.println("!!!" + ss.getRight());
            List<Pair<Float, String>> clustSentencesInSummary = partialSummary.get(selectedClustName);
            if (null == clustSentencesInSummary) {
                clustSentencesInSummary = new ArrayList<Pair<Float, String>>();
                partialSummary.put(selectedClustName, clustSentencesInSummary);
            }
            clustSentencesInSummary.add(ss);

            // 更新相关数据
            List<Word> words = StanfordNLPTools.segmentWord(ss.getRight());
            // 1.更新摘要字数
            for (Word word : words) {
                if (word.getName().equals(word.getPos())) {
                    continue;
                }
                ++summaryWordCount;
            }

            // 2.更新摘要包含的句子数
            ++summarySentenceCount;
            // 3.更新摘要中词的词频
            for (Word word : words) {
                Integer freq = wordFrequencyInPartialSummary.get(word.getName().toLowerCase());
                if (null == freq) {
                    freq = 0;
                }
                freq += 1;
                wordFrequencyInPartialSummary.put(word.getName(), freq);
            }
            // 4.更新选中句子所属类的多样性得分
            clusterDiversies.put(selectedClustName, selectedClustDiversityScore);

            // log.info("topic name:" + this.topicname + ",\t summary words:" +
            // summaryWordCount);

        }

        // 保存摘要
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
     * sigmoid函数
     *
     * @param x
     * @return
     */
    private double sigmoid(double x) {
        return 1 / (1 + Math.pow(Math.E, -x));
    }

    /**
     * 计算两个向量之间的余弦值<br>
     * 如果小于0，则说明计算出错
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

        // 利用向量余弦值来计算事件之间的相似度
        double scalar = 0; // 两个向量的内积
        double module_1 = 0, module_2 = 0; // 向量vec_1和vec_2的模
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
     * 加载压缩后的句子，按类别组织
     *
     * @param count: 每个类别下选取的句子数量
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
            int sentCount = 0; // 存储当前选择的句子数
            int totalCount = 0; // 总句子数
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // 当前为classes_
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
                    // 将score#sentence转换成(score, sentence)
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
