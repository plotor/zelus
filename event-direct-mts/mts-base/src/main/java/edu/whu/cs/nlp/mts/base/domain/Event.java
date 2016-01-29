package edu.whu.cs.nlp.mts.base.domain;

import edu.whu.cs.nlp.mts.base.global.GlobalConstant;

/**
 * 事件抽象类
 *
 * @author ZhenchaoWang 2015-10-28 10:49:56
 *
 */
public abstract class Event implements GlobalConstant {

    /**
     * 事件类型判定
     *
     * @return
     */
    public abstract EventType eventType();

    /**
     * 返回事件的精简形式
     *
     * @return
     */
    public abstract String toShortString();

}
