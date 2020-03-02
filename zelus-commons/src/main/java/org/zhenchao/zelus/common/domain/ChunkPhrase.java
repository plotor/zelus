package org.zhenchao.zelus.common.domain;

import java.util.List;

/**
 * chunk处理的短语单元
 *
 * @author ZhenchaoWang 2015-11-1 16:01:38
 */
public class ChunkPhrase {

    private Integer leftIndex;  // 短语第一个单词的index
    private Integer rightIndex; // 短语最后一个单词的index
    private List<Word> words;      // 短语中的所有单词

    public ChunkPhrase(Integer leftIndex, Integer rightIndex, List<Word> words) {
        super();
        this.leftIndex = leftIndex;
        this.rightIndex = rightIndex;
        this.words = words;
    }

    @Override
    public String toString() {
        return "ChunkPhrase [leftIndex=" + this.leftIndex + ", rightIndex=" + this.rightIndex + ", words=" + this.words
                + "]";
    }

    public Integer getLeftIndex() {
        return this.leftIndex;
    }

    public void setLeftIndex(Integer leftIndex) {
        this.leftIndex = leftIndex;
    }

    public Integer getRightIndex() {
        return this.rightIndex;
    }

    public void setRightIndex(Integer rightIndex) {
        this.rightIndex = rightIndex;
    }

    public List<Word> getWords() {
        return this.words;
    }

    public void setWords(List<Word> words) {
        this.words = words;
    }

}
