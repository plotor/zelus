package org.zhenchao.zelus.common.domain;

/**
 * 依存分析原子结果
 *
 * @author Apache_xiaochao
 */
public class ParseItem {

    private String dependencyType; // 依存类型
    private Word leftWord;       // 左边的词
    private Word rightWord;      // 右边的词

    public ParseItem() {
        super();
    }

    public String getDependencyType() {
        return this.dependencyType;
    }

    public Word getLeftWord() {
        return this.leftWord;
    }

    public Word getRightWord() {
        return this.rightWord;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }

    public void setLeftWord(Word leftWord) {
        this.leftWord = leftWord;
    }

    public void setRightWord(Word rightWord) {
        this.rightWord = rightWord;
    }

    public String toShortString() {
        return this.dependencyType + "(" + this.leftWord.toShortString() + ", " + this.rightWord.toShortString() + ")";
    }

    @Override
    public String toString() {
        return this.dependencyType + "(" + this.leftWord + ", " + this.rightWord + ")";
    }

}
