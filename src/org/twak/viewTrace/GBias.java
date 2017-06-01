package org.twak.viewTrace;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import org.twak.utils.DRectangle;
import org.twak.utils.Graph2D;
import org.twak.utils.Line;
import org.twak.utils.MUtils;
import org.twak.viewTrace.QuadTree.AABBQT;
import org.twak.viewTrace.QuadTree.AxisAlignedBoundingBox;
import org.twak.viewTrace.QuadTree.QLine;

public class GBias {

	public Set<Line> lines = new HashSet();

	AABBQT<Longer> tree;
	
	double expand;
	
	public static class Longer extends AxisAlignedBoundingBox {
		
		public Line line;
		
		public Longer (Line l2, double expand) {
			
			double tol = l2.length() * expand;
			
			set (new Point2d ( Math.min (l2.start.x, l2.end.x) - tol, Math.min (l2.start.y, l2.end.y) - tol),
					Math.abs( l2.end.x - l2.start.x ) + 2 * tol, 
					Math.abs( l2.end.y - l2.start.y ) + 2 * tol
				);
			
			this.line = l2;
		}
	}
	
	
	public GBias(Graph2D gis, double expand) {

		gis.mergeContiguous(0.05);
		
		DRectangle bounds = new DRectangle();
		this.expand = expand;
		
		for (List<Line> ll : gis.map.values())
			for (Line l : ll) {
				if (bounds == null )
					bounds = new DRectangle(l.start);
				
				bounds.envelop( l.start );
				bounds.envelop( l.end   );
			}
		
		tree = new AABBQT<Longer>( bounds );
		
		for (List<Line> ll : gis.map.values())
			for (Line l : ll) {
				tree.insert( new Longer (l, expand) );
				lines.add(l);
			}
	}

	
	/**
	 * local bias
	 */
	public Double getAngle (Line line, Point2d cen) {
				
		final double PI8 = Math.PI/8;
		
		DRectangle r = new DRectangle(line);
		r.grow( expand );
		Collection<Longer> res = tree.queryRange( r );
		
		double bestScore = -Double.MAX_VALUE;
		Double bestAngle = null;

		for ( AxisAlignedBoundingBox aabb : res ) {

			Line gis = ( (Longer) aabb ).line;

			double len = gis.length();
			double dist = gis.fromPPram( 0.5 ).distance( line.fromPPram( 0.5 ) );//  gis.distance( line );

			if ( dist < len * expand ) {
				
				double angle = line.absAngle( gis );

				
				if (angle < PI8) {
					
					double score = ( PI8 - angle ) / dist;
					
					if (score > bestScore) {
						bestAngle = gis.aTan2();
						bestScore = score;
					}
				}
				else if ( MUtils.inRangeTol( angle, MUtils.PI2, PI8 ) ){
					
					double score = 0.2 * ( PI8 - angle ) / dist;

					if (score > bestScore) {
						
						gis = new Line (
								new Point2d ( gis.start.y, -gis.start.x),
								new Point2d ( gis.end.y, -gis.end.x)
								);
						
						if (gis.absAngle( line ) > PI8)
							gis = gis.reverse();
						
						bestAngle = gis.aTan2();
						bestScore = score;
					}
				}
				
			}
		}

		if (bestAngle != null)
			return bestAngle;
		else
			return null;
	}

		
	/**
	 * global bias
	 */
//	public double getAngle(double angle) {
//		
//		double minDist = Math.PI / 4;
//		double bestAngle = Double.MAX_VALUE;
//		
//		for ( Double d : snapAngles ) { //Pair<Double,Double> pair : new ConsecutivePairs<Double>(snapAngles, true) ) {
//			
//			double dist = Anglez.dist ( angle, d);
//			if (dist < minDist) {
//				minDist = dist;
//				bestAngle = d;
//			}
//		}
//		
//		return bestAngle == Double.MAX_VALUE ? angle : bestAngle;
//			
//		
//	}
	
//	public Point2d snap (Point2d pt) {
//
//		double dist = Math.pow(0.5, 2);
//		Point2d best = null;
//		
//		for (Line l : lines) {
//			
//			Point2d p = l.project(pt, true);
//			double d = p.distanceSquared(pt);
//			if (d < dist) {
//				dist = d;
//				best = p;
//			}
//		}
//		
//		
//		return best == null ? pt : best;
//	}

	
}
