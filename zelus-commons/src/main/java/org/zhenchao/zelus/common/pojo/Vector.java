package org.zhenchao.zelus.common.pojo;

import opennlp.tools.util.StringUtil;
import org.zhenchao.zelus.common.Constants;

import java.io.Serializable;

/**
 * WordVector实体
 *
 * @author zhenchao 2015-11-4 11:18:18
 */
public class Vector implements Serializable {

    private static final long serialVersionUID = -5109650996129271001L;

    private Long id;
    private String word;
    private String vec;

    /**
     * 将String表示的VectorConvert成Pairs应的FloatClass型Array
     *
     * @return FloatClass型VectorArray
     */
    public Float[] floatVecs() {

        Float[] floatVec = null;
        if (StringUtil.isEmpty(this.vec)) {
            return floatVec;
        }
        floatVec = new Float[Constants.DIMENSION];
        String[] strs = this.vec.split("\\s+");
        for (int i = 0; i < Constants.DIMENSION; i++) {
            floatVec[i] = Float.parseFloat(strs[i]);
        }

        return floatVec;

    }

    /**
     * 将String表示的VectorConvert成Pairs应的DoubleClass型Array
     *
     * @return DoubleClass型VectorArray
     */
    public Double[] doubleVecs() {

        Double[] doubleVec = null;
        if (StringUtil.isEmpty(this.vec)) {
            return doubleVec;
        }
        doubleVec = new Double[Constants.DIMENSION];
        String[] strs = this.vec.split("\\s+");
        for (int i = 0; i < Constants.DIMENSION; i++) {
            doubleVec[i] = Double.parseDouble(strs[i]);
        }

        return doubleVec;

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
