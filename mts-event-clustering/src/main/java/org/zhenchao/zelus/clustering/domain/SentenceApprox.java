package org.zhenchao.zelus.clustering.domain;

import java.io.Serializable;

/**
 * 句子之间的相似度
 *
 * @author ZhenchaoWang 2015-11-29 19:45:19
 */
public class SentenceApprox implements Serializable {

    private static final long serialVersionUID = 2616505097603370716L;

    private Integer from;

    private Integer to;

    private Double approx;

    public SentenceApprox() {
        super();
    }

    public SentenceApprox(Integer from, Integer to, Double approx) {
        super();
        this.from = from;
        this.to = to;
        this.approx = approx;
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

    public Double getApprox() {
        return this.approx;
    }

    public void setApprox(Double approx) {
        this.approx = approx;
    }

}
