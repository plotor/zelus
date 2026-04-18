package org.zhenchao.zelus.common.pojo;

/**
 * Dependency parsing原子Result
 *
 * @author zhenchao
 */
public class ParseItem {

    private String dependencyType; // 依存Class型
    private Word leftWord;       // 左边的Word
    private Word rightWord;      // 右边的Word

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
