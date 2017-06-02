package org.twak.viewTrace;

import java.util.Iterator;
import java.util.Map;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.Line;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm;

public class CutHoles {
	
	public static Point3d find (Point2d pt, Map<Point2d, Point3d> root, Map<Point2d, Line> created ) {
		
		if ( root.containsKey(pt) )
			return root.get(pt);
		else
		{
			Line l = created.get(pt);
			Point3d a = find (l.start, root, created),
					b = find (l.end, root, created);
			
			return new Line3d(a, b).fromPPram (l.findPPram(pt));
		}
	}
	
	public static void cutHoles(LoopL<Point2d> out, double tol) {
		cutHoles (out, tol, null);
	}
	
	public static void cutHoles(LoopL<Point2d> out, double tol, Map<Point2d, Line> created ) {
		
		MultiMap<Boolean, Loop<Point2d>> holeToLoop = new MultiMap<>();
		
		Iterator<Loop<Point2d>> lit = out.iterator();
		
		while (lit.hasNext()) {
			
				Loop<Point2d> loop = lit.next(); // a hole can be a backwards loop...
			
				double area = Loopz.area(loop);
				
				if (Math.abs (area) < tol * tol)
					lit.remove();
				
				boolean isHole = area > 0;
				holeToLoop.put(isHole, loop);
				
				for (Loop<Point2d> h : loop.holes) { // ...or an explicit hole
					
					if (Loopz.area(h) > 0)
						h.reverse();
					
					holeToLoop.put(false, h);
				}
		}

		for (Loop<Point2d> hole : holeToLoop.get(false)) {
			
			Point2d origin = new Point2d (Double.MAX_VALUE, 0);
			Loopable<Point2d> originL = null;
			
			for (Loopable<Point2d> p : hole.loopableIterator() ) {
				if ( p.get().x < origin.x ) {
					originL = p;
					origin = originL.get();
				}
			}
			
			LinearForm ray = new LinearForm(0, 1);
			ray.findC(origin);
			double nearestD = Double.MAX_VALUE;
			Loopable<Point2d> nearestL = null;
			Point2d nearestH = null;
			
			for (Loop<Point2d> loop : out) {
				for (Loopable<Point2d> line : loop.loopableIterator()) {
					Point2d a = line.get(), b = line.getNext().get();
					if (
						a.y > origin.y && b.y < origin.y ||
						a.y < origin.y && b.y > origin.y ) {
						Point2d hit = new Line (a,b).intersects(ray);
						if (hit != null && hit.x < origin.x && (origin.x - hit.x < nearestD )) {
							nearestD = origin.x - hit.x;
							nearestL = line;
							nearestH = hit;
						}
					}
				}
			}
			
			
			if ( nearestH == null )
				System.err.println("failed to find outer ring for hole");
			else {
			
				if (created != null)
					created.put(nearestH, new Line (nearestL.get(), nearestL.getNext().get()));
				
				Loopable<Point2d> 
					hitPtF = new Loopable<Point2d>(nearestH),
					hitPtS = new Loopable<Point2d>(nearestH),
					originL2 = new Loopable(origin);
				
				hitPtS.setNext(nearestL.getNext());
				hitPtF.setPrev(nearestL);

				hitPtS.getNext().setPrev(hitPtS);
				hitPtF.getPrev().setNext(hitPtF);
				
				originL.getNext().setPrev(originL2);
				originL2.setNext(originL.getNext());
				
				originL.setNext(hitPtS);
				hitPtS.setPrev(originL);
				
				hitPtF.setNext(originL2);
				originL2.setPrev(hitPtF);
				
			}
			out.remove(hole);
		}
	}
}
