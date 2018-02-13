package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.siteplan.jme.MeshBuilder;
import org.twak.utils.Mathz;
import org.twak.utils.Mathz.Frame;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.Pair;

/**
 * Extrude a profile, using a set of planes at the start and end to define the end-type.
 * 
 */

public class Tube {
	
	public interface CrossGen {
		public List<Point2d> gen (Vector2d left, Vector2d right);
	}
	
	public static void tube (MeshBuilder out, 
			Collection<LinearForm3D> before, Collection<LinearForm3D> after, 
			Line3d line, LinearForm3D left, LinearForm3D right, CrossGen gen ) {
		
		if (angle ( before, line) < 0.1 || angle ( after, line ) < 0.1 )
			return; // too pointy to touch
		
		Point3d middle = line.fromPPram( 0.5 );
		
		Vector3d along = line.dir();
		along.normalize();
		Vector3d nAlong = new Vector3d (along);
		nAlong.negate();
		
		Vector3d o1 = left.normal(), u1 = new Vector3d();
		u1.cross( along, o1 );
		
		Frame frame = Mathz.buildFrame ( o1, u1, along, middle);
		
		Vector3d u2 = right.normal();
		u2.cross( u2, along );
//		u2.add( middle );
		
		Vector2d leftDir = Mathz.toXY ( frame, u1 );
		Vector2d rightDir = Mathz.toXY ( frame, u2 );
		
		List<Point3d> profilePts = gen.gen( leftDir, rightDir ).stream().
				map( p -> Mathz.fromXY( frame, p ) ).collect( Collectors.toList() );
		
		List<LinearForm3D> dummy = new ArrayList<>();
		
		for (Pair <Point3d, Point3d> pair : new ConsecutivePairs<Point3d>( profilePts, true ) ) {
			
			Point3d 
					f1 = clip ( pair.first (), along , after , dummy ),
					f2 = clip ( pair.second(), along , after , dummy ),
					b1 = clip ( pair.first (), nAlong, before, dummy ),
					b2 = clip ( pair.second(), nAlong, before, dummy );

			out.add (f2, f1, b1, b2);
		}
		
//		cap( out, after ,  along, profilePts, true  );
//		cap( out, before, nAlong, profilePts, false );
	}

	private static double angle( Collection<LinearForm3D> lfs, Line3d line ) {
		
		double min = Double.MAX_VALUE;
		
		for ( LinearForm3D lf : lfs ) {
			Point3d s = lf.project( line.start ), e = lf.project( line.end );
			min = Math.min( min, line.angle( new Line3d( s, e ).dir() ) );
		}
		
		return min;
	}

	private static void cap( MeshBuilder out, Collection<LinearForm3D> after, 
			Vector3d along, List<Point3d> profilePts, boolean reverse ) {

		MultiMap<LinearForm3D, Point3d>faces = new MultiMap<>();
		for (Point3d p : profilePts) {
			List<LinearForm3D> hit = new ArrayList<>();
			Point3d c = clip (p, along, after, hit);
			for (LinearForm3D h : hit)
				faces.put( h, c );
		}
		
		for (Map.Entry<LinearForm3D, List<Point3d>> e : faces.map.entrySet()) 
			out.add ( new Loop<Point3d> ( e.getValue() ).singleton(), null, reverse );
	}

	private static Point3d clip( Point3d pt, Vector3d dir, Collection<LinearForm3D> after, List<LinearForm3D> hit ) {
		
		Point3d out = pt;
		double bestScore = Double.MAX_VALUE;
		
		Line3d line = Line3d.fromRay (pt, dir);
		
		for ( LinearForm3D l : after) {
			
			
			if (l == null)
				continue;
			
			Point3d sec = l.collide( pt, dir );
			
			if (sec == null)
				continue;
			
			double score = line.findPPram(sec);
			
			if (score < bestScore) {
				out = sec;
				bestScore = score;
			}
		}
		
		for ( LinearForm3D l : after) {
			
			if (l == null)
				continue;
			
			Point3d sec = l.collide( pt, dir );
			if (sec != null) {
			double score = line.findPPram(sec);
			if (Math.abs (score - bestScore) < 0.00001 ) 
				hit.add(l);
			}
		}
		
		
		return out;
	}
}
