package org.zhenchao.zelus.takahe;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.pojo.SentNumSimiPair;
import org.zhenchao.zelus.common.pojo.SentenceSilimarityAttribute;
import org.zhenchao.zelus.common.util.ZelusUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Pairs压缩Process后的句子Collection按照TF-IDF values来选取
 *
 * @author zhenchao
 */
public class KeySentenceSelector2 implements Callable<Boolean>, Constants {

    private final Logger log = Logger.getLogger(this.getClass());

    public static final int MAX_SENTENCE_COUNT = 51;                               // 每个Class别的最大句子数

    private final String compressedFilePath;                                    // 压缩语句文件名
    private final String summaryFilePath;                                       // 摘要文件名
    private final String question;                                              // 问题

    public KeySentenceSelector2(String compressedFilePath, String summaryFilePath, String question) {
        super();
        this.compressedFilePath = compressedFilePath;
        this.summaryFilePath = summaryFilePath;
        this.question = question;
    }

    @Override
    public Boolean call() {

        this.log.info(Thread.currentThread() + " is processing [" + this.compressedFilePath + "]");

        LineIterator iterator = null;
        try {

            iterator = FileUtils.lineIterator(FileUtils.getFile(this.compressedFilePath), DEFAULT_CHARSET.toString());

            SentenceSilimarityAttribute aicfss = null;
            List<SentenceSilimarityAttribute> aicfssList = new ArrayList<SentenceSilimarityAttribute>();
            int[][] wordsCountInSentence; // 记录每个Word在每个句子中出现的次数，行表示Word，List示句子
            int wordNum = 0; // Class别中的Word序号
            //StringBuilder sb_summary = new StringBuilder();
            List<List<SentNumSimiPair>> sentNumSimiPairList = new ArrayList<List<SentNumSimiPair>>();

            while (iterator.hasNext()) {

                String line = iterator.nextLine();

                if (line.startsWith("classes_")) {

                    if (aicfss != null) {

                        aicfssList.add(aicfss);

                        // 表示有Data未Process，进行Process
                        // 统计每个Word在每个句子中的数量，Multi-sentence compression默认最多Output50句，再加上一个问句
                        wordsCountInSentence = new int[aicfss.getWords().size()][MAX_SENTENCE_COUNT];
                        for (int i = 0; i < MAX_SENTENCE_COUNT; ++i) {
                            if (i == aicfss.getSentences().size()) {
                                break;
                            }
                            List<String> wordsInSentence = aicfss.getSentences().get(i).words;

                            // Create word set collection for current sentence to improve speed
                            Set<String> tmpWordSet = new HashSet<String>();
                            for (String word : wordsInSentence) {
                                tmpWordSet.add(word.toLowerCase());
                            }
                            for (Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                                if (tmpWordSet.contains(entry.getKey().toLowerCase())) {
                                    // Current sentence contains this word, increment corresponding word count
                                    wordsCountInSentence[entry.getValue()][i] += 1;
                                }
                            }
                        }
                        // Calculate各语句的Vector，利用TF-IDF
                        Map<String, Integer> sentCount4words = new HashMap<String, Integer>(); // 用来记录包含指定Word的句子数
                        for (final Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                            int count = 0;
                            for (int j = 0; j < aicfss.getSentences().size(); ++j) {
                                if (wordsCountInSentence[entry.getValue()][j] > 0) {
                                    ++count;
                                }
                            }
                            sentCount4words.put(entry.getKey(), count);
                        }
                        float[][] sentenceVector = new float[aicfss.getWords().size()][MAX_SENTENCE_COUNT];
                        for (int i = 0; i < aicfss.getSentences().size(); ++i) {
                            for (final Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                                sentenceVector[entry
                                        .getValue()][i] = (float) ((wordsCountInSentence[entry.getValue()][i]
                                        / (float) aicfss.getSentences().get(i).words.size())
                                        * Math.log(aicfss.getSentences().size()
                                        / (double) sentCount4words.get(entry.getKey())));
                            }
                        }

                        // Calculate压缩得到的句子与问句之间的相似度，余弦定理
                        float[] questionVec = new float[aicfss.getWords().size()];
                        for (int j = 0; j < aicfss.getWords().size(); ++j) {
                            questionVec[j] = sentenceVector[j][0];
                        }
                        /*
                         * double approx_min = Double.MAX_VALUE; int sentenceNum
                         * = 0; float currCompressQuality = 0;
                         */
                        final List<SentNumSimiPair> sentNumSimiPairs = new ArrayList<SentNumSimiPair>();
                        for (int i = 1; i < aicfss.getSentences().size(); ++i) {
                            final float[] sentenceVec = new float[aicfss.getWords().size()];
                            for (int j = 0; j < aicfss.getWords().size(); ++j) {
                                sentenceVec[j] = sentenceVector[j][i];
                            }
                            // Calculate cosine similarity between two vectors
                            double approx = this.cosineDistence(questionVec, sentenceVec);
                            sentNumSimiPairs.add(new SentNumSimiPair(i, approx));
                            /*
                             * if(approx >= 0 && approx <= approx_min){
                             * if(approx == approx_min && currCompressQuality >
                             * aicfss.getSentences().get(i).compressedQuality){
                             * approx_min = approx; currCompressQuality =
                             * aicfss.getSentences().get(i).compressedQuality;
                             * sentenceNum = i; }else if(approx < approx_min){
                             * approx_min = approx; currCompressQuality =
                             * aicfss.getSentences().get(i).compressedQuality;
                             * sentenceNum = i; } }
                             */
                        }
                        Collections.sort(sentNumSimiPairs);
                        sentNumSimiPairList.add(sentNumSimiPairs);
                        // sb_summary.append(ZelusUtils.list2String(aicfss.getSentences().get(sentenceNum).words)
                        // + LINE_SPLITER);
                        // 打印测试
                        /*
                         * log.info(line + ",Word count：" +
                         * aicfss.getWords().size()); for (Entry<String,
                         * Integer> entry : aicfss.getWords().entrySet()) {
                         * log.info(entry.getKey() + "\t" +
                         * Arrays.toString(wordsCountInSentence[entry.getValue()
                         * ])); }
                         */
                        // return;
                    }

                    // 清空之前的ProcessResult
                    aicfss = new SentenceSilimarityAttribute(
                            new ArrayList<SentenceSilimarityAttribute.Sentence>(),
                            new HashMap<String, Integer>());
                    wordNum = 0;
                    // 每个Class别的第一句存放问句
                    final List<String> wordsInQuestions = Arrays.asList(this.question.split("\\s+"));
                    aicfss.getSentences().add(new SentenceSilimarityAttribute.Sentence(-1, wordsInQuestions));
                    for (final String word : wordsInQuestions) {
                        if (!aicfss.getWords().containsKey(word.toLowerCase())) {
                            // Add word to word map with incrementing index
                            aicfss.getWords().put(word.toLowerCase(), wordNum++);
                        }
                    }
                } else {
                    // 将文件中的句子表示成内存中的Data结构
                    final int firstSpliterIndex = line.indexOf("#");
                    final float compressedQuality = Float.parseFloat(line.substring(0, firstSpliterIndex));
                    final List<String> wordsInSentence = Arrays
                            .asList(line.substring(firstSpliterIndex + 1).split("\\s+"));
                    aicfss.getSentences().add(new SentenceSilimarityAttribute.Sentence(compressedQuality, wordsInSentence));
                    for (final String word : wordsInSentence) {
                        if (!aicfss.getWords().containsKey(word.toLowerCase())) {
                            // Add word to word map with incrementing index
                            aicfss.getWords().put(word.toLowerCase(), wordNum++);
                        }
                    }
                }
            }
            // Process最后一个Class别的Data
            if (aicfss != null) {
                aicfssList.add(aicfss);
                // 表示有Data未Process，进行Process
                // 统计每个Word在每个句子中的数量，Multi-sentence compression默认最多Output50句，再加上一个问句
                wordsCountInSentence = new int[aicfss.getWords().size()][MAX_SENTENCE_COUNT];
                for (int i = 0; i < MAX_SENTENCE_COUNT; ++i) {
                    if (i == aicfss.getSentences().size()) {
                        break;
                    }
                    final List<String> wordsInSentence = aicfss.getSentences().get(i).words;
                    // log.info(">>" + i + "\t" +
                    // wordsInSentence.toString());
                    // Create word set collection for current sentence to improve speed
                    final Set<String> tmpWordSet = new HashSet<String>();
                    for (final String word : wordsInSentence) {
                        tmpWordSet.add(word.toLowerCase());
                    }
                    for (final Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                        if (tmpWordSet.contains(entry.getKey().toLowerCase())) {
                            // Current sentence contains this word, increment corresponding word count
                            wordsCountInSentence[entry.getValue()][i] += 1;
                        }
                    }
                }

                // Calculate各语句的Vector，利用TF-IDF
                final Map<String, Integer> sentCount4words = new HashMap<String, Integer>(); // 用来记录包含指定Word的句子数
                for (final Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                    int count = 0;
                    for (int j = 0; j < aicfss.getSentences().size(); ++j) {
                        if (wordsCountInSentence[entry.getValue()][j] > 0) {
                            ++count;
                        }
                    }
                    sentCount4words.put(entry.getKey(), count);
                }
                final float[][] sentenceVector = new float[aicfss.getWords().size()][MAX_SENTENCE_COUNT];
                for (int i = 0; i < aicfss.getSentences().size(); ++i) {
                    for (final Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                        sentenceVector[entry.getValue()][i] = (float) ((wordsCountInSentence[entry.getValue()][i]
                                / (float) aicfss.getSentences().get(i).words.size())
                                * Math.log(
                                aicfss.getSentences().size() / (double) sentCount4words.get(entry.getKey())));
                    }
                }

                // Calculate压缩得到的句子与问句之间的相似度，余弦定理
                final float[] questionVec = new float[aicfss.getWords().size()];
                for (int j = 0; j < aicfss.getWords().size(); ++j) {
                    questionVec[j] = sentenceVector[j][0];
                }
                /*
                 * double approx_min = Double.MAX_VALUE; int sentenceNum = 0;
                 * float currCompressQuality = 0;
                 */
                final List<SentNumSimiPair> sentNumSimiPairs = new ArrayList<SentNumSimiPair>();
                for (int i = 1; i < aicfss.getSentences().size(); ++i) {
                    final float[] sentenceVec = new float[aicfss.getWords().size()];
                    for (int j = 0; j < aicfss.getWords().size(); ++j) {
                        sentenceVec[j] = sentenceVector[j][i];
                    }
                    // Calculate cosine similarity between two vectors
                    final double approx = this.cosineDistence(questionVec, sentenceVec);
                    sentNumSimiPairs.add(new SentNumSimiPair(i, approx));
                    /*
                     * if(approx >= 0 && approx <= approx_min){ if(approx ==
                     * approx_min && currCompressQuality >
                     * aicfss.getSentences().get(i).compressedQuality){
                     * approx_min = approx; currCompressQuality =
                     * aicfss.getSentences().get(i).compressedQuality;
                     * sentenceNum = i; }else if(approx < approx_min){
                     * approx_min = approx; currCompressQuality =
                     * aicfss.getSentences().get(i).compressedQuality;
                     * sentenceNum = i; } }
                     */
                }
                Collections.sort(sentNumSimiPairs);
                sentNumSimiPairList.add(sentNumSimiPairs);

            }
            // 构造摘要
            int wordsCountInSummary = 0;
            StringBuilder sb_summary = new StringBuilder();
            Map<Integer, Set<Integer>> selectedSentNum = new HashMap<Integer, Set<Integer>>();
            while (wordsCountInSummary <= MAX_SUMMARY_WORDS_COUNT) {
                // int k = 0;
                boolean flag = false;
                // log.info("Class别数：" + sentNumSimiPairList.size());
                for (int i = 0; i < sentNumSimiPairList.size(); ++i) {
                    Set<Integer> sentNumSet = selectedSentNum.get(i);
                    SentenceSilimarityAttribute attr = aicfssList.get(i);
                    for (SentNumSimiPair sentNumSimiPair : sentNumSimiPairList.get(i)) {
                        int sentNum = sentNumSimiPair.getSentNum();
                        if (sentNumSet == null) {
                            sentNumSet = new HashSet<Integer>();
                            sentNumSet.add(sentNum);
                            selectedSentNum.put(i, sentNumSet);
                            List<String> words = attr.getSentences().get(sentNum).words;
                            wordsCountInSummary += words.size();
                            sb_summary.append(ZelusUtils.list2String(words) + LINE_SPLITER);
                            flag = true;
                            break;
                        } else {
                            if (!sentNumSet.contains(sentNum)) {
                                sentNumSet.add(sentNum);
                                selectedSentNum.put(i, sentNumSet);
                                List<String> words = attr.getSentences().get(sentNum).words;
                                wordsCountInSummary += words.size();
                                sb_summary.append(ZelusUtils.list2String(words) + LINE_SPLITER);
                                flag = true;
                                break;
                            }
                        }
                    }
                    // ++k;
                }
                if (!flag) {
                    break;
                }
                log.info("字数：" + wordsCountInSummary);
            }

            FileUtils.writeStringToFile(FileUtils.getFile(this.summaryFilePath), ZelusUtils.cutLastLineSpliter(sb_summary.toString()), DEFAULT_CHARSET);

        } catch (IOException e) {

            this.log.error("", e);

        } finally {

            if (iterator != null) {
                try {
                    iterator.close();
                } catch (IOException e) {
                    log.error("Close iterator error!", e);
                }
            }

        }
        return true;
    }

    private double cosineDistence(float[] vec1, float[] vec2) {

        double value = -1;

        if (vec1 == null || vec2 == null) {
            return value;
        }

        //Calculate event similarity using vector cosine
        double scalar = 0;  //Inner product of two vectors
        double module_1 = 0, module_2 = 0;  //Magnitudes of vec1 and vec2
        for (int i = 0; i < vec1.length; ++i) {
            scalar += vec1[i] * vec2[i];
            module_1 += vec1[i] * vec1[i];
            module_2 += vec2[i] * vec2[i];
        }

        if (module_1 > 0 && module_2 > 0) {
            value = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)) + 1;
        }

        return value;

    }

}
