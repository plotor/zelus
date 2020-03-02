package org.zhenchao.zelus.clustering.domain;

import java.io.Serializable;

/**
 * 口哨算法边实体
 *
 * @author ZhenchaoWang 2015-11-10 14:40:59
 */
public class CWEdge implements Serializable {

    private static final long serialVersionUID = -1104638487897903829L;

    private Integer from;
    private Integer to;
    private Float weight;

    public CWEdge() {
        super();
    }

    public CWEdge(Integer from, Integer to, Float weight) {
        super();
        this.from = from;
        this.to = to;
        this.weight = weight;
    }

    public Integer getFrom() {
        return this.from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return this.to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    public Float getWeight() {
        return this.weight;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }

}
