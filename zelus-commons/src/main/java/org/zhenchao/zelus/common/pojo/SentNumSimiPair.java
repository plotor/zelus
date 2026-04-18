package org.zhenchao.zelus.common.pojo;

/**
 * Record sentence number and similarity to the query
 * Implements Comparable, sorted by similarity in ascending order
 *
 * @author zhenchao
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
