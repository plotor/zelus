package org.zhenchao.zelus.clustering.domain;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;

/**
 * 句子与其对应的向量
 *
 * @author ZhenchaoWang 2015-11-29 18:38:29
 */
public class SentenceVector implements Serializable {

    private static final long serialVersionUID = -2943493490559564584L;

    private String sentence;

    private float[] vector;

    public SentenceVector(String sentence, float[] vector) {
        super();
        this.sentence = sentence;
        this.vector = vector;
    }

    public SentenceVector(String sentence, String vector) {
        super();
        this.sentence = sentence;
        this.vector = this.parseToFloatVector(vector);
    }

    /**
     * 将字符串表示的向量，转换成浮点数组表示
     *
     * @param vec
     * @return
     */
    public float[] parseToFloatVector(String vec) {

        if (StringUtils.isBlank(vec)) {
            return null;
        }

        String[] strs = vec.trim().split("\\s+");
        float[] vector = new float[strs.length];
        for (int i = 0; i < strs.length; i++) {
            vector[i] = Float.parseFloat(strs[i]);
        }

        return vector;

    }

    public String getSentence() {
        return this.sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public float[] getVector() {
        return this.vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

}
