package org.zhenchao.zelus.common.pojo;

import org.apache.commons.collections.CollectionUtils;
import org.zhenchao.zelus.common.Constants;

import java.io.Serializable;
import java.util.List;

/**
 * 原子事件
 * 2.0版本中将主谓宾由单词扩充成短语
 *
 * @author ZhenchaoWang 2015-10-27 11:27:26
 * @version 2.0
 */
@SuppressWarnings("checkstyle:ReturnCount")
public class EventWithPhrase extends Event implements Serializable {

    private static final long serialVersionUID = -7370833867494031137L;

    private List<Word> leftPhrases;
    private List<Word> middlePhrases;
    private List<Word> rightPhrases;
    private EventType eventType;
    private Integer sentNum;
    private String filename;

    public EventWithPhrase(List<Word> leftPhrases,
                           List<Word> middlePhrases,
                           List<Word> rightPhrases,
                           Integer sentNum, String filename) {
        super();
        this.leftPhrases = leftPhrases;
        this.middlePhrases = middlePhrases;
        this.rightPhrases = rightPhrases;
        this.eventType = this.eventType();
        this.sentNum = sentNum;
        this.filename = filename;
    }

    /**
     * 事件类型：3表示三元组事件，2表示主-谓事件，1表示谓-宾事件，-1表示异常事件
     *
     * @return 事件类型
     */
    @Override
    public EventType eventType() {
        if (CollectionUtils.isNotEmpty(this.leftPhrases)
                && CollectionUtils.isNotEmpty(this.middlePhrases)
                && CollectionUtils.isNotEmpty(this.rightPhrases)) {
            return EventType.TERNARY;

        } else if (CollectionUtils.isNotEmpty(this.leftPhrases)
                && CollectionUtils.isNotEmpty(this.middlePhrases)) {
            return EventType.RIGHT_MISSING;

        } else if (CollectionUtils.isNotEmpty(this.middlePhrases)
                && CollectionUtils.isNotEmpty(this.rightPhrases)) {
            return EventType.LEFT_MISSING;

        }
        return EventType.ERROR;
    }

    /**
     * 判断当前事件是不是回文事件，即主语和宾语相同
     *
     * @return 是否为回文事件
     */
    public boolean isPalindromicEvent() {
        boolean isPalindromic = false;
        if (this.leftPhrases.size() == this.rightPhrases.size()) {
            isPalindromic = true;
            for (int i = 0; i < this.leftPhrases.size(); i++) {
                if (!this.leftPhrases.get(i).equals(this.rightPhrases.get(i))) {
                    isPalindromic = false;
                    break;
                }
            }
        }
        return isPalindromic;
    }

    /**
     * 返回事件的简要形式
     *
     * @return 事件的简要形式
     */
    @Override
    public String toShortString() {
        StringBuilder result = new StringBuilder();
        final String spliter = "_";
        StringBuilder sbLeft = new StringBuilder();
        if (CollectionUtils.isNotEmpty(this.leftPhrases)) {
            for (Word word : this.leftPhrases) {
                sbLeft.append(word.getName() + spliter);
            }
            result.append(sbLeft.substring(0, sbLeft.lastIndexOf(spliter)));
        }
        result.append(Constants.WORD_CONNECTOR_IN_EVENTS);
        StringBuilder sbMiddle = new StringBuilder();
        if (CollectionUtils.isNotEmpty(this.middlePhrases)) {
            for (Word word : this.middlePhrases) {
                sbMiddle.append(word.getName() + spliter);
            }
            result.append(sbMiddle.substring(0, sbMiddle.lastIndexOf(spliter)));
        }
        result.append(Constants.WORD_CONNECTOR_IN_EVENTS);
        StringBuilder sbRight = new StringBuilder();
        if (CollectionUtils.isNotEmpty(this.rightPhrases)) {
            for (Word word : this.rightPhrases) {
                sbRight.append(word.getName() + spliter);
            }
            result.append(sbRight.substring(0, sbRight.lastIndexOf(spliter)));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return (CollectionUtils.isEmpty(this.leftPhrases) ? "" : this.leftPhrases.toString())
                + Constants.WORD_CONNECTOR_IN_EVENTS
                + (CollectionUtils.isEmpty(this.middlePhrases) ? "" : this.middlePhrases.toString())
                + Constants.WORD_CONNECTOR_IN_EVENTS
                + (CollectionUtils.isEmpty(this.rightPhrases) ? "" : this.rightPhrases.toString())
                + Constants.FILENAME_REST_LEFT + this.filename
                + Constants.FILENAME_REST_RIGHT;
    }

    public List<Word> getLeftPhrases() {
        return this.leftPhrases;
    }

    public void setLeftPhrases(List<Word> leftPhrases) {
        this.leftPhrases = leftPhrases;
    }

    public List<Word> getMiddlePhrases() {
        return this.middlePhrases;
    }

    public void setMiddlePhrases(List<Word> middlePhrases) {
        this.middlePhrases = middlePhrases;
    }

    public List<Word> getRightPhrases() {
        return this.rightPhrases;
    }

    public void setRightPhrases(List<Word> rightPhrases) {
        this.rightPhrases = rightPhrases;
    }

    public EventType getEventType() {
        return this.eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Integer getSentNum() {
        return this.sentNum;
    }

    public void setSentNum(Integer sentNum) {
        this.sentNum = sentNum;
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

}
