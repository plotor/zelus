package org.zhenchao.zelus.domain;

import org.zhenchao.zelus.common.pojo.Pair;

import java.io.Serializable;
import java.util.List;

/**
 * 类别实体增强版
 *
 * @author zhenchao.Wang 2016-1-28 17:24:02
 */
public class ClustItemPlus implements Serializable, Comparable<ClustItemPlus> {

    private static final long serialVersionUID = -4111534585689243654L;

    /** clust名称 */
    private String name;

    /** clust下面的句子集合 */
    private List<Pair<Float, String>> sentences;

    /** 当前类别大小 */
    private int size;

    /** 多样性（包含的文件数） */
    private int diversity;

    /** 权重均值 */
    private float avgWeight = 0.0f;

    public ClustItemPlus() {
        super();
    }

    public ClustItemPlus(String name, List<Pair<Float, String>> sentences, int size, int diversity, float avgWeight) {
        super();
        this.name = name;
        this.sentences = sentences;
        this.size = size;
        this.diversity = diversity;
        this.avgWeight = avgWeight;
    }

    @Override
    public int compareTo(ClustItemPlus other) {
        return Float.compare(this.avgWeight, other.getAvgWeight());
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Pair<Float, String>> getSentences() {
        return this.sentences;
    }

    public void setSentences(List<Pair<Float, String>> sentences) {
        this.sentences = sentences;
    }

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getDiversity() {
        return this.diversity;
    }

    public void setDiversity(int diversity) {
        this.diversity = diversity;
    }

    public float getAvgWeight() {
        return this.avgWeight;
    }

    public void setAvgWeight(float avgWeight) {
        this.avgWeight = avgWeight;
    }

}
