package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.twak.utils.Pair;
import org.twak.utils.collections.ConsecutiveItPairsGap;
import org.twak.utils.collections.ConsecutivePairs;

public class RangeMerge<T> {

	private double holeTol, islandTol;
	private TreeSet<E> vals = new TreeSet<>();
	private TreeSet<ET> things = new TreeSet<>();
	
	private static class E<T> implements Comparable<E> {
		double val;
		boolean start;
//		T thing;
		
		public E(double val, boolean start) {
			this.val = val;
			this.start = start;
//			this.thing = thing;
		}
		
		@Override
		public boolean equals(Object obj) {
			E b = (E) obj;
			return b.val == val;
		}
		
		@Override
		public int compareTo(E o) {
			return Double.compare(val, o.val);
		}
	}
	
	private static class ET<T> implements Comparable<ET> {
		double val;
		T thing;

		public ET(double val, T thing) {
			this.val = val;
			this.thing = thing;
		}

		@Override
		public boolean equals(Object obj) {
			ET b = (ET) obj;
			return b.val == val && b.thing == thing;
		}

		@Override
		public int compareTo(ET o) {
			return Double.compare(val, o.val);
		}
	}

	public RangeMerge(double holeTol, double islandTol) {
		this.holeTol = holeTol;
		this.islandTol = islandTol;
	}
	
	public void add(double start, double end, T thing) {

		if (start == end)
			return;
		
		if (end < start) {
			double tmp = start;
			start = end;
			end = tmp;
		}
		
		if (thing != null)
			things.add (new ET ( ( start + end ) / 2, thing));

//		System.out.println("adding range " + start + " to " + end);

		E s = new E(start, true), e = new E(end, false);

		E 
			before = vals.floor  (s),
			after  = vals.ceiling(e);

		if (before == null && after == null) {
			vals.clear();
			vals.add(s);
			vals.add(e);
		} else {
			// delete from before to after
			Iterator<E> ib = vals.tailSet(s, false).iterator();

			while (ib.hasNext()) {

				E b = ib.next();

				if (after != null && b.equals(after))
					break;

				ib.remove();
			}

			if (before == null)
				vals.add(s);
			else if (!before.start) {
				// add start
				if (!s.equals(before))
					vals.add(s);
				else
					vals.remove(before);
			}

			if (after == null)
				vals.add(e);
			else if (after.start) {
				// add end
				if (!e.equals(after))
					vals.add(e);
				else
					vals.remove(after);

			}
		}

//		for (E f : vals)
//			System.out.println((f.start ? "start" : "end") + " at " + f.val);

	}

	public void remove(double start, double end) {
		// TODO Auto-generated method stub
		
	}

	
	public List<Double> get() {

		List<Double> out = new ArrayList();

		for (E e : vals)
			out.add(e.val);

		boolean merged;
		if (holeTol> 0) {
			
			do { // remove out-ins
				merged = false;
				
				for (int i = 1; i < out.size() - 2; i += 2) {
					if (Math.abs(out.get(i) - out.get(i + 1)) < holeTol) {
						out.remove(i);
						out.remove(i);
						merged = true;
						i-=2;
					}
				}
			} while (merged);
		}
		
		if (islandTol > 0 ) {
		
			do { // remove in-outs
				merged = false;
				for (int i = 0; i < out.size() - 1; i += 2) {
					if (Math.abs(out.get(i) - out.get(i + 1)) < islandTol) {
						out.remove(i);
						out.remove(i);
						merged = true;
						i-=2;
					}
				}
			} while (merged);
		}
		
		return out;
	}

	public static class Result<T> {
		
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		Set<T> things = new HashSet<>();
		
		public Result(){}
		
		public Result(double min, double max) {
			this.min = min;
			this.max = max;
		}
	}
	
	public List<Result<T>> getResults() {

		List<Result<T>> out = new ArrayList();
		
		int seen = 0;
		
		for (Pair<Double, Double> p : new ConsecutiveItPairsGap<>(get())) {
			
			Result r = new Result ( p.first(), p.second());
			out.add ( r );
			
			for (ET e : things.subSet(new ET(r.min, null), new ET(r.max, null)) ) {
				r.things.add(e.thing);
				seen++;
			}
		}
		
		return out;
	}
	
	public Result getLongest() {
		
		List<Double> l = get();
		
		double longest = -Double.MAX_VALUE;
		Pair<Double,Double> longestP = null;
		
		for (Pair<Double, Double> p : new ConsecutiveItPairsGap<>(l)) {
			double length = p.second() - p.first();
			if (length > longest) {
				longest = length;
				longestP = p;
			}
		}
		
		if (longestP == null)
			return new Result<>();
		
		Result out = new Result ( longestP.first(), longestP.second());
		
		for (ET e : things.subSet(new ET(out.min, null), new ET(out.max, null)) )
			out.things.add(e.thing);
		
		return out;
		
	}
}
