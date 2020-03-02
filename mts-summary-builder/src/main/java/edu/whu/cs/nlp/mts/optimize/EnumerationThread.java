package edu.whu.cs.nlp.mts.optimize;

import edu.whu.cs.nlp.msc.domain.CompressUnit;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.global.GlobalConstant;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 穷举选择句子构建摘要
 *
 * @author ZhenchaoWang 2015-11-13 20:43:50
 */
public class EnumerationThread implements Callable<Boolean>, GlobalConstant {

    private final Logger log = Logger.getLogger(this.getClass());

    /** 按照类别组织的句子 */
    private final List<List<CompressUnit>> rerankedClustedCompressUnits;
    /** 工作目录 */
    private final String workDir;
    /** 当前主题名 */
    private final String topicName;
    /** 每个类别下面的最大句子数 */
    private Integer maxSentenceCount = 5;

    /** 最大摘要数 */
    private Integer maxSummaries = 50000;

    public EnumerationThread(List<List<CompressUnit>> rerankedClustedCompressUnits, String workDir, String topicName, int maxSentenceCount, int maxSummaries) {
        super();
        this.rerankedClustedCompressUnits = rerankedClustedCompressUnits;
        this.workDir = workDir;
        this.topicName = topicName;
        this.maxSentenceCount = Integer.valueOf(maxSentenceCount);
        this.maxSummaries = Integer.valueOf(maxSummaries);
    }

    @Override
    public Boolean call() throws Exception {

        this.log.info(Thread.currentThread().getName() + " is iterate building summary for topic: " + this.topicName);

        // 初始化指示器
        int[] counter = new int[this.rerankedClustedCompressUnits.size()];
        Arrays.fill(counter, 0);

        // 初始化上限指示器
        int[] clusterSizes = new int[this.rerankedClustedCompressUnits.size()];
        for (int i = 0; i < this.rerankedClustedCompressUnits.size(); i++) {
            clusterSizes[i] = Math.min(this.rerankedClustedCompressUnits.get(i).size(), this.maxSentenceCount);
        }

        int itrCount = 0;
        do {

            String flag = Arrays.toString(counter).substring(1, Arrays.toString(counter).length() - 1).replaceAll(",\\s+", "-");

            this.log.info("[" + flag + "] summaru is building...");

            // 迭代构建摘要
            int wordsCount = 0;
            StringBuilder summary = new StringBuilder();
            int classCount = 0;
            for (int i = 0; i < this.rerankedClustedCompressUnits.size(); i++) {
                CompressUnit compressUnit = this.rerankedClustedCompressUnits.get(i).get(counter[i]);
                int count = compressUnit.wordsCount();
                if (count == 0) {
                    continue;
                }
                summary.append(compressUnit.getSentence() + " ");
                wordsCount += count;
                classCount++;
                if (wordsCount > MAX_SUMMARY_WORDS_COUNT) {
                    break;
                }
            }

            // 存储摘要
            int[] subCounter = Arrays.copyOfRange(counter, 0, classCount);
            String filename = Arrays.toString(subCounter).substring(1, Arrays.toString(subCounter).length() - 1).replaceAll(",\\s+", "-");
            File summaryFile = FileUtils.getFile(this.workDir + "/" + DIR_SUMMARY_RESULTS + "/" + this.topicName, filename);
            try {

                FileUtils.writeStringToFile(summaryFile, summary.toString().trim(), DEFAULT_CHARSET);
                itrCount++;
            } catch (IOException e) {

                this.log.error("Save summary[" + summaryFile.getAbsolutePath() + "] error!", e);

            }

        } while (this.isContinue(counter, clusterSizes) && itrCount < this.maxSummaries);

        return true;
    }

    /**
     * 是否继续
     *
     * @param counter
     * @return
     */
    private boolean isContinue(int[] counter, final int[] clusterSizes) {

        boolean over = true;

        if (counter == null || clusterSizes == null) {
            return false;
        }

        if (counter.length != clusterSizes.length) {
            return false;
        }

        for (int i = 0; ; i++) {
            if (i >= counter.length) {
                return false;
            }
            counter[i] += 1;
            if (counter[i] < clusterSizes[i]) {
                break;
            } else {
                counter[i] = 0;
            }
        }

        return over;
    }

}
