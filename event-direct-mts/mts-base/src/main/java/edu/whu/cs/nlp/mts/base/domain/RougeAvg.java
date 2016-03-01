package edu.whu.cs.nlp.mts.base.domain;

public class RougeAvg {

    private String rougeType;
    private String avgType;
    private float  value;

    public RougeAvg() {
        super();
    }

    public String getAvgType() {
        return this.avgType;
    }

    public String getRougeType() {
        return this.rougeType;
    }

    public float getValue() {
        return this.value;
    }

    public void setAvgType(String avgType) {
        this.avgType = avgType;
    }

    public void setRougeType(String rougeType) {
        this.rougeType = rougeType;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "RougeAvg [rougeType=" + this.rougeType + ", avgType=" + this.avgType + ", value=" + this.value + "]";
    }

}
