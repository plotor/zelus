package org.zhenchao.zelus.common.domain;

/**
 * 指代链元素
 *
 * @author ZhenchaoWang 2015-10-27 18:04:03
 */
public class CoreferenceElement {

    /** 当前词或短语 */
    private String element;
    /** 所属类别ID */
    private Integer clusterId;

    private Integer startIndex;

    private Integer endIndex;

    private Integer sentNum;
    /** 指代的词 */
    private CoreferenceElement ref;

    public CoreferenceElement(String element, Integer clusterId, Integer startIndex, Integer endIndex, Integer sentNum,
                              CoreferenceElement ref) {
        super();
        this.element = element;
        this.clusterId = clusterId;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.sentNum = sentNum;
        this.ref = ref;
    }

    @Override
    public String toString() {
        return this.element + "\t->\t" + this.ref.element;
    }

    public String getElement() {
        return this.element;
    }

    public void setElement(String element) {
        this.element = element;
    }

    public Integer getClusterId() {
        return this.clusterId;
    }

    public void setClusterId(Integer clusterId) {
        this.clusterId = clusterId;
    }

    public Integer getStartIndex() {
        return this.startIndex;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Integer getEndIndex() {
        return this.endIndex;
    }

    public void setEndIndex(Integer endIndex) {
        this.endIndex = endIndex;
    }

    public Integer getSentNum() {
        return this.sentNum;
    }

    public void setSentNum(Integer sentNum) {
        this.sentNum = sentNum;
    }

    public CoreferenceElement getRef() {
        return this.ref;
    }

    public void setRef(CoreferenceElement ref) {
        this.ref = ref;
    }

}
