package org.zhenchao.zelus.common.pojo;

import java.io.Serializable;

/**
 * 原子事件
 *
 * @author Apache_xiaochao
 */
public class EventWithWord extends Event implements Serializable {

    private static final long serialVersionUID = 5304005154459141241L;

    private Word leftWord;   // 使动词
    private Word negWord;    // 否定词
    private Word middleWord; // 连接词
    private Word rightWord;  // 被动词
    private String filename;   // 事件所属的文件名称

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
     * @return
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
     * @return
     */
    @Override
    public String toShortString() {
        return (this.leftWord == null ? "" : this.leftWord.getLemma()) + WORD_CONNECTOR_IN_EVENTS
                + (this.negWord == null ? "" : (this.negWord.getLemma() + " "))
                + (this.middleWord == null ? "" : this.middleWord.getLemma()) + WORD_CONNECTOR_IN_EVENTS
                + (this.rightWord == null ? "" : this.rightWord.getLemma());
    }

    /**
     * 返回事件的详细形式
     */
    @Override
    public String toString() {
        return (this.leftWord == null ? "" : this.leftWord) + WORD_CONNECTOR_IN_EVENTS
                + (this.middleWord == null ? "" : (this.middleWord + " "))
                + (this.middleWord == null ? "" : this.middleWord)
                + WORD_CONNECTOR_IN_EVENTS + (this.rightWord == null ? "" : this.rightWord)
                + FILENAME_REST_LEFT + this.filename + FILENAME_REST_RIGHT;
    }

}
