package org.twak.viewTrace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.twak.utils.collections.MultiMapSet;


public class Closer<E> {
	
	Map <E, Integer> map = new HashMap<>();
	MultiMapSet <Integer, E> rev = new MultiMapSet<>(); // could be a single map!
	int nextInt = 0;
	
	public void add(E...es) {
		
		Set<Integer> toMerge = new HashSet<>();
		Set<E> toMergeE = new HashSet<>();
		
		for (E e : es) {
			Integer s = map.get(e);
			if (s != null) {
				toMerge.add( s );
				toMergeE.addAll(rev.get(s));
			}
		}
			
		if (toMerge.isEmpty()) {
			for (E e : es) {
				map.put(e, nextInt);
				rev.put(nextInt, e);
			}
			nextInt++;
			return;
		}
		
		int set = toMerge.stream().min( Double::compare ).get();
		
		for (E e : toMergeE) {
			rev.remove(map.get(e), e);
			rev.put(set, e);
			map.put(e, set);
		}
		
		for (E e : es) {
			map.put(e, set);
			rev.put(set, e);
		}
	}

	public Set<Set<E>> close() {
		Set<Set<E>> out = new HashSet();
		
		for (Integer i : rev.keySet()) {
			Set<E> set = rev.get(i);
			if (!set.isEmpty())
				out.add(new HashSet<>(set));
		}
		
		return out;
	}
	
	public Map<E, Integer> findMap() {
		return new HashMap<E, Integer>( map );
	}
	
	public static void main (String[] args) {
		Closer<Integer> closer = new Closer<>();
		
		closer.add(1,2);
		closer.add(2,3);
		closer.add(4,5, 6, 7, 8);
		closer.add(8, 9);
		closer.add(8, 10);
		closer.add(-1, 1);
		closer.add(-11, -8);
		closer.add(-110, -8);
		
		for (Set<Integer> si : closer.close())  {
			si.stream().forEach( a -> System.out.print (a+" ") );
			System.out.println();
		}
		
	}
}