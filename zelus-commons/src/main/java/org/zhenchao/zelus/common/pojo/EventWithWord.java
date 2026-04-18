package org.zhenchao.zelus.common.pojo;

import org.zhenchao.zelus.common.Constants;

import java.io.Serializable;

/**
 * 原子事件
 *
 * @author Apache_xiaochao
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
     * 事件类型：3表示三元组事件，2表示主-谓事件，1表示谓-宾事件，-1表示异常事件
     *
     * @return 事件类型
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
     * 返回事件的简要形式
     *
     * @return 事件的简要形式
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
     * 返回事件的详细形式
     *
     * @return 事件的详细形式
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
