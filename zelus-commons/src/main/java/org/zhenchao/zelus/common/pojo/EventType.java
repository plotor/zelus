package org.zhenchao.zelus.common.pojo;

/**
 * Event type enum
 *
 * @author zhenchao 2015-10-27 11:31:59
 */
public enum EventType {
    /**
     * Ternary event
     */
    TERNARY,
    /**
     * Binary event: missing subject
     */
    LEFT_MISSING,
    /**
     * Binary event: missing object
     */
    RIGHT_MISSING,
    /**
     * Not an event
     */
    ERROR;
}
