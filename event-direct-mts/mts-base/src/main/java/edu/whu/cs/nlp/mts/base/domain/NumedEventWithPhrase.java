package edu.whu.cs.nlp.mts.base.domain;

/**
 * 封装已编号的事件，及其向量
 *
 * @author ZhenchaoWang 2015-11-4 13:16:50
 *
 */
public class NumedEventWithPhrase {

    private Integer         num;

    private EventWithPhrase event;

    private Double[]        vec;

    public NumedEventWithPhrase() {
        super();
    }

    public NumedEventWithPhrase(Integer num, EventWithPhrase event, Double[] vec) {
        super();
        this.num = num;
        this.event = event;
        this.vec = vec;
    }

    public Integer getNum() {
        return this.num;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

    public EventWithPhrase getEvent() {
        return this.event;
    }

    public void setEvent(EventWithPhrase event) {
        this.event = event;
    }

    public Double[] getVec() {
        return this.vec;
    }

    public void setVec(Double[] vec) {
        this.vec = vec;
    }

}
