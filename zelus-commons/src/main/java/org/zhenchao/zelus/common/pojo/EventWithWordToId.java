package org.zhenchao.zelus.common.pojo;

/**
 * 封装事件及其对应的编号
 *
 * @author Apache_xiaochao
 */
public class EventWithWordToId {

    private EventWithWord event;

    private Integer num;

    public EventWithWordToId() {
        super();
    }

    public EventWithWordToId(EventWithWord event, Integer num) {
        super();
        this.event = event;
        this.num = num;
    }

    public EventWithWord getEvent() {
        return this.event;
    }

    public Integer getNum() {
        return this.num;
    }

    public void setEvent(EventWithWord event) {
        this.event = event;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

}
