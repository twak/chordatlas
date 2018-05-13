package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.camp.Tag;
import org.twak.tweed.gen.Pointz;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm;

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
				
				Point3d pt = se.getStart(f);
				if (pt != null)
					lout.append( new LPoint3d( pt, label ) );
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

	/**
	 * For a single roof pitch / face
	 */
	public static LoopL<Point2d> wholeRoofUVs ( LoopL<LPoint3d> coords, DRectangle bounds ) {
		
		return coords.new Map<Point2d>() {
			@Override
			public Point2d map( Loopable<LPoint3d> input ) {
				return bounds.normalize( Pointz.to2XY( input.get() ) );
			}
		}.run();
	}
	
	public static LoopL<Point2d> roofPitchUVs ( LoopL<LPoint2d> coords, Point2d s, Point2d e, double d ) {
		
		Line l = new Line (s, e);
		double ll =  l.length();
		
		return coords.new Map<Point2d>() {
			@Override
			public Point2d map( Loopable<LPoint2d> input ) {
				
				Point2d x = l.project( input.get(), false );
				
				return new Point2d ( l.findPPram( x ) * ll * d, input.get().distance( x ) * d);
			}
		}.run();
	}
	
	public static LoopL<Point2d> uvs( LoopL<Point3d> coords, double d ) {
		
		Point3d s = coords.get( 0 ).getFirst(), e = coords.get( 0 ).getFirstLoopable().next.get();
		
		Line3d l = new Line3d (s, e);
		double ll =  l.length();
		
		return coords.new Map<Point2d>() {
			@Override
			public Point2d map( Loopable<Point3d> input ) {
				
				Point3d x = l.closestPointOn( input.get(), false );
				
				return new Point2d ( l.findPPram( x ) * ll * d, input.get().distance( x ) * d);
			}
		}.run();
		
	}
	

	public static LoopL<Point2d> wallUVs( LoopL<Point2d> coords, DRectangle unit ) {

		return coords.new Map<Point2d>() {
			@Override
			public Point2d map( Loopable<Point2d> pt ) {
				return unit.normalize( pt.get() );
			}
		}.run();
	}


	public static DRectangle findRect( Loop<? extends Point2d> skelFaces ) {
		
		double[] bounds = Loopz.minMax2d( skelFaces );
		
		DRectangle all = new DRectangle(
				bounds[0], 
				bounds[2], 
				bounds[1] - bounds[0], 
				bounds[3] - bounds[2] );
		
		return all;
		
	}
	public static DRectangle findRect( List<Loop<? extends Point2d>> skelFaces ) {
		
		DRectangle bounds = null;
		
		for (Loop<? extends Point2d> loop : skelFaces) {
			for (Loopable<? extends Point2d> pt : loop.loopableIterator())
				if (bounds == null)
					bounds = new DRectangle(pt.get().x, pt.get().y, 0, 0);
				else
					bounds.envelop( pt.get() );
		}
		return bounds;
	}
	
	public static LoopL<Point2d> findRectagle( Loop<LPoint2d> flat, Point2d s, Point2d e ) {

//		new Plot(flat);
		
		List<Point2d> left = new ArrayList<>(), right = new ArrayList<>();
		
		for (Loopable<LPoint2d> pt : flat.loopableIterator()) {
			if (pt.get().equals (s) && left.isEmpty()) {
				double h = pt.get().y;
				do {
					left.add(pt.get());
					h = pt.get().y;
					pt = pt.getNext();
				}
				while (pt.get().y >= h);
			}
		
			if (pt.get().equals (e) && right.isEmpty()) {
				
				double h = pt.get().y;
				do {
					right.add(pt.get());
					h = pt.get().y;
					pt = pt.getPrev();
				}
				while (pt.get().y >= h);
			}
		}

		for (List<Point2d> l : new List[] {left, right}) 
			while ( l.size() > 2 && l.get( l.size()-2 ).y == l.get(l.size() -1).y)
				l.remove (l.size()-1);

		if (left.size() < 2 || right.size() < 2 )
			return null;
		
		double tween = (
					left .stream().mapToDouble( p -> p.x ).max().getAsDouble() + 
					right.stream().mapToDouble( p -> p.x ).min().getAsDouble() ) /2; 
		
		PtInChain
				xyL = findValid(left , tween, true),
				xyR = findValid(right, tween, false);
		
		double height = Math.min (xyL.y, xyR.y);
		
		if (xyL.y > xyR.y)
			xyL = setToHeight (left, true, xyL.x, height);
		else
			xyR = setToHeight (right, false, xyR.x, height);
		
		LoopL<Point2d> out = new LoopL<>();
		
		{
			Loop<Point2d> rect = new Loop<>();
			out.add( rect );
			rect.append( new Point2d( xyL.x, 0 ) );
			rect.append( new Point2d( xyL.x, xyL.y ) );
			rect.append( new Point2d( xyR.x, xyR.y ) );
			rect.append( new Point2d( xyR.x, 0 ) );
		}
		
		Loop<Point2d> lef = toPoly( left, xyL );
		if ( Loopz.area( lef ) > 0.001 )
			out.add( lef );
		
		Loop<Point2d> rig = toPoly( right, xyR );
		rig.reverse();
		if ( Loopz.area( rig ) > 0.001 )
			out.add( rig );
		
		{
			Loop<Point2d> top = new Loop<>();

			top.append( new Point2d( xyL.x, xyL.y ) );

			if ( xyL.prevPt < left.size() - 1 )
				top.append( new Line( left.get( xyL.prevPt ), left.get( xyL.prevPt + 1 ) ).fromPPram( xyL.frac ) );

			for ( int i = xyL.prevPt + 1; i < left.size(); i++ )
				top.append( left.get( i ) );

			for ( int i = right.size() - 1; i > xyR.prevPt; i-- )
				top.append( right.get( i ) );

			if ( xyR.prevPt < right.size() - 1 )
				top.append( new Line( right.get( xyR.prevPt ), right.get( xyR.prevPt + 1 ) ).fromPPram( xyR.frac ) );

			top.append( new Point2d( xyR.x, xyR.y ) );

			if ( Loopz.area( top ) > 0.001 )
				out.add( top );
		}
	
		return out;
	}

	public static Loop<Point2d> toPoly( List<Point2d> left, PtInChain xyL ) {
		
		Loop<Point2d> lef = new Loop<>();
		for (int i = 0; i <= xyL.prevPt; i++)
			lef.append (left.get(i));
		
		if (xyL.frac > 0 && xyL.prevPt < left.size()-1) 
			lef.append (new Line ( left.get ( xyL.prevPt ), left.get(xyL.prevPt + 1)).fromPPram( xyL.frac ) );
		
		lef.append( new Point2d(xyL.x, xyL.y) );
		lef.append( new Point2d(xyL.x, 0) );
		return lef;
	}
	
	public static PtInChain setToHeight( List<Point2d> chain, boolean left, double x2, double y2 ) {
		
		double bestX = chain.get(0).x;
		
		for (int i = 1; i < chain.size(); i++) {
			Point2d 
				p = chain.get(i-1),
				n = chain.get(i);
			
			bestX = left ? Math.max (p.x, bestX) : Math.min (p.x, bestX);
			
			PtInChain first = new PtInChain( new Point2d ( n ), i, 0 );
			first.x = bestX;
			
			if (Math.abs(n.y-y2) < 0.001) {
				first.x = left ? Math.max (n.x, first.x) : Math.min (n.x, first.x);
				return first;
			}
			
			Line pn = new Line(p, n);
			
			if (n.y > y2) {
				Point2d sec = new LinearForm(pn).intersect( new LinearForm( 0, 1, y2 ) );
				if (sec == null)
					return first;
				
				sec.x = left ? Math.max (bestX, sec.x) : Math.min (bestX, sec.x);
				
				return new PtInChain( sec, i-1, pn.findPPram( sec ) );
			}
		}
		return null;
	}

	public static PtInChain findValid( List<Point2d> chain, double startingX, boolean left ) {
		
		double bestArea = -Double.MAX_VALUE;
		PtInChain bestPt = new PtInChain ( chain.get(0), 0, 0 );
		
		double bestX = chain.get(0).x;
		
		Point2d pt;
		for (int i = 0; i < chain.size(); i++) {
			
			pt    = chain.get(i);
			bestX = left ? Math.max (pt.x, bestX) : Math.min (pt.x, bestX); 
			
			double area = Math.abs(startingX-bestX) * pt.y;

			if (area > bestArea || area == 0 && bestArea == 0 && pt.y > bestPt.y) {
				bestPt = new PtInChain( new Point2d ( bestX, pt.y), i, 0);
				bestArea = area;
			}
		}
		
		return bestPt;
	}
	
	public static class PtInChain extends Point2d {
		
		public PtInChain( Point2d point2d, int pos, double frac ) {
			super (point2d);
			this.prevPt = pos;
			this.frac = frac;
		}
		
		int prevPt;
		double frac;
	}

	public static Tag getTag( Set<Tag> tags, Class<? extends Tag> class1 ) {
		
		for (Tag t : tags)
			if (t.getClass() == class1)
				return t;
		
		return null;
	}
}
