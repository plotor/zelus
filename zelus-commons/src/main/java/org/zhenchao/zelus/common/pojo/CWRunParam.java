package org.zhenchao.zelus.common.pojo;

/**
 * 口哨算法运行参数bean
 *
 * @author Apache_xiaochao
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class CWRunParam {

    private static final int DEFAULT_ITER_COUNT = 100;

    private String jarPath;
    private String nodeFilePath;
    private String edgeFilePath;
    private String algorithmOpt = "dist_log";
    private int edgeWeightThreshold;
    private float keepClassRate;
    private String mutationMode = "constant";
    private float mutationRate;
    private int iterCount = DEFAULT_ITER_COUNT;
    private String resultFilePath;

    public String getAlgorithmOpt() {
        return this.algorithmOpt;
    }

    public String getEdgeFilePath() {
        return this.edgeFilePath;
    }

    public int getEdgeWeightThreshold() {
        return this.edgeWeightThreshold;
    }

    public int getIterationCount() {
        return this.iterCount;
    }

    public String getJarPath() {
        return this.jarPath;
    }

    public float getKeepClassRate() {
        return this.keepClassRate;
    }

    public String getMutationMode() {
        return this.mutationMode;
    }

    public float getMutationRate() {
        return this.mutationRate;
    }

    public String getNodeFilePath() {
        return this.nodeFilePath;
    }

    public String getResultFilePath() {
        return this.resultFilePath;
    }

    public void setAlgorithmOpt(String algorithmOpt) {
        this.algorithmOpt = algorithmOpt;
    }

    public void setEdgeFilePath(String edgeFilePath) {
        this.edgeFilePath = edgeFilePath;
    }

    public void setEdgeWeightThreshold(int edgeWeightThreshold) {
        this.edgeWeightThreshold = edgeWeightThreshold;
    }

    public void setIterationCount(int iterCount) {
        this.iterCount = iterCount;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public void setKeepClassRate(float keepClassRate) {
        this.keepClassRate = keepClassRate;
    }

    public void setMutationMode(String mutationMode) {
        this.mutationMode = mutationMode;
    }

    public void setMutationRate(float mutationRate) {
        this.mutationRate = mutationRate;
    }

    public void setNodeFilePath(String nodeFilePath) {
        this.nodeFilePath = nodeFilePath;
    }

    public void setResultFilePath(String resultFilePath) {
        this.resultFilePath = resultFilePath;
    }

    @Override
    public String toString() {
        return "java -jar " + this.jarPath
                + " -F -i " + this.nodeFilePath
                + " " + this.edgeFilePath
                + " -a " + this.algorithmOpt
                + " -t " + this.edgeWeightThreshold
                + " -k " + this.keepClassRate
                + " -m " + this.mutationMode
                + " " + this.mutationRate
                + " -d " + this.iterCount
                + " -o " + this.resultFilePath;
    }

}
