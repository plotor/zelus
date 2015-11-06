package edu.whu.cs.nlp.mts.base.domain;

import java.util.List;
import java.util.Map;

/**
 * 记录压缩输出的句子集合的一些统计属性
 *
 * @author Apache_xiaochao
 *
 */
public class AttributeInClassForSentenceSilimarity {

    /**
     * 句子实体
     *
     * @author Apache_xiaochao
     *
     */
    public class Sentence {
        public float        compressedQuality; // 压缩质量，数值越小越好，可能存在0
        public List<String> words;             // 句中的单词

        public Sentence(float compressedQuality, List<String> words) {
            super();
            this.compressedQuality = compressedQuality;
            this.words = words;
        }
    }

    private List<Sentence>       sentences;// 句子集合
    private Map<String, Integer> words;    // 单词集合，不重复，key为单词，value为对应的序号

    public AttributeInClassForSentenceSilimarity(List<Sentence> sentences, Map<String, Integer> words) {
        super();
        this.sentences = sentences;
        this.words = words;
    }

    public List<Sentence> getSentences() {
        return this.sentences;
    }

    public Map<String, Integer> getWords() {
        return this.words;
    }

    public void setSentences(List<Sentence> sentences) {
        this.sentences = sentences;
    }

    public void setWords(Map<String, Integer> words) {
        this.words = words;
    }

    // private int wordsCount; //单词总数
    // private int[][] wordsCountInSentence; //每个单词在每个句子中的出现次数，行表示单词，列表示句子

}
