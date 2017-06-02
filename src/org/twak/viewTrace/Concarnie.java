package org.twak.viewTrace;

import java.awt.Paint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import org.apache.commons.math3.exception.InsufficientDataException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.Segment;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.apache.commons.math3.geometry.partitioning.Region.Location;
import org.twak.utils.MUtils;
import org.twak.utils.PaintThing;
import org.twak.utils.Pair;
import org.twak.utils.collections.ConsecutiveItPairs;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.collections.ItComb;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.MultiMapSet;
import org.twak.utils.geom.Anglez;
import org.twak.utils.geom.Graph2D;
import org.twak.utils.geom.Line;
import org.twak.utils.geom.LinearForm;
import org.twak.utils.geom.UnionWalker;


/**
 * Convexity of Concavities implementation
 * 
 * @author twak
 *
 */
public class Concarnie {

	// io state
	public LoopL<Point2d> out;
	Graph2D graph = new Graph2D();
	LineSoup in;
	
	// iteration state
	int depth;
	
	SliceParameters P;
	
	public Concarnie ( LineSoup soups, Map<Line, Integer> cliques, SliceParameters P ) {
		
		this.in = soups;
		this.P = P;

//		PaintThing.debug.clear();
		
		MultiMapSet<Integer, Line> partitions = new MultiMapSet<>();
		soups.all.stream().forEach(x -> partitions.put(cliques.get(x), x));
		
		
		for (Integer clique : partitions.keySet()) {
		
//			if ( clique != 1 )
//				continue;
					
			Problem p = new Problem (partitions.get(clique));
			
//			PaintThing.debug.put(clique, p.soup);
			
			boolean posArea = signedArea(p.soup) > 0;
	
//			if (posArea)
				main(Collections.singletonList(p), posArea ? 0 : 1);
		}
			
		tidy ( graph );
	}

	private class Problem {
		
		List<Line> portal;
		Set<Line> soup;
		List<Line> hull;
		ConvexHull2D chull;
		double portalLength;
		
		public Problem(Set<Line> soup) {
			this.soup = soup;
			this.portal = Collections.emptyList();
			
			doHull();
		}

		public Problem(List<Line> portal, Set<Line> soup) {
			this.soup = soup;
			this.portal = portal;
			
			doHull();
		}
		
		public void doHull() {
			
			// build convex hull from soup and portal
			portal.stream().forEach(x -> portalLength += x.length());
			
			List<Vector2D> verts = new ArrayList();
			
			Set<Point2d> seen = new HashSet<>();

			for (Line lp : portal) {
				verts.add( asV ( lp.start ) );
				verts.add( asV ( lp.end   ) );
				
				seen.add(lp.start);
				seen.add(lp.end);
			}
			

			for (Line l : soup) {
				if (seen.add(l.start))
					verts.add(asV(l.start));
				if (seen.add(l.end))
					verts.add(asV(l.end));
			}
			
			chull = new MonotoneChain(false,0.0001).generate(verts); // failure observed at 0.0001
			
			hull = Arrays.stream( chull.getLineSegments() ).map(x -> asL(x) ).collect( Collectors.toList() );
			
			Collections.reverse ( hull );
			
			
			if (hull.isEmpty()) {
				addPortal();
				return;
			}
				
			
			if (!portal.isEmpty()) { /* remove portal from hull, by projection */

				Point2d s = portal.get(0).start, e = portal.get(portal.size() - 1).end;
				
				R
					sx = project(s), 
					ex = project(e);

				Line patchStart = new Line ( hull.get(sx.seg).start, s ),
					 patchEnd   = new Line ( e, hull.get(ex.seg).end );
				
				int insertAt;
				 
				if (sx.perim < ex.perim ) { 
					hull.subList(sx.seg, ex.seg + 1).clear();
					insertAt = sx.seg;
				} else {
					hull.subList(sx.seg, hull.size()).clear();
					hull.subList(0, Math.min (hull.size(), ex.seg + 1)).clear();
					insertAt = hull.size();
				}

				if (sx.seg == ex.seg && sx.perim > ex.perim ) // single edge in hull (portal is all but section of a single edge)
					hull.add(insertAt, new Line (e,s));
				else {

					if (!patchEnd.start.equals(patchEnd.end))
						hull.add(insertAt, patchEnd);

					if (!patchStart.start.equals(patchStart.end))
						hull.add(insertAt, patchStart);
				}
				
			}
		}

		class R {
			
			int seg;
			Point2d pos;
			double perim;
			
			public R(int i, Point2d proj, double perim) {
				this.seg = i;
				this.pos = proj;
				this.perim = perim;
			}
		}
		
		private R project (Point2d pt) {
			double bestDist = Double.MAX_VALUE;
			R best = null;
			
			double perim = 0;
			
			for (int i = 0; i < hull.size(); i++ ) {
				Point2d proj = hull.get(i).project(pt, true);
				double dist = proj.distanceSquared(pt);
				if (dist < bestDist) {
					
					bestDist = dist;
					best = new R (i, proj, perim + hull.get(i).start.distance(proj));
				}
				
				perim += hull.get(i).length();
			}
			
			return best;
		}

		public void addPortal() {
			
			for (Line l : portal) {
				l = depth % 2 == 1 ? l : l.reverse();
				graph.add(l);
			}
		}

		private double area() {
			return Math.abs (signedArea( hull ));
		}
		
		private double minHullAngle() { // very sharp, and very concave angles return high values
			
			if (hull.size() <= 1)
				return 0;
			
			double min = Double.MAX_VALUE;
			
			for (Pair <Line,Line> a : new ConsecutivePairs<>(hull, false)) 
				min = Math.min(min, Anglez.dist ( a.first().aTan2(), a.second().aTan2() ) );
			
			return min;
		}
	}

	private void main ( List<Problem> current, int startingDepth ) {
		
		depth = startingDepth;
		
		do {

			List<Problem> tmp = current;
			current = new ArrayList<>();
			
			int count = 0;
			
			removeOverlaps(tmp);
			
			for ( Problem p1 : tmp ) {
				List<Problem> children = apply ( p1, count++);
				current.addAll( children ); 
			}
			
			depth++;
			
		} while (!current.isEmpty());

	}
	
	private List<Problem> apply ( Problem problem, int count ) {

//		if (depth == 2) {
//			PaintThing.debug.put(problem, problem.portal);
//			PaintThing.debug.put(problem, problem.portal);
//			PaintThing.debug.put(problem, problem.soup);
//		}
		
		if (depth > 10 || problem.soup.isEmpty()) {
			problem.addPortal();
			return Collections.emptyList();
		}
		
		Set<Line> portals = new HashSet<>();
		Set<Line> in = new HashSet<>(problem.soup);
		
		for ( Line sl : problem.hull ) {
			
			RangeMerge<Line> rm = new RangeMerge<>(P.CON_TOL, P.CON_TOL);
			
			LinearForm lf = new LinearForm( sl );
			
			for (Line l : problem.soup ) {
				
				if ( onHull(sl, l) ) {
					
					double 
						pps = lf.findPParam ( l.start ),
						ppe = lf.findPParam ( l.end   );
					
					rm.add( pps, ppe, l);
					in.remove(l);
				}
			}
			
			List<Double> rmGet = rm.get();
			
			if (rmGet.isEmpty()) {
				
				if (!sl.start.equals(sl.end)) {
					

					if ( Double.isNaN(sl.start.x))
						System.out.println("help!");
					
					portals.add( sl );//  whole thing is portal
				}
				
			} else {

				List<Point2d> occupied = new ArrayList();
				
				{
					double lf1 = lf.findPParam ( sl.start ),
						   lf2 = lf.findPParam ( sl.end );
					
					if (lf1 > lf2) { 
						double tmp = lf1;
						lf1 = lf2;
						lf2 = tmp;
					}
					
					for (double d : rmGet) {

						d = MUtils.clamp (d, lf1, lf2);
						occupied.add(lf.fromPParam(d));
					}
				}
				
				boolean onHull = false;
				
				{
					Point2d snapE = occupied.get(0), snapS = occupied.get(occupied.size() -1);
					
					if ( snapS.distance(sl.start) < P.CON_TOL )
						snapS.set(sl.start);
					else  {
						occupied.add( new Point2d( sl.start ));
					}
					
					if (snapE.distance(sl.end) < P.CON_TOL)
						snapE.set(sl.end  );
					else {
						occupied.add(0, new Point2d( sl.end ));
						onHull = true;
					}
				}
			
				
				for (Pair<Point2d, Point2d> pair : new ConsecutiveItPairs<Point2d>(occupied) ) {
					
					onHull =! onHull;
					
					
					if (pair.first().equals(pair.second()))
						continue;
					
					Line line = new Line(pair.first(), pair.second());

					if (onHull) {
						
						if ( depth % 2 == 0 )
							line = line.reverse();
						
						graph.add( line );
						
					} else {
						
						portals.add( line.reverse() );
					}
				}
			}
		}
		
		if (in.size() == problem.soup.size()) { // we didn't do anything! remove something, anything...
			
			List<Line> d = new ArrayList(in);
			Collections.sort ( d, (a,b) -> Double.compare (a.length(), b.length()) );
			
			for (int i = 0; i < Math.max(1,in.size() * 0.33); i++) // remove the shortest 1/3 lines
				in.remove(d.get(i));
		}

		List<Portal> mergedPortals = mergeConsecutive (portals);
		
		MultiMapSet<Portal,Line> subproblems = new MultiMapSet(); // assign each member of in to a portal

		if (!mergedPortals.isEmpty())
		{
			MultiMapSet<Portal, Line> sub2 = new MultiMapSet();            // O(n^3) closest-clique assignment

			for ( Portal p : mergedPortals )
				sub2.putAll( p, p.lines );

			while ( !in.isEmpty() ) {

				double bestDist = Double.MAX_VALUE;
				Portal bestP = null;
				Line bestL = null;

				for ( Line l : in )
					for ( Portal p : sub2.keySet() )
						for ( Line sl : sub2.get( p ) ) {

							double dlsl = l.distance( sl ); 
							
							if ( dlsl > Math.max(P.CON_TOL*3, p.length * 0.5 ) ) // ignore lines a long way away
								continue;
								
							double dist = dlsl + 0.1 * l.distance( p.summary );
							
//							dist = 10e6;
//							double pPram = p.summary.findPPram( l.fromFrac( 0.5 ) );
//							if ( pPram < -0.2 || pPram > 1.2 )
//								dist += l.distance( p.summary );
							
							if ( dist < bestDist ) {
								bestP = p;
								bestDist = dist;
								bestL = l;
							}
						}
				
				
				if (bestL == null)
					break;
				
				in.remove( bestL );
				
				double lenBestL = bestL.length();
				if (lenBestL > P.CON_TOL && lenBestL > 0.5 * bestP.summary.length()) {
					in.add(new Line (bestL.start, bestL.fromPPram( 0.5 )));
					in.add(new Line (bestL.fromPPram( 0.5 ), bestL.end));
				}
				else
				{
					subproblems.put( bestP, bestL );
					sub2.put( bestP, bestL );
				}
			}
		}
		else {
			mergedPortals.add(null);
			subproblems.putAll( null, in );
		}
		
//		if (depth == 1) {
//			int c = 0;
//			for (Portal p : subproblems.keySet()) {
//				if (p != null) {
//					PaintThing.debug.put(c, subproblems.get(p));
//					PaintThing.debug.put(c, p.lines);
//					PaintThing.debug.put(c, p.lines);
//				}
//				c++;
//			}
//		}
		
		
//		for (Line l : in) { // voronoi
//			
//			double bestDist = Double.MAX_VALUE;
//			Portal best = null;
//			
//			for (Portal portal : mergedPortals) {
//				
//				for (Line pl : portal.lines) {
//					double dist = pl.distance(l);
//					if ( dist < bestDist ) {
//						bestDist = dist;
//						best = portal;
//					}
//				}
//			}
//			
//			subproblems.put(best, l);
//		}
		
//		for (Portal portal : mergedPortals) {  // chaining 
//			connectedLines(in, subproblems, portal, portal.lines.get(0).start, TOL * 1.5, true);
//			connectedLines(in, subproblems, portal, portal.lines.get(portal.lines.size()-1).end, TOL * 1.5, false);
//			
//			System.out.println(portal.length +" >> " + subproblems.get(portal).size());
//			if (depth == 0) {
//				PaintThing.debug.put(portal, portal.lines);
//				PaintThing.debug.put(portal, subproblems.get(portal));
//			}
//		}
		
		// ignores stuff not connected to portal(!)
		
		return mergedPortals.stream().map(
				x -> new Problem( x == null ? Collections.emptyList() : x.lines, subproblems.get(x) ) ) .collect(Collectors.toList());
	}

//	private void connectedLines(Set<Line> in, MultiMapSet<Portal, Line> subproblems, Portal portal, Point2d first, double tol, boolean forwards) {
//		Set<Point2d> toProcess = new HashSet<>();
//		toProcess.add(first);
//
//		while (!toProcess.isEmpty()) {
//		
//			Point2d current = toProcess.iterator().next();
//			toProcess.remove(current);
//			
//			Iterator<Line> lit = in.iterator();
//			while (lit.hasNext()) {
//				Line line = lit.next();
//				Point2d lp = forwards ? line.start: line.end;
//
//				if (lp.distance(current ) < tol) {
//					lit.remove();
//					toProcess.add(forwards ? line.end : line.start);
//					subproblems.put(portal, line);
//				}
//			}
//		}
//	}

	private static Line asL (Segment s) {
		return new Line(s.getEnd().getX(), s.getEnd().getY(), s.getStart().getX(), s.getStart().getY());
	}
	
	private static Vector2D asV (Point2d in) {
		return new Vector2D(in.x, in.y);
	}
	
	public static Point2d asP (Vector2D in) {
		return new Point2d(in.getX(), in.getY());
	}
	
	private static class Portal {
		public Portal(){}
		public Portal(Line put) {
			lines.add(put);
			this.summary = put;
		}

		List<Line> lines = new ArrayList<>();
		Line summary;
		double length;
	}
	
	private List<Portal> mergeConsecutive( Set<Line> portals ) { // merge consecutive portals 

		List<Portal> portalsOut = new ArrayList();

		Map<Point2d, Line> starts = new HashMap<>(), ends = new HashMap<>();

		for ( Line l : portals ) {
			Line replace = starts.put( l.start, l );
			if ( replace != null )
				portalsOut.add( new Portal( replace ) );
			replace = ends.put( l.end, l );
			if ( replace != null )
				portalsOut.add( new Portal( replace ) );
		}

		//				starts = portals.stream().collect( Collectors.toMap( x-> x.start, x -> x)),
		//				ends   = portals.stream().collect( Collectors.toMap( x-> x.end  , x -> x));

		while ( !starts.isEmpty() ) {

			Portal p = new Portal();
			portalsOut.add( p );

			Line start = starts.entrySet().iterator().next().getValue(), current = start;

			do {
				p.lines.add( current );
				p.length += current.length();
				starts.remove( current.start );
				ends.remove( current.end );

				current = starts.get( current.end );

			} while ( current != null );

			current = start;
			current = ends.get( current.start );

			if ( current != null )
				do {
					p.lines.add( 0, current );
					p.length += current.length();
					starts.remove( current.start );
					ends.remove( current.end );

					current = ends.get( current.start );

				} while ( current != null );

			p.summary = new Line( p.lines.get( 0 ).start, p.lines.get( p.lines.size() - 1 ).end );
		}

		Collections.sort( portalsOut, ( a, b ) -> -Double.compare( a.length, b.length ) );

		return portalsOut;
	}

	private void tidy(Graph2D graph) {
		
		UnionWalker uw = new UnionWalker();
		
		for (Point2d a : graph.map.keySet()) {
			for (Line l : graph.get(a)) {
				uw.addEdge(l.start, l.end);
				
//				PaintThing.debug.put("flibble", l);
				
			}
		}
		
		out = uw.findAll();
		
//		for every hole, find nearest, bigger non-hole.
		
//		if (false)
		for (Loop<Point2d> loop : out) {
			for (Loopable<Point2d> pt : loop.loopableIterator()) {
				
				Line 
					prev = findSupporting(pt,  1),
					next = findSupporting(pt, -1);

				if ( prev != null && next != null ) {
					Point2d bestFit = prev.intersects(next, false);
					
					if ( 
					    bestFit != null && 
					    pt.get().distance(bestFit) < P.CON_TOL &&
						bestFit.distanceSquared(prev.start) < bestFit.distanceSquared(prev.end) &&  // if nearer other ends of lines, don't use
						bestFit.distanceSquared(next.end) < bestFit.distanceSquared(next.start) 
						) {
							pt.get().set(bestFit);
					}
				}
			}
		}
		
		Iterator<Loop<Point2d>> lit = out.iterator();
		
//		if (false)
		while (lit.hasNext()) {
			Loop<Point2d> loop = lit.next();
			Loopable<Point2d> start = loop.start, current = start;
			int size = loop.count();
			
			boolean again;
			do {
				again = false;
				Point2d a = current.getPrev().get(),
						b = current.get(),
						c = current.getNext().get();
				
				Line ab = new Line(a,b),
				     bc = new Line (b,c);
				
				double angle = Anglez.dist( ab.aTan2(), bc.aTan2() );
				
				if ( 
						a.distanceSquared(b) < 0.0001 || // corner filter moves corners to same point!
						b.distanceSquared(c) < 0.0001 ||
						angle < 0.2 && Math.abs ( MUtils.area(a, b, c) ) < 50 * P.CON_TOL * P.CON_TOL  ) // nearly parallel, but small area removed
//					ab.length() + bc.length() > TOL/2 && 
//						angle > Math.PI - 0.1 ) // pointy corner
//						a.distanceSquared(c) < 0.0001 )
				{
					current.getPrev().setNext(current.getNext());
					current.getNext().setPrev(current.getPrev());
					size--;
					if (start == current)
						loop.start = start = current.getPrev();
					
					again = true;
					current = current.getPrev();
				}
				else
					current = current.getNext();
			}
			while ( ( again || current != start) && size > 2);
			
			if (size <= 2)
				lit.remove();
		}
	}


	private Line findSupporting(Loopable<Point2d> pt, int dir) {
		
		double totalDist = 0;
		Point2d ptg = pt.get();
		
		Loopable<Point2d> current = pt;
		int count = 0;
		do {
			
			Point2d a = current.get(), b = current.move(dir).get();
			
			if (dir < 0) {
				Point2d tmp = a;
				a = b;
				b = tmp;
			}
			
			Line hull = new Line (a,b);

			double bestDist = Double.MAX_VALUE;
			
			Line bestLine = null;
			
			for (Line l :  new ItComb<>( in.getNear(hull.start, 0.5), in.getNear(hull.end, 0.5) ) )
				if (Anglez.dist(l.aTan2(), hull.aTan2()) < 0.2 && maxPerpDistance(l, hull) < 0.2) {
					double dist = l.distance(ptg, true);
					if (dist < bestDist) {
						bestDist = dist;
						bestLine = l;
					}
				}
			
			if (bestLine != null)
				return bestLine;
			
			totalDist += hull.length();
			current = current.move(dir);
			
		} while ( totalDist < 1 && count++ < 30 );
		
		return null;
	}

	private boolean onHull(Line hull, Line l) {
		double la = depth % 2 == 0 ? l.aTan2() : l.reverse().aTan2();
		return maxPerpDistance(hull, l) < P.CON_TOL * 1 && Anglez.dist(hull.aTan2(), la) < Math.PI / 6;
		
	}
	
	public static double maxPerpDistance(Line a, Line b) {
		
		return Math.sqrt(
				MUtils.max (
					distanceSquared (b.start, a ),
					distanceSquared (b.end  , a ),
					distanceSquared (a.start, b ),
					distanceSquared (a.end  , b )
			) );
	}
	
	public static Point2d project(Line l, Point2d pt) {
		
		Vector2d v1 = new Vector2d(l.end);
		v1.sub(l.start);
		Vector2d v2 = new Vector2d(pt);
		v2.sub(l.start);
		double param = v2.dot(v1) / v1.length();

		if (param < 0 || param > v1.length())
			return null;

		v1.normalize();
		v1.scale(param);
		v1.add(l.start);

		return new Point2d(v1);
	}

	public static double distanceSquared(Point2d a, Line l) {
		
		Point2d b = project (l, a);
		
		if (a == null || b == null)
			return -Double.MAX_VALUE;
		return a.distanceSquared(b);
	}
	
	private void removeOverlaps(List<Problem> current) {

		Closer<Problem> closer = new Closer();

		for (Problem a : current) {

			try {
				Region<Euclidean2D> ar = a.chull.createRegion();

				b: for (Problem b : current)
					if (a != b)
						for (Vector2D v : b.chull.getVertices()) {
							Location vInA = ar.checkPoint(v);
							if (vInA == Location.BOUNDARY || vInA == Location.INSIDE) {

								closer.add(a, b);
								// remove from a,b

								continue b;
							}
						}
			} 
			catch ( InsufficientDataException    th) {}
			catch ( MathIllegalArgumentException th) {}
		}
		
		for (Set<Problem> close : closer.close()) {

			List<Problem> intersecting = new ArrayList<Problem>(close);

			Collections.sort(intersecting, (a, b) -> -Double.compare(a.area(), b.area()));

			for (int i = 1; i < intersecting.size(); i++) {
				Problem togo = intersecting.get(i);
				togo.addPortal();
				current.remove (togo);
			}
			
			
		}
	}
	
	private double signedArea(Collection<Line> set) {
		
		double 
			ax = set.stream().collect(Collectors.averagingDouble(l -> l.start.x)).doubleValue(),
			ay = set.stream().collect(Collectors.averagingDouble(l -> l.start.y)).doubleValue();
				
		Point2d cen = new Point2d(ax, ay);
		
		double c = set.stream().collect(Collectors.summingDouble(x -> MUtils.area(cen, x.end, x.start)) ).doubleValue();
		
//		System.out.println(set.size()+ " area " + c);
		
		return c;
	}
}