package edu.whu.cs.nlp.mts.summary;

import java.io.IOException;
import java.util.ArrayList;
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
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.Pair;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;

/**
 * 基于子模函数生成多文档摘要
 *
 * @author zhenchao.wang 2016-1-17 17:06:39
 *
 */
public class SummaryBuilder implements Callable<Boolean>, SystemConstant {

    private static Logger log              = Logger.getLogger(SummaryBuilder.class);

    /** 工作目录 */
    private final String  workDir;

    /** 主题文件名 */
    private final String  filename;

    /** 每个主题下面选取的句子的数量 */
    private Integer       sentCountInClust = 10;

    public SummaryBuilder(String workDir, String filename, int sentCountInClust) {
        super();
        this.workDir = workDir;
        this.filename = filename;
        this.sentCountInClust = Integer.valueOf(sentCountInClust);
    }

    @Override
    public Boolean call() throws Exception {

        log.info("[Thread id:" + Thread.currentThread().getId() + "] is building summary for[" + this.workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "]");

        // 加载当前主题下面的句子，每个类别控制句子数量
        Map<String, List<Pair<Float, String>>> candidateSentences = this.loadSentences(this.sentCountInClust);

        // 加载每个clust的权值
        String clusterWeightFilepath = this.workDir + "/" + SystemConstant.DIR_CLUSTER_WEIGHT + "/" + this.filename.substring(0, this.filename.length() - 4) + "." + SystemConstant.OBJ;
        log.info("Loading serilized file[" + clusterWeightFilepath + "]");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Float> clusterWeights = (Map<String, Float>) SerializeUtil.readObj(clusterWeightFilepath);
        }catch(IOException e) {
            log.error("Load serilized file[" + clusterWeightFilepath + "] error!", e);
            throw e;
        }
        log.info("Load serilized file[" + clusterWeightFilepath + "] successed!");

        /*
         * 在保证摘要总字数不超过规定字数的前提下，
         * 按照句子的综合得分（主题贡献分，查询覆盖度，多样性得分）循环从候选句子中选取句子
         */
        /*历史主题贡献得分*/
        float historyTopicScore = 0.0f;
        /*历史查询覆盖度得分*/
        float historyQueryScore = 0.0f;
        /*历史多样性得分*/
        float historyDiversityScore = 0.0f;
        int summaryWordCount = 0;
        // 判断候选集合中是否还有句子
        boolean isNotEmpty = true;
        while(isNotEmpty && summaryWordCount < MAX_SUMMARY_WORDS_COUNT) {
            isNotEmpty = false;
            for (Entry<String, List<Pair<Float, String>>> entry : candidateSentences.entrySet()) {
                String currentClustKey = entry.getKey();
                List<Pair<Float, String>> pairs = entry.getValue();
                if(CollectionUtils.isNotEmpty(pairs)) {
                    isNotEmpty = true;
                    for (Pair<Float, String> pair : pairs) {
                        // 1.计算当前句子的主题贡献分

                        // 2.计算当前句子的查询覆盖度

                        // 3.计算当前句子的多样性得分
                    }
                }

            }
        }



        log.info("[Thread id:" + Thread.currentThread().getId() + "] build summary for[" + this.workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION + "/" + this.filename + "] finished!");
        return true;
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
     * @param selectedSentences 已经选取的句子集合
     * @param candidateSentence 候选句子
     * @param clusterKey 当前cluster的key
     * @param clusterWeights 所有clust的权值
     * @return
     */
    private float topicScore(Map<String, List<Pair<Float, String>>> selectedSentences, Pair<Float, String> candidateSentence, Float currentClusterWeights) {

        float score = 0.0f;

        return score;

    }

}
