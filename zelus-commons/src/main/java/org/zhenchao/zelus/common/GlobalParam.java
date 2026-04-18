package org.zhenchao.zelus.common;

/**
 * Global parameters
 *
 * @author zhenchao 2016-1-29 20:22:22
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class GlobalParam {

    /** Thread count */
    public static String workDir;

    /** Cache name */
    public static String cacheName = "db_cache_vec";

    /** Data源名称 */
    public static String datasource;

    /** wordnetWordVector所在路径 */
    public static String wordnetDictPath;

    /** Chinese Whispers algorithmClustering选边阈值 */
    public static float edgeWeightThresh;

    /** n-gram模型所在路径 */
    public static String ngramModelPath;

    /** Question filename */
    public static String questionFilename;

    /** idf值文件名 */
    public static String idfFilename;

    /** Word vector filename */
    public static String vecFilename;

    /** Run mode */
    public static String runMode;

    /** 每个Class别中选取的句子的上限数 */
    public static int sentenceCountThresh;

    /** 相似度上限 */
    public static float similarityThresh;

    /** 摘要Parameter */
    public static float alpha4summary = 1.0f;

    /** 摘要Parameter */
    public static float beta4summary = 1.0f;

    public static void setWorkDir(String workDir) {
        GlobalParam.workDir = workDir;
    }

    public static void setCacheName(String cacheName) {
        GlobalParam.cacheName = cacheName;
    }

    public static void setDatasource(String datasource) {
        GlobalParam.datasource = datasource;
    }

    public static void setWordnetDictPath(String wordnetDictPath) {
        GlobalParam.wordnetDictPath = wordnetDictPath;
    }

    public static void setEdgeWeightThresh(float edgeWeightThresh) {
        GlobalParam.edgeWeightThresh = edgeWeightThresh;
    }

    public static void setNgramModelPath(String ngramModelPath) {
        GlobalParam.ngramModelPath = ngramModelPath;
    }

    public static void setQuestionFilename(String questionFilename) {
        GlobalParam.questionFilename = questionFilename;
    }

    public static void setIdfFilename(String idfFilename) {
        GlobalParam.idfFilename = idfFilename;
    }

    public static void setVecFilename(String vecFilename) {
        GlobalParam.vecFilename = vecFilename;
    }

    public static void setRunMode(String runMode) {
        GlobalParam.runMode = runMode;
    }

    public static void setSentenceCountThresh(int sentenceCountThresh) {
        GlobalParam.sentenceCountThresh = sentenceCountThresh;
    }

    public static void setSimilarityThresh(float similarityThresh) {
        GlobalParam.similarityThresh = similarityThresh;
    }

    public static void setAlpha4summary(float alpha4summary) {
        GlobalParam.alpha4summary = alpha4summary;
    }

    public static void setBeta4summary(float beta4summary) {
        GlobalParam.beta4summary = beta4summary;
    }

}
