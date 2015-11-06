package edu.whu.cs.nlp.mts.base.domain;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

/**
 * 原子事件<br>
 * 2.0版本中将主谓宾由单词扩充成短语
 *
 * @author ZhenchaoWang 2015-10-27 11:27:26
 * @version 2.0
 *
 */
public class EventWithPhrase extends Event implements Serializable{

    private static final long serialVersionUID = -7370833867494031137L;

    private final List<Word> leftPhrases;   // 主语
    private final List<Word> middlePhrases; // 谓语
    private final List<Word> rightPhrases;  // 宾语
    private final EventType  eventType;     // 事件类型
    private String     filename;      // 事件所属的文件名称

    public EventWithPhrase(List<Word> leftPhrases, List<Word> middlePhrases, List<Word> rightPhrases, String filename) {
        super();
        this.leftPhrases = leftPhrases;
        this.middlePhrases = middlePhrases;
        this.rightPhrases = rightPhrases;
        this.eventType = this.eventType();
        this.filename = filename;
    }

    /**
     * 事件类型：3表示三元组事件，2表示主-谓事件，1表示谓-宾事件，-1表示异常事件
     *
     * @return
     */
    @Override
    public EventType eventType() {
        if (CollectionUtils.isNotEmpty(this.leftPhrases)
                && CollectionUtils.isNotEmpty(this.middlePhrases)
                && CollectionUtils.isNotEmpty(this.rightPhrases)) {
            // 三元事件
            return EventType.TERNARY;

        } else if (CollectionUtils.isNotEmpty(this.leftPhrases)
                && CollectionUtils.isNotEmpty(this.middlePhrases)) {
            // 宾语缺失
            return EventType.RIGHT_MISSING;

        } else if (CollectionUtils.isNotEmpty(this.middlePhrases)
                && CollectionUtils.isNotEmpty(this.rightPhrases)) {
            // 主语缺失
            return EventType.LEFT_MISSING;

        }
        // 不是事件
        return EventType.ERROR;
    }

    /**
     * 判断当前事件是不是回文事件，即主语和宾语相同
     *
     * @return
     */
    public boolean isPalindromicEvent() {
        boolean isPalindromic = false;
        if(this.leftPhrases.size() == this.rightPhrases.size()) {
            isPalindromic = true;
            for(int i = 0; i < this.leftPhrases.size(); i++) {
                if(!this.leftPhrases.get(i).equals(this.rightPhrases.get(i))) {
                    isPalindromic = false;
                    break;
                }
            }
        }
        return isPalindromic;
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<Word> getLeftPhrases() {
        return this.leftPhrases;
    }

    public List<Word> getMiddlePhrases() {
        return this.middlePhrases;
    }

    public List<Word> getRightPhrases() {
        return this.rightPhrases;
    }

    public EventType getEventType() {
        return this.eventType;
    }

    /**
     * 返回事件的简要形式
     *
     * @return
     */
    @Override
    public String toShortString() {
        StringBuilder result = new StringBuilder();
        final String SPLITER = "_";
        StringBuilder sb_left = new StringBuilder();
        if(CollectionUtils.isNotEmpty(this.leftPhrases)) {
            for (Word word : this.leftPhrases) {
                sb_left.append(word.getLemma() + SPLITER);
            }
            result.append(sb_left.substring(0, sb_left.lastIndexOf(SPLITER)));
        }
        result.append(WORD_CONNECTOR_IN_EVENTS);
        StringBuilder sb_middle = new StringBuilder();
        if(CollectionUtils.isNotEmpty(this.middlePhrases)) {
            for (Word word : this.middlePhrases) {
                sb_middle.append(word.getLemma() + SPLITER);
            }
            result.append(sb_middle.substring(0, sb_middle.lastIndexOf(SPLITER)));
        }
        result.append(WORD_CONNECTOR_IN_EVENTS);
        StringBuilder sb_right = new StringBuilder();
        if(CollectionUtils.isNotEmpty(this.rightPhrases)) {
            for (Word word : this.rightPhrases) {
                sb_right.append(word.getLemma() + SPLITER);
            }
            result.append(sb_right.substring(0, sb_right.lastIndexOf(SPLITER)));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return  (CollectionUtils.isEmpty(this.leftPhrases) ? "" : this.leftPhrases.toString()) + WORD_CONNECTOR_IN_EVENTS
                + (CollectionUtils.isEmpty(this.middlePhrases) ? "" : this.middlePhrases.toString()) + WORD_CONNECTOR_IN_EVENTS
                + (CollectionUtils.isEmpty(this.rightPhrases) ? "" : this.rightPhrases.toString()) + FILENAME_REST_LEFT + this.filename + FILENAME_REST_RIGHT;
    }

}
