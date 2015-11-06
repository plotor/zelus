package edu.whu.cs.nlp.mts.base.domain;

/**
 * 记录当前句子的编号，以及与问句的相似度 实现了Comparable接口，按照相似度由小到大进行排序
 *
 * @author Apache_xiaochao
 *
 */
public class SentNumSimiPair implements Comparable<SentNumSimiPair> {

    private int    sentNum;    // 句子序号
    private double similarity; // 与问句的相似度

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
            return -1;
        }
        // return this.similarity - similarity;
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
