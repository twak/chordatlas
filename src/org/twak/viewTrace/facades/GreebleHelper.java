package org.twak.viewTrace.facades;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;

public class GreebleHelper {


	public static class LPoint3d extends Point3d {
		String label = "none";
		
		public LPoint3d (Point3d loc, String label) {
			super (loc);
			this.label = label;
		}
	}
	
	public static class LPoint2d extends Point2d {
		public String label = "none";
		
		public LPoint2d (Point2d loc, String label) {
			super (loc);
			this.label = label;
		}

		public LPoint2d( double x, double y, String label ) {
			super (x, y);
			this.label = label;
		}
	}
	
	public final static String 
			WALL_EDGE  = "wall", 
			ROOF_EDGE  = "roof", 
			FLOOR_EDGE = "floor";
	
	public static LoopL<LPoint3d> findPerimeter (Face f) {
		
		LoopL<LPoint3d> out = new LoopL<>();

		
		for (Loop<SharedEdge> loop : f.edges) {
			
			Loop<LPoint3d> lout = new Loop<>();
			out.add(lout);
			
			for (SharedEdge se : loop) {
				
				String label;
				
				Face other = se.getOther( f );
				
				if (other == null || se.start.z < 0.1 && se.end.z < 0.1)
					label = FLOOR_EDGE;
				else if ( GreebleEdge.isWall( other ) )
					label = WALL_EDGE;
				else
					label = ROOF_EDGE;
				
				lout.append( new LPoint3d( se.getStart( f ), label ) );
			}
		}
		
		return out;
	}
	
	public static Loop<LPoint3d> transform( Loop<? extends LPoint3d> ll, Matrix4d mat ) {
		Loop<LPoint3d> out = new Loop<>();
		
		for (LPoint3d p : ll) {
			LPoint3d pn = new LPoint3d(p, p.label);
			mat.transform( pn );
			out.append( pn );
		}
		
		return out;
	}
	public static Loop<LPoint2d> to2dLoop(Loop<LPoint3d> in, int axis) {

		Loop<LPoint2d> out = new Loop<>();
		for (LPoint3d p3 : in) {
			LPoint2d p2 = axis == 0? new LPoint2d( p3.y, p3.z, p3.label ) : axis == 1 ? 
					new LPoint2d (p3.x,p3.z, p3.label) : 
						new LPoint2d (p3.y,p3.z, p3.label);
			out.append(p2);
		}
		return out;

	}
	
}
