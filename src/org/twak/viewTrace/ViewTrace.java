package org.twak.viewTrace;

import org.twak.utils.PaintThing;

public class ViewTrace {

	public static void installPainters() {
		 PaintThing.lookup.put(LineSoup.class, new LineSoupPainter() );
	}

}
