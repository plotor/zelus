/**
 * This file is part of the Java Machine Learning Library
 *
 * The Java Machine Learning Library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * The Java Machine Learning Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Java Machine Learning Library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Copyright (c) 2006-2012, Thomas Abeel
 *
 * Project: http://java-ml.sourceforge.net/
 */

package net.sf.javaml.clustering.mcl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * SparseVector represents a sparse vector.
 * <p>
 * Conventions: except for the inherited methods and normalise(double),
 * operations leave <tt>this</tt> ummodified (immutable) if there is a return
 * value. Within operations, no pruning of values close to zero is done. Pruning
 * can be controlled via the prune() method.
 *
 * @author gregor :: arbylon . net
 */
public class SparseVector extends HashMap<Integer, Double> {

    /**
     *
     */
    private static final long serialVersionUID = 8101876335024188425L;
    private int length = 0;

    /**
     * create empty vector
     */
    public SparseVector() {
        super();
    }

    /**
     * create empty vector with length
     */
    public SparseVector(int i) {
        this();
        length = i;
    }

    /**
     * create vector from dense vector
     *
     * @param x
     */
    public SparseVector(double[] x) {
        this(x.length);
        for (int i = 0; i < x.length; i++) {
            if (x[i] != 0) {
                this.put(i, x[i]);
            }
        }
    }

    /**
     * copy constructor
     *
     * @param v
     */
    public SparseVector(SparseVector v) {
        super(v);
        this.length = v.length;
    }

    /**
     * get ensures it returns 0 for empty hash values or if index exceeds
     * length.
     *
     * @param key
     * @return val
     */
    @Override
    public Double get(Object key) {
        Double b = super.get(key);
        if (b == null) {
            return 0.;
        }
        return b;
    }

    /**
     * put increases the matrix size if the index exceeds the current size.
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public Double put(Integer key, Double value) {
        length = Math.max(length, key + 1);
        if (value == 0) {
            return this.remove(key);
        }
        return super.put(key, value);
    }

    /**
     * normalises the vector to 1.
     */
    public void normalise() {
        double invsum = 1. / this.sum();
        for (int i : this.keySet()) {
            this.mult(i, invsum);
        }
    }

    /**
     * normalises the vector to newsum
     *
     * @param the value to which the element sum
     * @return the old element sum
     */
    public double normalise(double newsum) {
        double sum = this.sum();
        double invsum = newsum / sum;
        Set<Integer> keys = new HashSet<Integer>();
        keys.addAll(this.keySet());
        for (int i : keys) {
            this.mult(i, invsum);
        }
        return sum;
    }

    /**
     * sum of the elements
     *
     * @return
     */
    private double sum() {
        double sum = 0;
        for (double a : this.values()) {
            sum += a;
        }
        return sum;
    }

    /**
     * power sum of the elements
     *
     * @return
     */
    public double sum(double s) {
        double sum = 0;
        for (double a : this.values()) {
            sum += Math.pow(a, s);
        }
        return sum;
    }

    /**
     * mutable add
     *
     * @param v
     */
    public void add(SparseVector v) {
        for (int i : this.keySet()) {
            this.add(i, v.get(i));
        }
    }

    /**
     * mutable mult
     *
     * @param i index
     * @param a value
     */
    public void mult(int i, double a) {
        Double c = this.get(i);
        c *= a;
        this.put(i, c);
    }

    /**
     * mutable factorisation
     *
     * @param a
     */
    public void factor(double a) {
        SparseVector s = this.copy();
        for (int i : this.keySet()) {
            s.mult(i, a);
        }
    }

    /**
     * immutable scalar product
     *
     * @param v
     * @return scalar product
     */
    public double times(SparseVector v) {
        double sum = 0;
        for (int i : this.keySet()) {
            sum += this.get(i) * v.get(i);
        }
        return sum;
    }

    /**
     * mutable Hadamard product (elementwise multiplication)
     *
     * @param v
     */
    public void hadamardProduct(SparseVector v) {
        for (int i : this.keySet()) {
            this.put(i, v.get(i) * this.get(i));
        }
    }

    /**
     * mutable Hadamard power
     *
     * @param s
     */
    public void hadamardPower(double s) {
        Set<Integer> keys = new HashSet<Integer>();
        keys.addAll(this.keySet());
        for (int i : keys) {
            this.put(i, Math.pow(this.get(i), s));
        }
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void round(int decimalPlaces) {
        Set<Integer> keys = new HashSet<Integer>();
        keys.addAll(this.keySet());
        for (int i : keys) {
            this.put(i, round(this.get(i), decimalPlaces));
        }
    }

    /**
     * mutable add
     *
     * @param i
     * @param a
     */
    public void add(int i, double a) {
        length = Math.max(length, i + 1);
        double c = this.get(i);
        c += a;
        this.put(i, c);
    }

    /**
     * get the length of the vector
     *
     * @return
     */
    public final int getLength() {
        return length;
    }

    /**
     * set the new length of the vector (regardless of the maximum index).
     *
     * @param length
     */
    public final void setLength(int length) {
        this.length = length;
    }

    /**
     * copy the contents of the sparse vector
     *
     * @return
     */
    public SparseVector copy() {
        return new SparseVector(this);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i : this.keySet()) {
            sb.append(i).append("->").append(this.get(i)).append(", ");
        }
        return sb.toString();
    }

    /**
     * create string representation of dense equivalent.
     *
     * @return
     */
    public String toStringDense() {
        return Vectors.print(this.getDense());
    }

    /**
     * get dense represenation
     *
     * @return
     */
    public double[] getDense() {
        double[] a = new double[length];
        for (int i : this.keySet()) {
            a[i] = this.get(i);
        }
        return a;
    }

    /**
     * maximum element value
     *
     * @return
     */
    public double max() {
        double max = 0;
        for (int i : this.keySet()) {
            max = Math.max(this.get(i), max);
        }
        return max;
    }

    /**
     * exponential sum, i.e., sum (elements^p)
     *
     * @param p
     * @return
     */
    public double expSum(int p) {
        double sum = 0;
        for (double a : this.values()) {
            sum += Math.pow(a, p);
        }
        return sum;
    }

    /**
     * remove all elements whose magnitude is < threshold
     *
     * @param threshold
     */
    public void prune(double threshold) {
        for (Iterator<Integer> it = this.keySet().iterator(); it.hasNext(); ) {
            int key = it.next();
            if (Math.abs(this.get(key)) < threshold) {
                it.remove();
            }
        }
    }
}
