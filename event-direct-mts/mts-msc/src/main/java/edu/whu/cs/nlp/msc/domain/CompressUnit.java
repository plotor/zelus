package edu.whu.cs.nlp.msc.domain;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;

/**
 * 压缩输出语句单元
 *
 * @author ZhenchaoWang 2015-11-12 16:08:46
 *
 */
public class CompressUnit implements Comparable<CompressUnit>, Serializable {

    private static final long serialVersionUID = 2448812084641441217L;

    /** 压缩得分，可能为0 */
    private float  score;
    /** 压缩输出句子 */
    private String sentence;

    public CompressUnit(float score, String sentence) {
        super();
        this.score = score;
        this.sentence = sentence;
    }

    @Override
    public String toString() {
        return this.score + "#" + this.sentence;
    }

    /**
     * 当前句子单词数，不包含标点
     *
     * @return
     */
    public int wordsCount() {

        int count = 0;

        if(StringUtils.isBlank(this.sentence)) {
            return count;
        }

        String[] strs = this.sentence.split("\\s+");
        for(int i = 0; i < strs.length; i++) {
            if(SystemConstant.PUNCT_EN.contains(strs[i])) {
                continue;
            }
            count++;
        }

        return count;

    }

    public float getScore() {
        return this.score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public String getSentence() {
        return this.sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    @Override
    public int compareTo(CompressUnit other) {
        if(this.score < other.getScore()) {
            return 1;
        } else if(this.score == other.getScore()) {
            return 0;
        } else {
            return -1;
        }
    }

}
