package org.zhenchao.zelus.domain;

import org.zhenchao.zelus.common.domain.Pair;

import java.io.Serializable;
import java.util.List;

/**
 * 类别实体
 *
 * @author zhenchao.Wang 2016-1-25 13:18:59
 */
public class ClustItem implements Serializable {

    private static final long serialVersionUID = -4111534585689243654L;

    /** clust名称 */
    private String name;

    /** clust下面的句子集合 */
    private List<Pair<Float, String>> sentences;

    /** 当前类别大小 */
    private int size;

    public ClustItem() {
        super();
    }

    public ClustItem(String name, List<Pair<Float, String>> sentences, int size) {
        super();
        this.name = name;
        this.sentences = sentences;
        this.size = size;
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

}
