package org.zhenchao.zelus.common.pojo;

/**
 * 记录当前句子的编号，以及与问句的相似度
 * 实现了Comparable接口，按照相似度由小到大进行排序
 *
 * @author Apache_xiaochao
 */
public class SentNumSimiPair implements Comparable<SentNumSimiPair> {

    private static final int NEGATIVE_ONE = -1;

    private int sentNum;
    private double similarity;

    public SentNumSimiPair(int sentNum, double similarity) {
        super();
        this.sentNum = sentNum;
        this.similarity = similarity;
    }

    @Override
    public int compareTo(SentNumSimiPair sentNumSimiPair) {
        if (this.similarity < sentNumSimiPair.similarity) {
            return 1;
        } else if (this.similarity == sentNumSimiPair.similarity) {
            return 0;
        } else {
            return NEGATIVE_ONE;
        }
    }

    public int getSentNum() {
        return this.sentNum;
    }

    public double getSimilarity() {
        return this.similarity;
    }

    public void setSentNum(int sentNum) {
        this.sentNum = sentNum;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

}
