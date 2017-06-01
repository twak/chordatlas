package org.twak.viewTrace.facades;

import javax.vecmath.Vector2d;

public enum Dir {
	
	L (-1,0),R (1,0),U (0, 1),D (0, -1);
	
	static{
		L.opposite = R;
		R.opposite = L;
		U.opposite = D;
		D.opposite = U;
		
		L.cw = R.cc = U;
		L.cc = R.cw = D;
		D.cw = U.cc = L;
		D.cc = U.cw = R;
	}
	
	private Vector2d dir;
	Dir opposite, 
		cw, cc; // clockwise / counterclockwise
	
	Dir (double x, double y) {
		this.dir  = new Vector2d( x, y);
	}
	
	
	public Vector2d dir() {
		return new Vector2d(dir);
	}
}