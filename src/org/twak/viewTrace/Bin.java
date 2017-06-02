package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.twak.utils.MUtils;
import org.twak.utils.collections.Arrayz;

public class Bin<E> {

	double[] weights;
	Set<E>[] es;
	double min, max;
	boolean wrap;
	
	public Bin(double min, double max, int bins, boolean wrap) {
		this.min = min;
		this.max = max;
		this.wrap = wrap;
		weights = new double[bins];
	}

	
	public void add(double val, double weight) {
		add (val, weight, null);
	}
	
	public void add(double val, double weight, E thing) {
		
		if (weight == 0)
			return;
		
		if (thing != null && es == null)
			es = new HashSet[weights.length];
		
		int i = getI( val );
		
		if (thing != null ) {
			Set<E> se = es[i];
			if (se == null)
				se = es[i] = new HashSet<>();
			se.add(thing);
		}
		
		weights[i] += weight;
		
	}

	public double getWeight ( double val ) {
		return weights[getI(val)];
	}
	
	public int getI( double val ) {
		return Math.min (weights.length-1, (int)Math.floor ( (weights.length * ( val - min ) / ( max - min ) ) ) );
	}
	
	public void addRange (double valStart, double valEnd, double weight, E thing ) {
		if (weight == 0)
			return;
		
		if (thing != null && es == null)
			es = new HashSet[weights.length];
		
		valStart = MUtils.clamp( valStart, min, max );
		valEnd   = MUtils.clamp( valEnd  , min, max );
		
		for (int i = getI(valStart); i < getI(valEnd); i++ ) {
			
			weights[i] += weight;
			
			Set<E> se = es[i];
			if (se == null)
				se = es[i] = new HashSet<>();
			se.add(thing);
		}
		
	}
	
	public int maxI() {
		return Arrayz.max(weights);
	}
	
	public Set<E> getThings (int i ) {
		Set<E> se = es[i];
		if (se == null)
			return Collections.emptySet();
		else return se;
	}
	
	/**
	 * Within +- range of i
	 */
	public Set<E> getThings (int i, double range) {

		int spread = (int) ( range * weights.length / (max - min) );
		spread = MUtils.clamp (spread, 0, weights.length);
		
		return getThings(i, new int[]{ spread, spread});
		
//		Set<E> out = new HashSet<>();
//		
//		for (int j = i - spread; j < i + spread; j++)
//		{
//			Set<E> se = es[ (j+vals.length)%vals.length];
//			if (se != null)
//				out.addAll(se);
//		}
//		return out;
	}

	
	public Set<E> getThings(int i, int[] range) {

		if (es == null)
			return Collections.emptySet();
		
		Set<E> out = new HashSet<>();
		
		int min = i - range[0], max = i+range[1];
		
		if (!wrap) {
			
			min = Math.max(0,min);
			max = Math.min(weights.length-1, max);
		}
		
		for (int j = min; j <= max; j++) {
			
			Set<E> se = es[ (j+weights.length)%weights.length];
			if (se != null)
				out.addAll(se);
		}
		return out;
		
	}
	
	public int[] maxMode(int allowableBad, int max) {
		
		int center = maxI();
		int[] out = new int[] { center, center };
		double p = weights[center];
		double thresh = 0.01;
		
		int bads = allowableBad;
		
		for (int i = 1; i < weights.length; i++) {
			double n = weights[ (center + i) % weights.length];
			if ( n < thresh * weights[center] || n > p) {
				if (bads > 0)
					bads --;
				else {
					out[1] = i;
					break;
				}
			}
		}
		
		bads = allowableBad;
		p = weights[center];
		for (int i = 1; i < weights.length; i++) {
			double n = weights[ (center - i + weights.length) % weights.length];
			if ( n < thresh * weights[center] || n > p) {
				if (bads > 0)
					bads --;
				else {
					out[0] = i;
					break;
				}
			}
		}
		
		out[0] = Math.min(max, out[0] );
		out[1] = Math.min(max, out[1] );
		
		return out;
	}
	
	
	public double val(int i) {
		return ( ( i + 0.5) / (double) weights.length) * (max - min) + min;
	}

	
	public double maxVal() {
		return val(maxI());
	}
	
	public static class Binned<E> {
		public Binned(double val, double weight, E thing) {
			this.val = val;
			this.weight = weight;
			this.thing = thing;
		}
		double val, weight;
		E thing;

	}
	
	public static class Builder<E> {

		Set<Binned<E>> things = new HashSet<>();
		
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		
		public void add(double val, double weight, E thing) {
			
			things.add(new Binned(val, weight, thing ));
			min = Math.min(min, val);
			max = Math.max(max, val);
		}
		
		public Bin<E> done(int bins, boolean wrap) {
			Bin<E> b = new Bin(min, max, bins, wrap);
			for (Binned<E> pair : things)
				b.add(pair.val, pair.weight, pair.thing);
			return b;
		}
	}

	public void add (Bin toAdd) {
		for (int i = 0; i < weights.length; i++) {
			weights[i] += toAdd.weights[i];
			if (toAdd.es[i] != null) {
				if (toAdd.es[i] == null)
					es[i] = new HashSet();
				es[i].addAll( toAdd.es[i] );
			}
		}
	}

	public void multiply(double d) {
		for (int i = 0; i < weights.length; i++)
			weights[i] *= d;
	}


	public void multiply(Bin m, double c) {
		for (int i = 0; i < weights.length; i++)
			weights[i] = weights[i] * c + weights[i] * m.weights[i];
	}

	public List<Double> getRegionAbove (double m) {

		List<Double> out = new ArrayList<>();
		
		for (int i = 0; i < weights.length; i++) {
			if ( (i == 0 || weights[i-1] <=m ) && weights[i] > m )
				out.add(val(i));
			if ( i != 0 && (i == weights.length-1 || weights[i-1] >=m ) && weights[i] < m )
				out.add(val(i));
		}
		
		return out;
	}

	public double getFirst( double m ) {
		for (int i = 0; i < weights.length; i++)
			if (weights[i] > m )
				return val ( i );
		return -Double.NaN;
	}


	public double getLast( double m ) {
		for (int i = weights.length-1; i >= 0; i--)
			if (weights[i] > m )
				return val ( i );
		return -Double.NaN;
	}
}
