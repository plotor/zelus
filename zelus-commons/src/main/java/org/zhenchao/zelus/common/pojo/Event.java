package org.zhenchao.zelus.common.pojo;

/**
 * Abstract event class
 *
 * @author zhenchao 2015-10-28 10:49:56
 */
public abstract class Event {

    /**
     * Determine event type
     *
     * @return event type
     */
    public abstract EventType eventType();

    /**
     * Return the compact form of the event
     *
     * @return compact form of the event
     */
    public abstract String toShortString();

}
