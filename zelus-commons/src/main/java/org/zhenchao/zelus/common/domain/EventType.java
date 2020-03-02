package org.zhenchao.zelus.common.domain;

/**
 * 事件所属类型
 *
 * @author ZhenchaoWang 2015-10-27 11:31:59
 */
public enum EventType {
    /**
     * 三元事件
     */
    TERNARY,
    /**
     * 二元事件：主语缺失
     */
    LEFT_MISSING,
    /**
     * 二元事件：宾语缺失
     */
    RIGHT_MISSING,
    /**
     * 不是事件
     */
    ERROR;
}
