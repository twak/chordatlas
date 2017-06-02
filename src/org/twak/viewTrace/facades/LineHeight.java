package org.twak.viewTrace.facades;

import javax.vecmath.Point2d;

import org.twak.utils.geom.Line;

public class LineHeight extends Line {

	public double min, max;
	
	public LineHeight (Point2d start, Point2d end, double min, double max) {
		super (start, end);
		this.min = min;
		this.max = max;
	}
	
}
