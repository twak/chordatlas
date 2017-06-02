package org.twak.viewTrace;

import javax.vecmath.Point2d;

import org.twak.tweed.gen.ProfileGen.MegaFacade;
import org.twak.utils.geom.Line;

public class SuperLine extends Line {

	public MegaFacade mega;
	
	public SuperLine( Point2d s, Point2d e ) {
		super (s,e);
	}

	public SuperLine( double x1, double y1, double x2, double y2 ) {
		super( x1, y1, x2, y2 );
	}

	public SuperLine( SuperLine o ) {
		super ( new Point2d ( o.start ), new Point2d ( o.end ) );
		this.mega = o.mega;
	}
	
	public MegaFacade getMega() {
		return mega;
	}

	public void setMega( MegaFacade m ) {
		this.mega = m;
	}
}
