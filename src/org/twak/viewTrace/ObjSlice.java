package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.utils.Line;
import org.twak.utils.LinearForm3D;
import org.twak.utils.ObjRead;
import org.twak.utils.geom.Line3d;
import org.twak.utils.results.LineOnPlane;
import org.twak.utils.results.OOB;

public class ObjSlice {

	public static List<Line> sliceTri(ObjRead mesh, double h, int majorAxis ) {
		
		 int[] flatAxis = new int[] { ( majorAxis + 1 ) % 3, ( majorAxis + 2 ) % 3 };
		 
		 if (flatAxis[0] > flatAxis[1]) {
			 int tmp = flatAxis[0];
			 flatAxis[0] = flatAxis[1];	
			 flatAxis[1] = tmp;
		 }
		 
		Vector3d sliceNormal = new Vector3d(
				majorAxis == 0 ? 1: 0, 
				majorAxis == 1 ? 1: 0,
				majorAxis == 2 ? 1: 0),
				
				slicePt = new Vector3d( sliceNormal );
		
		slicePt.scale(h);
		LinearForm3D slicePlane = new LinearForm3D(sliceNormal, slicePt);

		return slice(mesh, flatAxis, slicePlane, Double.MAX_VALUE, null, Double.MAX_VALUE).stream().map(l -> new Line(l.start.x, l.start.z, l.end.x, l.end.z)).collect(Collectors.toList());
	}
	
	private final static int[] flatAxis = new int[] { 0,1,2 };
	
	public static List<Line3d> sliceTri(
			ObjRead mesh, 
			LinearForm3D plane, 
			double maxGradDev, 
			Vector3d goodNormal, 
			double goodNormalTol ) {
		
		return slice(mesh, flatAxis, plane, maxGradDev, goodNormal, goodNormalTol);
	}

	private static List<Line3d> slice(
			ObjRead mesh, 
			int[] axisOrder, 
			LinearForm3D slicePlane, 
			double maxGradDev, 
			Vector3d goodNormal, 
			double goodNormalTol) {

		List<Line3d> results = new ArrayList();
		
		face:
		for (int[] ind : mesh.faces) {
			
			Point3d[] points = new Point3d[2];
			int pCount = 0;

			Vector3d normal = new Vector3d();
			
			for (int t = 0; t < ind.length; t++) {

				double[] 
						a = mesh.pts[ind[t]], 
						b = mesh.pts[ind[(t + 1) % ind.length]], 
						c = mesh.pts[ind[(t + 2) % ind.length]];

				Point3d  aa = new Point3d(a);
				Vector3d ab = new Vector3d(b), bc = new Vector3d(c);
				ab.sub(aa);
				bc.sub(new Point3d(b));
				
				Vector3d cross = new Vector3d();
				cross.cross(ab, bc);
				normal.add(cross);

				
				if (ab.z < 0) { // slice in consistent order to cut adjacent faces in same direction
					aa = new Point3d(b);
					ab = new Vector3d(a);
					ab.sub(aa);
				}
				
				double len = ab.length();
				ab.normalize(); // needed?

				Point3d cR = slicePlane.collide(aa, ab, len);

				if (cR instanceof LineOnPlane) { 
					LineOnPlane out = (LineOnPlane) cR;
					out.direction.scale(out.distance);
					
//					if (Math.abs( slicePlane.pointDistance(aa) ) < 0.01)
//						add ( results, new Line(a[axisOrder[0]], a[axisOrder[1]], b[axisOrder[0]], b[axisOrder[1]])); // direction is randomly assigned
					
				} else if (cR instanceof OOB)
					continue;
				else if (cR != null) {
					if (pCount < points.length)
						points[pCount++] = cR;
				}
			}
		
			if (goodNormal != null && goodNormal.angle( normal ) > goodNormalTol )
				continue face;
			
			if (pCount == 2) {
				
				Vector3d lineDir = new Vector3d( points[1] );
				lineDir.sub(points[0]);
				
				Vector3d cross = new Vector3d();
				cross.cross(lineDir, normal);
				
				if ( cross.y > 0 ) { // only for y-up cuts?
					Point3d tmp = points[0];
					points[0] = points[1];
					points[1] = tmp;
				}
				
				if (maxGradDev != Double.MAX_VALUE) { // tidy this to parameterize up. and when slicing walls, only use faces pointing forwards.
					Point3d up = new LinearForm3D( normal, new Point3d() ).project( new Point3d(0,1,0) );
					
					double angle = new Vector3d( up ).angle(lineDir);
					if ( angle > maxGradDev && angle < Math.PI - maxGradDev )
						continue face;
				}
				
				add(results, new Line3d(points[0], points[1]));
//					points[0].x, 
//					points[0].z,
//					points[1].x, 
//					points[1].z
//				));
			}
		}
		
		return results;
	}

	private static void add(List<Line3d> results, Line3d line) {
		if (line.start.equals(line.end))
			return;
		results.add(line);
		
	}
	
}
