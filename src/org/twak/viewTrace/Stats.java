package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Several different dimensions of sets for a collection of objects.
 * Each entry of stats[] contains stats for a collection of different values (x, y, or width).
 * Each entry of stats list contains one collection of things we're measuring statas over.
 * 
 * @author twak
 *
 */
public class Stats {

	public List<Stat>[] stats;
	public Stat[] globals;
	
	public class Stat {
		
		public Object t;
		public double mean, deviation, total;
		int set;
		
		public Stat(List<Double> stream) {
			this (-1, null, stream);
		}
		public Stat(int set, Object t2, List<Double> vs) {
			this.set = set;
			this.t = t2;
			long count = vs.stream().count();
			total =  vs.stream().mapToDouble(x -> x).sum();
			mean = 	total / count;
			deviation = Math.sqrt( vs.stream().mapToDouble( x -> Math.pow(x - mean, 2) ).sum() / count);
		}
		
//		public Stat get(int i) {
//			return stats[i].get(set);
//		}
		
		public Stat getGlobal() {
			return globals[set];
		}
	}
	
	public interface Statable<T> {
		public double[] getVals(T t);
	}
	
	public Stats(List rs, Statable...filters) {
		
		stats = new List[filters.length];
		globals = new Stat[filters.length];
		
		for (int f = 0; f < filters.length; f++)
		{
			List<Double> all = new ArrayList();
			
			stats[f] = new ArrayList();
			for (int r = 0; r < rs.size(); r++ ) { 
				double[] vals = filters[f].getVals(rs.get(r));
				
				List<Double> dd = DoubleStream.of(vals).mapToObj(Double::valueOf).collect(Collectors.toList() );
				stats[f].add(new Stat(f, rs.get(r), dd) );
				
				for (double d : vals)
					all.add(d);
			}
			
			globals[f] = new Stat(all);
		}
		
		
	}
	
	public List<Stat> byMean(int i) {
		
		List<Stat> out = stats[i];
		
		Collections.sort(out, (Stat o1, Stat o2) -> Double.compare(o1.mean, o2.mean) );
		
		return Collections.unmodifiableList(out);
	}
	
	public List<Stat> byAbsMean(int i, double[] minMax) {
		
		List<Stat> out = stats[i];
		minMax[0] =  Double.MAX_VALUE;
		minMax[1] = -Double.MAX_VALUE;
		
		Collections.sort(out, (Stat o1, Stat o2) -> Double.compare(Math.abs ( o1.mean ), Math.abs ( o2.mean) ) );
		
		for (Stat s : out) {
			minMax[0] = Math.min ( minMax[0], Math.abs ( s.mean ) );
			minMax[1] = Math.max ( minMax[1], Math.abs ( s.mean ) );
		}
		
		return Collections.unmodifiableList(out);
	}
	
	public List<Stat> byDeviation(int i, double[] minMax) {
		
		List<Stat> out = stats[i];
		
		minMax[0] =  Double.MAX_VALUE;
		minMax[1] = -Double.MAX_VALUE;
		
		Collections.sort(out, (Stat o1, Stat o2) -> Double.compare( o1.deviation, o2.deviation ) );
		
		for (Stat s : out) {
			minMax[0] = Math.min ( minMax[0], s.deviation );
			minMax[1] = Math.max ( minMax[1], s.deviation );
		}
		
		return Collections.unmodifiableList(out);
	}

}
