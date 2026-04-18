package org.zhenchao.zelus.common.pojo;

import org.zhenchao.zelus.common.Constants;

import java.io.Serializable;

/**
 * 单词
 *
 * @author Apache_xiaochao
 */
public class Word implements Serializable, Cloneable {

    private static final long serialVersionUID = -4747705721887890597L;

    private String name;
    private String pos;
    private String lemma;
    private String ner;
    private int sentenceNum;
    private int numInLine;

    public Word() {
        super();
    }

    public Word(String name, String pos, String lemma, String ner, int sentenceNum, int numInLine) {
        super();
        this.name = name;
        this.pos = pos;
        this.lemma = lemma;
        this.ner = ner;
        this.sentenceNum = sentenceNum;
        this.numInLine = numInLine;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * 词向量字典的key
     *
     * @return 字典key
     */
    public String dictKey() {

        return this.name + "-" + this.pos;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.lemma == null) ? 0 : this.lemma.hashCode());
        result = prime * result + ((this.name == null) ? 0 : this.name.hashCode());
        result = prime * result + ((this.ner == null) ? 0 : this.ner.hashCode());
        result = prime * result + this.numInLine;
        result = prime * result + ((this.pos == null) ? 0 : this.pos.hashCode());
        result = prime * result + this.sentenceNum;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        Word other = (Word) obj;
        if (this.lemma == null) {
            if (other.lemma != null) {
                return false;
            }
        } else if (!this.lemma.equals(other.lemma)) {
            return false;
        }
        if (this.name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!this.name.equals(other.name)) {
            return false;
        }
        if (this.ner == null) {
            if (other.ner != null) {
                return false;
            }
        } else if (!this.ner.equals(other.ner)) {
            return false;
        }
        if (this.numInLine != other.numInLine) {
            return false;
        }
        if (this.pos == null) {
            if (other.pos != null) {
                return false;
            }
        } else if (!this.pos.equals(other.pos)) {
            return false;
        }
        if (this.sentenceNum != other.sentenceNum) {
            return false;
        }
        return true;
    }

    public String getLemma() {
        return this.lemma;
    }

    public String getName() {
        return this.name;
    }

    public String getNer() {
        return this.ner;
    }

    public int getNumInLine() {
        return this.numInLine;
    }

    public String getPos() {
        return this.pos;
    }

    public int getSentenceNum() {
        return this.sentenceNum;
    }

    public void setLemma(String lemma) {
        this.lemma = lemma;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNer(String ner) {
        this.ner = ner;
    }

    public void setNumInLine(int numInLine) {
        this.numInLine = numInLine;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public void setSentenceNum(int sentenceNum) {
        this.sentenceNum = sentenceNum;
    }

    /**
     * 返回简短字符串表示
     *
     * @return 简短字符串
     */
    public String toShortString() {
        return this.name + Constants.WORD_ATTRBUTE_CONNECTOR + this.numInLine;
    }

    @Override
    public String toString() {
        return this.name + Constants.WORD_ATTRBUTE_CONNECTOR
                + this.lemma + Constants.WORD_ATTRBUTE_CONNECTOR
                + this.pos + Constants.WORD_ATTRBUTE_CONNECTOR
                + this.ner + Constants.WORD_ATTRBUTE_CONNECTOR
                + this.sentenceNum + Constants.WORD_ATTRBUTE_CONNECTOR
                + this.numInLine;
    }

    /**
     * 仅打印词性
     *
     * @return 词/词性
     */
    public String wordWithPOS() {
        return this.name + "/" + this.pos;
    }

}
