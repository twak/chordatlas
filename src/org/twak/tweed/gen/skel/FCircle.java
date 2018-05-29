package org.twak.tweed.gen.skel;

import javax.vecmath.Point2d;

import org.twak.utils.geom.DRectangle;

public class FCircle {

	public Point2d loc;
	public double radius;
	public RoofGreeble f;
	
	public FCircle( Point2d loc, double radius, RoofGreeble f ) {
		this.loc = loc;
		this.radius = radius;
		this.f = f;
	}

	public DRectangle toRect() {
		return new DRectangle (loc.x - radius, loc.y - radius, radius * 2, radius * 2);
	}
}
