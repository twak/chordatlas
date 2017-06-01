package org.twak.viewTrace;

import javax.vecmath.Point2d;

import org.twak.utils.Line;

class SupportPoint {
	
	Point2d pt;
	Line line;
	double support;
	
	public SupportPoint(Line l, Point2d pt) {
		this.line = l;
		this.pt = pt;
	}
}