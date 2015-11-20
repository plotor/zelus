package edu.whu.cs.nlp.msc.giga;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Ngram model item
 *
 * @author ZhenchaoWang 2015-11-20 14:52:44
 *
 */
public class NGramScore implements Serializable {

    private static final long serialVersionUID = -2714006471175249369L;

    private float                     prob;
    private float                     backOffProb;

    public NGramScore(float prob, float backOffProb) {
        this.prob = prob;
        this.backOffProb = backOffProb;
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {

        stream.defaultWriteObject();
        stream.writeObject(this.prob);
        stream.writeObject(this.backOffProb);

    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {

        stream.defaultReadObject();
        this.prob = (float) stream.readObject();
        this.backOffProb = (float) stream.readObject();

    }

    public float getProb() {
        return this.prob;
    }

    public float getBackOffProb() {
        return this.backOffProb;
    }
}
