package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ReverseIterator<T> implements Iterable<T> {

	boolean reverse;
	Iterable<T> src;

	public ReverseIterator(Iterable<T> src, boolean reverse) {
		this.src = src;
		this.reverse = reverse;
	}

	@Override
	public Iterator<T> iterator() {
		
		if (!reverse)
			return src.iterator();
		else {
			List<T> ordered = new ArrayList();
			for (T t : src)
				ordered.add(t);
			Collections.reverse(ordered);
			return ordered.iterator();
		}
	}

}
