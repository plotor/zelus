package edu.whu.cs.nlp.mts.summary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.Pair;
import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;

/**
 * 基于子模函数生成多文档摘要
 *
 * @author zhenchao.wang 2016-1-17 17:06:39
 *
 */
public class SummaryBuilder implements Callable<Boolean>, SystemConstant {

    private static Logger       log              = Logger.getLogger(SummaryBuilder.class);

    /** 工作目录 */
    private final String        workDir;

    /** 主题文件名 */
    private final String        filename;

    /** 存放IDF值的文件名 */
    private final String        idfFilename;

    /** topic query */
    private final String        question;

    /** 词向量获取器 */
    private final EhCacheUtil   ehCacheUtil;

    /** alpha 参数 */
    private final float         alpha;

    /** beta 参数 */
    private final float         beta;

    /** 每个主题下面选取的句子的数量 */
    private Integer             sentCountInClust = 10;

    /** Google 总页面数估值 */
    private static final double TOTAL_PAGE_COUNT = 30000000000.0D;

    public SummaryBuilder(String workDir, String filename, int sentCountInClust, String idfFilename, String question, EhCacheUtil ehCacheUtil, float alpha, float beta) {
        super();
        this.workDir = workDir;
        this.filename = filename;
        this.sentCountInClust = Integer.valueOf(sentCountInClust);
        this.idfFilename = idfFilename;
        this.question = question;
        this.ehCacheUtil = ehCacheUtil;
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public Boolean call() throws Exception {

        log.info("[Thread id:" + Thread.currentThread().getId() + "] is building summary for[" + this.workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");

        // 加载当前主题下面的句子，每个类别控制句子数量
        Map<String, List<Pair<Float, String>>> candidateSentences = this.loadSentences(this.sentCountInClust);

        // 加载每个clust的权值
        String clusterWeightFilepath = this.workDir + "/" + SystemConstant.DIR_CLUSTER_WEIGHT + "/" + this.filename.substring(0, this.filename.length() - 4) + "." + SystemConstant.OBJ;
        log.info("Loading serilized file[" + clusterWeightFilepath + "]");
        Map<String, Float> clusterWeights = null;
        try {
            clusterWeights = (Map<String, Float>) SerializeUtil.readObj(clusterWeightFilepath);
        } catch (IOException e) {
            log.error("Load serilized file[" + clusterWeightFilepath + "] error!", e);
            throw e;
        }
        log.info("Load serilized file[" + clusterWeightFilepath + "] successed!");

        // 加载每个词的IDF值
        Map<String, Double> idfValue = new HashMap<String, Double>();
        File idfFIle = FileUtils.getFile(this.workDir + "/" + DIR_IDF_FILE, this.idfFilename);
        log.info("Loading idf value file[" + idfFIle.getAbsolutePath() + "]");
        LineIterator lineIterator = null;
        try {
            lineIterator = FileUtils.lineIterator(idfFIle, DEFAULT_CHARSET.toString());
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                String[] strs = line.split("\\s+");
                if (strs.length != 2) {
                    log.warn("Line[" + line + "] format is illegal, ignore it!");
                    continue;
                }
                idfValue.put(strs[0].trim(), Long.parseLong(strs[1]) / TOTAL_PAGE_COUNT);
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

        /*
         * 在保证摘要总字数不超过规定字数的前提下， 按照句子的综合得分（主题贡献分，查询覆盖度，多样性得分）循环从候选句子中选取句子
         */

        int summaryWordCount = 0;
        // 判断候选集合中是否还有句子
        boolean isNotEmpty = true;

        // 提取当前问题的关键字（考虑停用词）
        String[] questionWords = this.question.trim().split("\\s+");

        /* 存放摘要的中间值，以及最终的摘要，按照clust进行组织 */
        Map<String, List<Pair<Float, String>>> partialSummary = new HashMap<String, List<Pair<Float, String>>>();

        /* 摘要中间值中各词的词频 */
        Map<String, Integer> wordFrequencyInPartialSummary = new HashMap<String, Integer>();

        /* 缓存摘要中每个类的多样性得分 */
        Map<String, Double> clusterDiversies = new HashMap<String, Double>();

        while (isNotEmpty && summaryWordCount < MAX_SUMMARY_WORDS_COUNT) {

            isNotEmpty = false;

            // 记录当前最大的综合得分
            float maxGeneralScore = Float.MIN_VALUE;
            // 计算最大综合得分对应的clust名称
            String selectedClustName = null;
            // 记录最大综合得分对应的句子的序号
            int selectedSentenceNumInClust = -1;
            // 记录最大综合得分对应类别的新的多样性得分
            double selectedClustDiversityScore = -1.0D;

            for (Entry<String, List<Pair<Float, String>>> entry : candidateSentences.entrySet()) {

                String currentClustKey = entry.getKey();
                List<Pair<Float, String>> pairs = entry.getValue();

                // 获取当前cluster的权值
                float currentClusterWeight = clusterWeights.get(currentClustKey);

                /* 历史多样性得分 */
                float historyDiversityScore = 0.0f;
                for (Entry<String, Double> innerEntry : clusterDiversies.entrySet()) {
                    historyDiversityScore += innerEntry.getValue();
                }

                if (CollectionUtils.isNotEmpty(pairs)) {

                    isNotEmpty = true;

                    // 综合得分
                    float generalScore = 0.0f;

                    for (int i = 0; i < pairs.size(); i++) {

                        Pair<Float, String> pair = pairs.get(i);

                        // 1.计算当前句子的主题贡献分
                        float topicScore = currentClusterWeight * pair.getLeft();

                        // 2.计算当前句子的查询覆盖度
                        float queryScore = 0.0f;
                        String sentence = pair.getRight();
                        // 计算当前句子中每个词的频率
                        String[] strs = sentence.trim().split("\\s+");
                        Map<String, Integer> wordFreqInSentence = new HashMap<String, Integer>();
                        for (String word : strs) {
                            Integer freq = wordFreqInSentence.get(word);
                            if (null == freq) {
                                freq = 0;
                            }
                            freq += 1;
                            wordFreqInSentence.put(word, freq);
                        }

                        for (String questionWord : questionWords) {
                            // 问句中的词在当前摘要中的词频
                            Integer frequencyInSummary = wordFrequencyInPartialSummary.get(questionWord);
                            if (null == frequencyInSummary) {
                                frequencyInSummary = 0;
                            }
                            // 问句中的词在当前句子中的词频
                            Integer frequencyInSentence = wordFreqInSentence.get(questionWord);
                            if (null == frequencyInSentence) {
                                frequencyInSentence = 0;
                            }
                            // 计算当前词的tf-idf值
                            double tf = (frequencyInSummary + frequencyInSentence) / (double) (summaryWordCount + strs.length);
                            double idf = idfValue.get(questionWord);
                            queryScore += tf * idf;
                        }

                        // 3.计算当前句子的多样性得分
                        // 计算非当前clust的历史多样性分值之和
                        double diversityScore = historyDiversityScore;

                        // 当前句子的多样性得分
                        double currentSentenceDiversityScore = 0.0D;
                        // 当前句子的向量
                        double[] sentenceVec = this.sentenceToVector(sentence);

                        if (null != sentenceVec) {
                            // 计算当前clust的多样性得分
                            List<Pair<Float, String>> selectedSentencesInClust = partialSummary.get(entry.getKey());
                            for (Pair<Float, String> pairInClust : selectedSentencesInClust) {
                                // 计算当前句子与所属clust里面已经选择的句子的相似度之和
                                // 计算历史句子的向量
                                double[] otherSentVec = this.sentenceToVector(pairInClust.getRight());
                                if (null == otherSentVec) {
                                    continue;
                                }
                                currentSentenceDiversityScore += this.cosineDistence(sentenceVec, otherSentVec);
                            }
                        }

                        diversityScore += currentSentenceDiversityScore;

                        // 计算综合得分
                        generalScore = (float) (topicScore + this.alpha * queryScore + this.beta * diversityScore);

                        if(generalScore > maxGeneralScore) {
                            maxGeneralScore = generalScore;
                            selectedClustName = entry.getKey();
                            selectedSentenceNumInClust = i;
                            selectedClustDiversityScore = diversityScore;
                        }

                    }
                }

            }

            // 更新已经选择的摘要
            if(null == selectedClustName || selectedSentenceNumInClust == -1 || selectedClustDiversityScore == -1) {
                log.warn("Selected clust or sentence is illegal[selectedClustName = " + selectedClustName + ", selectedSentenceNumInClust = " + selectedSentenceNumInClust + "]");
                continue;
            }

            // 从候选集合中选择最佳句子加入摘要集合中，同时将其从候选集合中删除
            List<Pair<Float, String>> sentences = candidateSentences.get(selectedClustName);
            Pair<Float, String> selectedSentence = sentences.remove(selectedSentenceNumInClust);
            List<Pair<Float, String>> clustSentencesInSummary = partialSummary.get(selectedClustName);
            if(null == clustSentencesInSummary) {
                clustSentencesInSummary = new ArrayList<Pair<Float, String>>();
            }
            clustSentencesInSummary.add(selectedSentence);

            // 更新相关数据
            String[] wordsInSentence = selectedSentence.getRight().split("\\s+");
            // 1.更新摘要字数
            summaryWordCount += wordsInSentence.length;
            // 2.更新摘要中词的词频
            for (String word : wordsInSentence) {
                Integer freq = wordFrequencyInPartialSummary.get(word);
                if (null == freq) {
                    freq = 0;
                }
                freq += 1;
                wordFrequencyInPartialSummary.put(word, freq);
            }
            // 3.更新选中句子所属类的多样性得分
            clusterDiversies.put(selectedClustName, selectedClustDiversityScore);

        }

        log.info("[Thread id:" + Thread.currentThread().getId() + "] build summary for[" + this.workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "] finished!");
        return true;
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
     * 计算输入句子的向量
     *
     * @param sentence
     * @return
     */
    private double[] sentenceToVector(String sentence) {
        double[] vector = null;
        if (StringUtils.isBlank(sentence)) {
            return vector;
        }
        vector = new double[DIMENSION];
        Arrays.fill(vector, 0.0D);
        String[] strs = sentence.split("\\s+");
        int count = 0;
        for (String str : strs) {
            if (PUNCT_EN.contains(str)) {
                // 跳过标点
                continue;
            }
            if (STOPWORDS.contains(str)) {
                // 跳过停用词
                continue;
            }

            Word word = new Word();
            word.setName(str);
            word.setLemma(str);
            word.setNer("O");
            try {
                Vector vec = this.ehCacheUtil.getMostSimilarVec(word);
                if (vec == null) {
                    // 如果在词向量中找不到当前的词向量，则跳过
                    continue;
                }
                Float[] floatVec = vec.floatVecs();
                for (int i = 0; i < DIMENSION; i++) {
                    vector[i] += floatVec[i];
                }
                count++;
            } catch (Exception e) {
                log.error("Get word[" + str + "] vector error!", e);
            }
        }

        if (count > 0) {
            for (int i = 0; i < DIMENSION; i++) {
                vector[i] /= count;
            }
        }

        return vector;
    }

    /**
     * 加载压缩后的句子，按类别组织
     *
     * @param count:
     *            每个类别下选取的句子数量
     * @return
     * @throws IOException
     */
    private Map<String, List<Pair<Float, String>>> loadSentences(int count) throws IOException {

        Map<String, List<Pair<Float, String>>> clustedSentences = new HashMap<String, List<Pair<Float, String>>>();

        Pattern pattern = Pattern.compile("(classes_\\d+):");

        try {
            SummaryBuilder.log.info("Loading msc file[" + this.workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");
            LineIterator lineIterator = FileUtils.lineIterator(FileUtils.getFile(this.workDir + '/' + SystemConstant.DIR_SENTENCES_COMPRESSION, this.filename), SystemConstant.DEFAULT_CHARSET.toString());

            String currentKey = "";
            int sentCount = 0; // 存储当前选择的句子数
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // 当前为classes_
                    currentKey = matcher.group(1);
                    sentCount = 0;
                } else {
                    if (sentCount <= count) {
                        List<Pair<Float, String>> sentences = clustedSentences.get(currentKey);
                        if (null == sentences) {
                            sentences = new ArrayList<Pair<Float, String>>();
                        }
                        // 将score#sentence转换成(score, sentence)
                        int flagNum = line.indexOf("#");
                        sentences.add(new Pair<Float, String>(Float.parseFloat(line.substring(0, flagNum)), line.substring(flagNum + 1)));
                    }
                }
            }
        } catch (IOException e) {
            SummaryBuilder.log.error("Load msc file[" + this.workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "] error!", e);
            throw e;
        }

        return clustedSentences;

    }

    /**
     * 计算当前候选句子的主题贡献分
     *
     * @param selectedSentences
     *            已经选取的句子集合
     * @param candidateSentence
     *            候选句子
     * @param clusterKey
     *            当前cluster的key
     * @param clusterWeights
     *            所有clust的权值
     * @return
     */
    private float topicScore(Map<String, List<Pair<Float, String>>> selectedSentences, Pair<Float, String> candidateSentence, Float currentClusterWeights) {

        float score = 0.0f;

        return score;

    }

}
