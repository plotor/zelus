package org.zhenchao.zelus.common.pojo;

import java.util.List;
import java.util.Map;

/**
 * 记录压缩输出的句子集合的一些统计属性
 *
 * @author Apache_xiaochao
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public class SentenceSilimarityAttribute {

    /**
     * 句子实体
     *
     * @author Apache_xiaochao
     */
    public static class Sentence {
        public float compressedQuality;
        public List<String> words;

        public Sentence(float compressedQuality, List<String> words) {
            super();
            this.compressedQuality = compressedQuality;
            this.words = words;
        }
    }

    private List<Sentence> sentences;
    private Map<String, Integer> words;

    public SentenceSilimarityAttribute(List<Sentence> sentences, Map<String, Integer> words) {
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

}
