package org.zhenchao.zelus.common.pojo;

/**
 * 事件抽象类
 *
 * @author ZhenchaoWang 2015-10-28 10:49:56
 */
public abstract class Event {

    /**
     * 事件类型判定
     *
     * @return 事件类型
     */
    public abstract EventType eventType();

    /**
     * 返回事件的精简形式
     *
     * @return 事件的精简形式
     */
    public abstract String toShortString();

}
