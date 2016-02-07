package edu.whu.cs.nlp.mts.base.global;

/**
 * 全局参数
 *
 * @author zhenchao.Wang 2016-1-29 20:22:22
 *
 */
public abstract class GlobalParam {

    /** 线程数 */
    public static String workDir;

    /** 缓存名称 */
    public static String cacheName     = "db_cache_vec";

    /** 数据源名称 */
    public static String datasource;

    /** wordnet词向量所在路径 */
    public static String wordnetDictPath;

    /** 口哨算法聚类选边阈值 */
    public static float edgeWeightThresh;

    /** n-gram模型所在路径 */
    public static String ngramModelPath;

    /** 问题文件名 */
    public static String questionFilename;

    /** idf值文件名 */
    public static String idfFilename;

    /** 词向量文件名 */
    public static String vecFilename;

    /** 运行模式 */
    public static String runMode;

    /** 每个类别中选取的句子的上限数 */
    public static int    sentenceCountThresh;

    /** 相似度上限 */
    public static float  similarityThresh;

    /** 摘要参数 */
    public static float  alpha4summary = 1.0f;

    /** 摘要参数 */
    public static float  beta4summary  = 1.0f;

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
