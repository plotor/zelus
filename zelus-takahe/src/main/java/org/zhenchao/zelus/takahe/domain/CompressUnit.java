package org.zhenchao.zelus.takahe.domain;

import org.apache.commons.collections.CollectionUtils;
import org.zhenchao.zelus.common.pojo.Word;
import org.zhenchao.zelus.common.util.ZelusUtils;

import java.io.Serializable;
import java.util.List;

/**
 * 压缩Output语句单元
 *
 * @author zhenchao 2015-11-12 16:08:46
 */
public class CompressUnit implements Comparable<CompressUnit>, Serializable {

    private static final long serialVersionUID = 2448812084641441217L;

    /** 压缩得分，可能为0 */
    private float score;
    /** 压缩Output句子 */
    /*private String sentence;*/

    private List<Word> sentence;

    public CompressUnit(float score, List<Word> sentence) {
        super();
        this.score = score;
        this.sentence = sentence;
    }

    @Override
    public String toString() {
        return this.score + "#" + ZelusUtils.wordsToSentence(this.sentence);
    }

    /**
     * Current sentence子Word count，不包含标点
     *
     * @return
     */
    public int wordsCount() {

        int count = 0;

        if (CollectionUtils.isEmpty(this.sentence)) {
            return count;
        }

        for (Word word : this.sentence) {
            if (ZelusUtils.isPunctuation(word)) {
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

    public List<Word> getSentence() {
        return this.sentence;
    }

    public void setSentence(List<Word> sentence) {
        this.sentence = sentence;
    }

    @Override
    public int compareTo(CompressUnit other) {

        return Float.compare(other.getScore(), this.getScore());

    }

}
