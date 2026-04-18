package org.zhenchao.zelus.common.pojo;

import org.zhenchao.zelus.common.Constants;

import java.io.Serializable;

/**
 * Atomic event
 *
 * @author zhenchao
 */
@SuppressWarnings("checkstyle:ReturnCount")
public class EventWithWord extends Event implements Serializable {

    private static final long serialVersionUID = 5304005154459141241L;

    private Word leftWord;
    private Word negWord;
    private Word middleWord;
    private Word rightWord;
    private String filename;

    public EventWithWord(Word leftWord, Word negWord, Word middleWord, Word rightWord, String filename) {
        super();
        this.leftWord = leftWord;
        this.negWord = negWord;
        this.middleWord = middleWord;
        this.rightWord = rightWord;
        this.filename = filename;
    }

    /**
     * Event type: 3=ternary, 2=subject-verb, 1=verb-object, -1=abnormal
     *
     * @return event type
     */
    @Override
    public EventType eventType() {

        if (this.leftWord != null && this.middleWord != null && this.rightWord != null) {

            return EventType.TERNARY;

        } else if (this.leftWord != null && this.middleWord != null) {

            return EventType.RIGHT_MISSING;

        } else if (this.middleWord != null && this.rightWord != null) {

            return EventType.LEFT_MISSING;

        }

        return EventType.ERROR;

    }

    public Word getLeftWord() {
        return this.leftWord;
    }

    public void setLeftWord(Word leftWord) {
        this.leftWord = leftWord;
    }

    public Word getNegWord() {
        return this.negWord;
    }

    public void setNegWord(Word negWord) {
        this.negWord = negWord;
    }

    public Word getMiddleWord() {
        return this.middleWord;
    }

    public void setMiddleWord(Word middleWord) {
        this.middleWord = middleWord;
    }

    public Word getRightWord() {
        return this.rightWord;
    }

    public void setRightWord(Word rightWord) {
        this.rightWord = rightWord;
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Return the brief form of the event
     *
     * @return brief form of the event
     */
    @Override
    public String toShortString() {
        return (this.leftWord == null ? "" : this.leftWord.getLemma())
                + Constants.WORD_CONNECTOR_IN_EVENTS
                + (this.negWord == null ? "" : (this.negWord.getLemma() + " "))
                + (this.middleWord == null ? "" : this.middleWord.getLemma())
                + Constants.WORD_CONNECTOR_IN_EVENTS
                + (this.rightWord == null ? "" : this.rightWord.getLemma());
    }

    /**
     * Return the detailed form of the event
     *
     * @return detailed form of the event
     */
    @Override
    public String toString() {
        return (this.leftWord == null ? "" : this.leftWord)
                + Constants.WORD_CONNECTOR_IN_EVENTS
                + (this.middleWord == null ? "" : (this.middleWord + " "))
                + (this.middleWord == null ? "" : this.middleWord)
                + Constants.WORD_CONNECTOR_IN_EVENTS
                + (this.rightWord == null ? "" : this.rightWord)
                + Constants.FILENAME_REST_LEFT + this.filename
                + Constants.FILENAME_REST_RIGHT;
    }

}
