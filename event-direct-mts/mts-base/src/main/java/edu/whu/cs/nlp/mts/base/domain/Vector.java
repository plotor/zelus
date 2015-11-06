package edu.whu.cs.nlp.mts.base.domain;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import opennlp.tools.util.StringUtil;

/**
 * 词向量实体
 *
 * @author ZhenchaoWang 2015-11-4 11:18:18
 *
 */
public class Vector {

    private Long   id;
    private String word;
    private String vec; // 向量字符串

    /**
     * 将字符串表示的向量转换成对应的float类型数组
     *
     * @return
     */
    public Float[] floatVecs() {

        Float[] floatVec = new Float[SystemConstant.DIMENSION];
        if (StringUtil.isEmpty(this.vec)) {
            return floatVec;
        }

        String[] strs = this.vec.split("\\s+");
        for (int i = 0; i < SystemConstant.DIMENSION; i++) {
            floatVec[i] = Float.parseFloat(strs[i]);
        }

        return floatVec;

    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return this.word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getVec() {
        return this.vec;
    }

    public void setVec(String vec) {
        this.vec = vec;
    }

}
