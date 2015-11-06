package edu.whu.cs.nlp.mts.base.domain;

/**
 * 口哨算法运行参数bean
 *
 * @author Apache_xiaochao
 *
 */
public class CWRunParam {

    private String jarPath;                    // jar文件所在路径
    private String nodeFilePath;               // nodes文件所在路径
    private String edgeFilePath;               // edges文件所在路径
    private String algorithm_opt = "dist_log"; // 节点之间近似度计算模式
    private int    edgeWeightThreshold;        // 边入选聚类的权值
    private float  keepClassRate;
    private String mutation_mode = "constant";
    private float  mutation_rate;
    private int    iterCount     = 100;        // 迭代次数
    private String resultFilePath;             // 结果文件存放路径

    public String getAlgorithm_opt() {
        return this.algorithm_opt;
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

    public String getMutation_mode() {
        return this.mutation_mode;
    }

    public float getMutation_rate() {
        return this.mutation_rate;
    }

    public String getNodeFilePath() {
        return this.nodeFilePath;
    }

    public String getResultFilePath() {
        return this.resultFilePath;
    }

    public void setAlgorithm_opt(String algorithm_opt) {
        this.algorithm_opt = algorithm_opt;
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

    public void setMutation_mode(String mutation_mode) {
        this.mutation_mode = mutation_mode;
    }

    public void setMutation_rate(float mutation_rate) {
        this.mutation_rate = mutation_rate;
    }

    public void setNodeFilePath(String nodeFilePath) {
        this.nodeFilePath = nodeFilePath;
    }

    public void setResultFilePath(String resultFilePath) {
        this.resultFilePath = resultFilePath;
    }

    @Override
    public String toString() {
        return "java -jar " + this.jarPath + " -F -i " + this.nodeFilePath + " " + this.edgeFilePath + " -a " + this.algorithm_opt + " -t "
                + this.edgeWeightThreshold + " -k " + this.keepClassRate + " -m " + this.mutation_mode + " " + this.mutation_rate + " -d "
                + this.iterCount + " -o " + this.resultFilePath;
    }

}
