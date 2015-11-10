package edu.whu.cs.nlp.msc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.FileLoader;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.AttributeInClassForSentenceSilimarity;
import edu.whu.cs.nlp.mts.base.domain.SentNumSimiPair;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;

/**
 * 对压缩处理后的句子集合按照TF-IDF值来选取
 *
 * @author Apache_xiaochao
 *
 */
public class CompressedSentencesSelectThread implements Runnable, SystemConstant {

    public final static int MAX_SENTENCE_COUNT = 51;                               // 每个类别的最大句子数
    private final Logger    log                = Logger.getLogger(this.getClass());
    private final String    compressedFilePath;                                    // 压缩语句文件名
    private final String    summaryFilePath;                                       // 摘要文件名
    private final String    question;                                              // 问题

    public CompressedSentencesSelectThread(String compressedFilePath, String summaryFilePath, String question) {
        super();
        this.compressedFilePath = compressedFilePath;
        this.summaryFilePath = summaryFilePath;
        this.question = question;
    }

    // 计算两个向量之间的余弦值
    private double cosine(float vec_1[], float vec_2[]) {
        if (vec_1.length != vec_2.length) {
            return -1;
        }
        // 利用向量余弦值来计算事件之间的相似度
        double scalar = 0; // 两个向量的内积
        double module_1 = 0, module_2 = 0; // 向量vec_1和vec_2的模
        for (int i = 0; i < vec_1.length; ++i) {
            scalar += vec_1[i] * vec_2[i];
            module_1 += vec_1[i] * vec_1[i];
            module_2 += vec_2[i] * vec_2[i];
        }
        double approx = -1;
        if (module_1 > 0 && module_2 > 0) {
            approx = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2));
        }
        return approx;
    }

    @Override
    public void run() {
        this.log.info(Thread.currentThread() + "is running..." + this.compressedFilePath);
        BufferedReader br = null;
        try {
            br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(this.compressedFilePath), DEFAULT_CHARSET));
            String line = null;
            AttributeInClassForSentenceSilimarity aicfss = null;
            final List<AttributeInClassForSentenceSilimarity> aicfssList = new ArrayList<AttributeInClassForSentenceSilimarity>();
            int[][] wordsCountInSentence; // 记录每个单词在每个句子中出现的次数，行表示单词，列表示句子
            int wordNum = 0; // 类别中的单词序号
            final StringBuilder sb_summary = new StringBuilder();
            final List<List<SentNumSimiPair>> sentNumSimiPairList = new ArrayList<List<SentNumSimiPair>>();
            while ((line = br.readLine()) != null) {
                line = line.trim();
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
                            final List<String> wordsInSentence = aicfss.getSentences().get(i).words;
                            // System.out.println(i + "\t" +
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
                                sentenceVector[entry
                                               .getValue()][i] = (float) ((wordsCountInSentence[entry.getValue()][i]
                                                       / (float) aicfss.getSentences().get(i).words.size())
                                                       * Math.log(aicfss.getSentences().size()
                                                               / (double) sentCount4words.get(entry.getKey())));
                            }
                        }

                        // 计算压缩得到的句子与问句之间的相似度，余弦定理
                        final float[] questionVec = new float[aicfss.getWords().size()];
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
                            final double approx = this.cosine(questionVec, sentenceVec);
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
                    final double approx = this.cosine(questionVec, sentenceVec);
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
                // sb_summary.append(CommonUtil.list2String(aicfss.getSentences().get(sentenceNum).words)
                // + LINE_SPLITER);
                // FileUtil.write(summaryFilePath,
                // CommonUtil.cutLastLineSpliter(sb_summary.toString()),
                // DEFAULT_CHARSET);
                // 打印测试
                /*
                 * System.out.println(line + ",单词数：" +
                 * aicfss.getWords().size());
                 */
                /*
                 * for (Entry<String, Integer> entry :
                 * aicfss.getWords().entrySet()) {
                 * System.out.println(Arrays.toString(wordsCountInSentence[entry
                 * .getValue()]));
                 * System.out.println(Arrays.toString(sentenceVector[entry.
                 * getValue()])); }
                 */
                /*
                 * System.out.println(); for (Entry<String, Integer> entry :
                 * aicfss.getWords().entrySet()) {
                 * System.out.println(Arrays.toString(sentenceVector[entry.
                 * getValue()])); }
                 */
            }
            // 构造摘要
            int wordsCountInSummary = 0;
            // StringBuilder sb_summary = new StringBuilder();
            final Map<Integer, Set<Integer>> selectedSentNum = new HashMap<Integer, Set<Integer>>();
            while (wordsCountInSummary < MAX_SUMMARY_WORDS_COUNT) {
                // int k = 0;
                boolean flag = false;
                // System.out.println("类别数：" + sentNumSimiPairList.size());
                for (int i = 0; i < sentNumSimiPairList.size(); ++i) {
                    Set<Integer> sentNumSet = selectedSentNum.get(i);
                    final AttributeInClassForSentenceSilimarity attr = aicfssList.get(i);
                    for (final SentNumSimiPair sentNumSimiPair : sentNumSimiPairList.get(i)) {
                        final int sentNum = sentNumSimiPair.getSentNum();
                        if (sentNumSet == null) {
                            sentNumSet = new HashSet<Integer>();
                            sentNumSet.add(sentNum);
                            selectedSentNum.put(i, sentNumSet);
                            final List<String> words = attr.getSentences().get(sentNum).words;
                            wordsCountInSummary += words.size();
                            sb_summary.append(CommonUtil.list2String(words) + LINE_SPLITER);
                            flag = true;
                            break;
                        } else {
                            if (!sentNumSet.contains(sentNum)) {
                                sentNumSet.add(sentNum);
                                selectedSentNum.put(i, sentNumSet);
                                final List<String> words = attr.getSentences().get(sentNum).words;
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
                // System.out.println("字数：" + wordsCountInSummary);
            }
            FileLoader.write(this.summaryFilePath, CommonUtil.cutLastLineSpliter(sb_summary.toString()), DEFAULT_CHARSET);
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            this.log.error("", e);
            // e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (final IOException e) {
                    // TODO Auto-generated catch block
                    this.log.error("关闭文件异常", e);
                    // e.printStackTrace();
                }
            }
        }
    }

    /*
     * public static void main(String[] args) { Thread thread = new Thread(new
     * CompressedSentencesSelectThread("src/tmp/D0704A",
     * "src/tmp/D0704A.summary",
     * "Describe the activities of Morris Dees and the Southern Poverty Law Center."
     * )); thread.start(); }
     */

}
