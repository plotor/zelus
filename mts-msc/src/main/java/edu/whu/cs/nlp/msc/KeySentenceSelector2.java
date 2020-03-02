package edu.whu.cs.nlp.msc;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.domain.AttributeInClassForSentenceSilimarity;
import org.zhenchao.zelus.common.domain.SentNumSimiPair;
import org.zhenchao.zelus.common.global.GlobalConstant;
import org.zhenchao.zelus.common.util.CommonUtil;

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
 * 对压缩处理后的句子集合按照TF-IDF值来选取
 *
 * @author Apache_xiaochao
 */
public class KeySentenceSelector2 implements Callable<Boolean>, GlobalConstant {

    private final Logger log = Logger.getLogger(this.getClass());

    public final static int MAX_SENTENCE_COUNT = 51;                               // 每个类别的最大句子数

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

            AttributeInClassForSentenceSilimarity aicfss = null;
            List<AttributeInClassForSentenceSilimarity> aicfssList = new ArrayList<AttributeInClassForSentenceSilimarity>();
            int[][] wordsCountInSentence; // 记录每个单词在每个句子中出现的次数，行表示单词，列表示句子
            int wordNum = 0; // 类别中的单词序号
            //StringBuilder sb_summary = new StringBuilder();
            List<List<SentNumSimiPair>> sentNumSimiPairList = new ArrayList<List<SentNumSimiPair>>();

            while (iterator.hasNext()) {

                String line = iterator.nextLine();

                if (line.startsWith("classes_")) {

                    if (aicfss != null) {

                        aicfssList.add(aicfss);

                        // 表示有数据未处理，进行处理
                        // 统计每个单词在每个句子中的数量，多语句压缩默认最多输出50句，再加上一个问句
                        wordsCountInSentence = new int[aicfss.getWords().size()][MAX_SENTENCE_COUNT];
                        for (int i = 0; i < MAX_SENTENCE_COUNT; ++i) {
                            if (i == aicfss.getSentences().size()) {
                                break;
                            }
                            List<String> wordsInSentence = aicfss.getSentences().get(i).words;

                            // 创建当前句子对应的单词set集合，提高运行速度
                            Set<String> tmpWordSet = new HashSet<String>();
                            for (String word : wordsInSentence) {
                                tmpWordSet.add(word.toLowerCase());
                            }
                            for (Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                                if (tmpWordSet.contains(entry.getKey().toLowerCase())) {
                                    // 当前句子中包含该单词，对应单词数+1
                                    wordsCountInSentence[entry.getValue()][i] += 1;
                                }
                            }
                        }
                        // 计算各语句的向量，利用TF-IDF
                        Map<String, Integer> sentCount4words = new HashMap<String, Integer>(); // 用来记录包含指定单词的句子数
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

                        // 计算压缩得到的句子与问句之间的相似度，余弦定理
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
                            // 计算两个向量的余弦值
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
                        // sb_summary.append(CommonUtil.list2String(aicfss.getSentences().get(sentenceNum).words)
                        // + LINE_SPLITER);
                        // 打印测试
                        /*
                         * System.out.println(line + ",单词数：" +
                         * aicfss.getWords().size()); for (Entry<String,
                         * Integer> entry : aicfss.getWords().entrySet()) {
                         * System.out.println(entry.getKey() + "\t" +
                         * Arrays.toString(wordsCountInSentence[entry.getValue()
                         * ])); }
                         */
                        // return;
                    }

                    // 清空之前的处理结果
                    aicfss = new AttributeInClassForSentenceSilimarity(
                            new ArrayList<AttributeInClassForSentenceSilimarity.Sentence>(),
                            new HashMap<String, Integer>());
                    wordNum = 0;
                    // 每个类别的第一句存放问句
                    final List<String> wordsInQuestions = Arrays.asList(this.question.split("\\s+"));
                    aicfss.getSentences().add(aicfss.new Sentence(-1, wordsInQuestions));
                    for (final String word : wordsInQuestions) {
                        if (!aicfss.getWords().containsKey(word.toLowerCase())) {
                            // 往单词map中添加单词，序号递增
                            aicfss.getWords().put(word.toLowerCase(), wordNum++);
                        }
                    }
                } else {
                    // 将文件中的句子表示成内存中的数据结构
                    final int firstSpliterIndex = line.indexOf("#");
                    final float compressedQuality = Float.parseFloat(line.substring(0, firstSpliterIndex));
                    final List<String> wordsInSentence = Arrays
                            .asList(line.substring(firstSpliterIndex + 1).split("\\s+"));
                    aicfss.getSentences().add(aicfss.new Sentence(compressedQuality, wordsInSentence));
                    for (final String word : wordsInSentence) {
                        if (!aicfss.getWords().containsKey(word.toLowerCase())) {
                            // 往单词map中添加单词，序号递增
                            aicfss.getWords().put(word.toLowerCase(), wordNum++);
                        }
                    }
                }
            }
            // 处理最后一个类别的数据
            if (aicfss != null) {
                aicfssList.add(aicfss);
                // 表示有数据未处理，进行处理
                // 统计每个单词在每个句子中的数量，多语句压缩默认最多输出50句，再加上一个问句
                wordsCountInSentence = new int[aicfss.getWords().size()][MAX_SENTENCE_COUNT];
                for (int i = 0; i < MAX_SENTENCE_COUNT; ++i) {
                    if (i == aicfss.getSentences().size()) {
                        break;
                    }
                    final List<String> wordsInSentence = aicfss.getSentences().get(i).words;
                    // System.out.println(">>" + i + "\t" +
                    // wordsInSentence.toString());
                    // 创建当前句子对应的单词set集合，提高运行速度
                    final Set<String> tmpWordSet = new HashSet<String>();
                    for (final String word : wordsInSentence) {
                        tmpWordSet.add(word.toLowerCase());
                    }
                    for (final Entry<String, Integer> entry : aicfss.getWords().entrySet()) {
                        if (tmpWordSet.contains(entry.getKey().toLowerCase())) {
                            // 当前句子中包含该单词，对应单词数+1
                            wordsCountInSentence[entry.getValue()][i] += 1;
                        }
                    }
                }

                // 计算各语句的向量，利用TF-IDF
                final Map<String, Integer> sentCount4words = new HashMap<String, Integer>(); // 用来记录包含指定单词的句子数
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

                // 计算压缩得到的句子与问句之间的相似度，余弦定理
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
                    // 计算两个向量的余弦值
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
                // System.out.println("类别数：" + sentNumSimiPairList.size());
                for (int i = 0; i < sentNumSimiPairList.size(); ++i) {
                    Set<Integer> sentNumSet = selectedSentNum.get(i);
                    AttributeInClassForSentenceSilimarity attr = aicfssList.get(i);
                    for (SentNumSimiPair sentNumSimiPair : sentNumSimiPairList.get(i)) {
                        int sentNum = sentNumSimiPair.getSentNum();
                        if (sentNumSet == null) {
                            sentNumSet = new HashSet<Integer>();
                            sentNumSet.add(sentNum);
                            selectedSentNum.put(i, sentNumSet);
                            List<String> words = attr.getSentences().get(sentNum).words;
                            wordsCountInSummary += words.size();
                            sb_summary.append(CommonUtil.list2String(words) + LINE_SPLITER);
                            flag = true;
                            break;
                        } else {
                            if (!sentNumSet.contains(sentNum)) {
                                sentNumSet.add(sentNum);
                                selectedSentNum.put(i, sentNumSet);
                                List<String> words = attr.getSentences().get(sentNum).words;
                                wordsCountInSummary += words.size();
                                sb_summary.append(CommonUtil.list2String(words) + LINE_SPLITER);
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
                System.out.println("字数：" + wordsCountInSummary);
            }

            FileUtils.writeStringToFile(FileUtils.getFile(this.summaryFilePath), CommonUtil.cutLastLineSpliter(sb_summary.toString()), DEFAULT_CHARSET);

        } catch (IOException e) {

            this.log.error("", e);

        } finally {

            if (iterator != null) {
                iterator.close();
            }

        }
        return true;
    }

    private double cosineDistence(float[] vec1, float[] vec2) {

        double value = -1;

        if (vec1 == null || vec2 == null) {
            return value;
        }

        //利用向量余弦值来计算事件之间的相似度
        double scalar = 0;  //两个向量的内积
        double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
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
