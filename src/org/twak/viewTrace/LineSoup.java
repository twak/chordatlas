package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;

import org.twak.utils.DRectangle;
import org.twak.utils.Graph2D;
import org.twak.utils.Line;
import org.twak.utils.LinearForm;
import org.twak.viewTrace.QuadTree.AABBQT;
import org.twak.viewTrace.QuadTree.AxisAlignedBoundingBox;
import org.twak.viewTrace.QuadTree.PointRegionQuadTree;
import org.twak.viewTrace.QuadTree.QLine;

public class LineSoup {

	
	public AABBQT<QLine> tree;// = new PointRegionQuadTree<>( 0, 0, 0, 0 )
	
	public Set<Line> all = new HashSet<>();
	boolean multiColour = false;
	
	public LineSoup() {
	}

	public LineSoup(Iterable<Line> lines) {
		
		DRectangle bounds;
		{
			if ( lines.iterator().hasNext() )
				bounds = new DRectangle( lines.iterator().next() );
			else
				bounds = new DRectangle();
		}
		
		for (Line l : lines) {
		bounds.envelop( l.start );
		bounds.envelop( l.end );
		}
		
		tree = new AABBQT( bounds );
		
		for (Line l : lines) {
			tree.insert( new QLine(l) );
			all.add(l);
		}
	}

	public Set<Line> getNear(Point2d a, double dist) {
		return tree.queryRange( a.x - dist, a.y - dist, dist * 2, dist * 2 ).stream().map (q -> q.line).collect( Collectors.toSet() );
	}

	public void clear() {
		tree = null;
		all.clear();
	}
	
	public double intersect(double maxDist, LinearForm dir, Point2d pt) {

		double bestD = maxDist;

		for (Line l : maxDist == Double.MAX_VALUE ? all : getNear(pt, maxDist)) {
			Point2d sec = l.intersects(dir);
			
			if (sec != null)
				bestD = Math.min ( bestD, sec.distanceSquared( pt) );
		}

		return  Math.sqrt( bestD );
	}

	public List<Set<Line>> partition(double d) {
		
		List<Set<Line>> out = new ArrayList();
		
		for (Line l : all) {
			
			Set<Set<Line>> closeSets = new HashSet<>();
			
			part:
			for (Set<Line> part : out) 
				for (Line pl : part) 
					if (pl.distance( l) < d) {
						closeSets.add(part);
						continue part;
					}
			
			Set<Line> best = null;
			if (closeSets.isEmpty()) {
				best = new HashSet<>();
				out.add(best);
			
			} else {
				Iterator<Set<Line>> wtf = closeSets.iterator();
				best = wtf.next();
				while (wtf.hasNext()) {
					Set<Line> togo = wtf.next();
					best.addAll(togo);
					out.remove(togo);
				}
			}
			best.add(l);
		}
		
//		out.add(new HashSet<>(all) );
		
		return out;
	}
	
	public Map<Line, Integer> clique(double highTol, double lowTol) {
		
		int currentC = 0;

		Graph2D lookup = new Graph2D(all);
		
		Set<Line> remaining = new LinkedHashSet<>(all), visited = new HashSet<>();
		
		Map<Line, Integer> out = new HashMap<>();
		
		while (!remaining.isEmpty()) {

			Set<Line> queue = new LinkedHashSet<>();
			queue.add(remaining.iterator().next());
			while (!queue.isEmpty()) {
				Line l = queue.iterator().next();

				queue.remove(l);
				remaining.remove(l);
				visited.add(l);
				out.put(l, currentC);
				
				for (Point2d pt : l.points()) { // find nearest with parity one

					double tol = lookup.get(pt).size() % 2 == 0 ? highTol : lowTol;

					for (Line l2 : getNear(pt, tol)) {
						if (!visited.contains(l2) && l2.distance(pt, true) < tol)
							queue.add(l2);
					}
				}
				
			}
			currentC++;
		}

		return out;
	}

}
