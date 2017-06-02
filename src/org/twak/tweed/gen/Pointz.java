package org.twak.tweed.gen;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple2d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.twak.utils.geom.Line;
import org.twak.utils.geom.Line3d;

import com.jme3.math.Vector3f;

public class Pointz {

	public static final Tuple2d ORIGIN = new Point2d();

	public static Point3d to3( Point2d p2 ) {
		return new Point3d( p2.x, 0, p2.y );
	}

	public static Line3d to3( Line line ) {
		return new Line3d (line.start.x, 0, line.start.y, line.end.x, 0, line.end.y);
	}

	public static Point2d to2( Tuple3d loc ) {
		return new Point2d(loc.x, loc.z);
	}

	public static Point3d to3( Point2d p2, double y ) {
		return new Point3d( p2.x, y, p2.y );
	}
	
	public static Vector3d to3V( Point2d p2, double y ) {
		return new Vector3d( p2.x, y, p2.y );
	}

}
