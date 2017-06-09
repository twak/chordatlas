package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.twak.utils.Line;
import org.twak.utils.Pair;
import org.twak.utils.collections.ConsecutiveItPairs;
import org.twak.utils.collections.MultiMapSet;
import org.twak.utils.geom.Anglez;
import org.twak.utils.geom.LinearForm;
import org.twak.viewTrace.RangeMerge.Result;

/**
 * Line-finder
 * 
 * @author twak
 *
 */
public class FindLines {

	public LineSoup result = new LineSoup();

	public Map<Line, Integer> cliques = new HashMap();
	
	SliceParameters P;
	GBias bias;
	
	public FindLines( LineSoup slice, GBias bias, int debugLine, Map<Line, Integer> sliceCliques, SliceParameters p ) {
		
		this.P = p;
		this.bias = bias;
		
//		PaintThing.debug.clear();
//		for (Line l : slice.all)
//			PaintThing.debug.put(sliceCliques.get(l), l);
		
		MultiMapSet<Integer, Line> cliques = new MultiMapSet<>();
		for ( Line l : slice.all ) {
			cliques.put ( sliceCliques == null ? 0:sliceCliques.get(l), l);
		}
		
//		Set <Line> remaining = new HashSet<>(slice.all);
		
		List<Line> out = new ArrayList();
		
		for (Integer c : cliques.keySet())
			forClique(cliques.get(c), out,   debugLine, c);
		
		result = new LineSoup(out);
	}
	
	public FindLines( Set<Line> lines, SliceParameters p ) {
		
		this.P = p;
		
		List<Line> out = new ArrayList();
		forClique(lines, out,   -1, -1);
		
		result = new LineSoup(out);
	}
	
	private void forClique( Set<Line> remaining, List<Line> out, int debugLine, int clique ) {
		
		Iterator<Line> filter = remaining.iterator();

		while (filter.hasNext()) {
			double l = filter.next().length();
			if (l == 0)
				filter.remove();
		}
		
		if ( remaining.isEmpty())
			return;
		
		int count = 0;
		
		while (!remaining.isEmpty()) {

			double angle = nextAngle(remaining, count); 
				
				Bin.Builder<Line> oBBin = new Bin.Builder<>(); // offset bin
				LinearForm lfPerp = new LinearForm(Math.cos(angle), Math.sin(angle) );

				for (Line l : remaining) {
					
					if ( Anglez.dist(l.aTan2(), angle) < Math.PI / 2 ) { // and line angle near angle+-180
						oBBin.add(lfPerp.findPParam(l.start), l.length(), l);
						oBBin.add(lfPerp.findPParam(l.end), l.length(), l);
					}
				}

				Bin<Line> oBin = oBBin.done(P.FL_BINS * 2, false ); // we're super sensitive to the number of orientation bins :(

				int oBinI = oBin.maxI();
				int[] oBinM = oBin.maxMode(1, 10);
				
				LinearForm lfDir = new LinearForm(lfPerp.y, -lfPerp.x);
				lfDir.findC( lfPerp.fromPParam(oBin.val(oBinI)) );
				
				RangeMerge<Line> rm = new RangeMerge( P.FL_GAP_TOL, P.FL_GAP_TOL / 2 );

				for (Line w : oBin.getThings(oBinI, oBinM)) {
					if (
						lfDir.distance(w.start) < getTolNearLine(w.start) && 
						lfDir.distance(w.end  ) < getTolNearLine(w.end  ) 
							&& Anglez.dist(w.aTan2(), angle) < Math.PI / 2.5 // and line angle near angle+-180
							) {
								rm.add(lfDir.findPParam(w.start), lfDir.findPParam(w.end), w);
							}
				}
				
				
				List<Result<Line>> merged = rm.getResults();
				
				if (merged.isEmpty()) 
				{
					for (Line w : oBin.getThings(oBinI, oBinM)) 
						remaining.remove(w);
				}
				else {
					
					double maxP = merged.stream().map( x -> x.max - x.min ).max( Double::compare ).get();
							
					for (Result<Line> r : merged) {

						if (r.max - r.min < 0.2 * maxP)
							continue;
						
						Line line = null;
						
						if (P.FL_REGRESS)
							line = regress(r.things, lfDir);
						
						if (line == null)
							line = new Line(lfDir, r.min, r.max);
						
						Iterator<Line> rit = remaining.iterator();
						boolean removed = false;
						
						Bin<Line> counts = null;
						if (P.MIN_LINES > 1)
							counts = new Bin<>( 0, 1, 50, false );
						
						while (rit.hasNext()) {  
							Line rine = rit.next();
							if (
									line.distance(rine.start, true) < getTolNearLine2( rine.start ) && 
									line.distance(rine.end  , true) < getTolNearLine2( rine.end   )
									&& Anglez.dist(line.aTan2(), rine.aTan2()) < getTolRemoveAngle (rine)
									)
								{
									if (counts != null)
										counts.addRange( line.findPPram( rine.start ), line.findPPram( rine.end ), 1, rine );
									removed = true;
									rit.remove();
								}
						}
						
					if ( counts != null ) {
						
//						for (Pair<Double, Double > d : new ConsecutiveItPairs<>( counts.getRegionAbove( P.MIN_LINES ) )) {
//							
//							Point2d start = line.fromPPram( counts.getFirst( P.MIN_LINES ) ), 
//									end   = line.fromPPram( counts.getLast( P.MIN_LINES ) );
//							
//							if ( start != null && end != null ) {
//								out.add( new Line(start, end) );
//							}
//						}
						
						Point2d start = line.fromPPram( counts.getFirst( P.MIN_LINES ) ), 
								end   = line.fromPPram( counts.getLast( P.MIN_LINES ) );

						if ( start != null && end != null ) {
							line.start = start;
							line.end = end;
						}
						else line = null;
					}
						
							
						if (!removed )
							for (Line l : r.things) {
								if (remaining.contains(l)) {
									remaining.remove(l);
								}
							}
						
					if ( line != null ) {
						if ( bias != null ) {
							Point2d cen = line.fromPPram( 0.5 );
							Double toGIS = bias.getAngle( line, cen );
							if ( toGIS != null )
								line = rotateToAngle( line, cen, toGIS );
						}

						if ( debugFilter( count, debugLine ) && line.start.distanceSquared( line.end ) > 0.001 ) {
							out.add( line );
							cliques.put( line, clique );
						}
					}
					}
				}
				
			count++;
		}
		
	}

	protected double getTolRemoveAngle( Line rine ) {
		return Math.PI / 2;
	}

	protected double getTolNearLine( Point2d start ) {
		return P.FL_NEAR_LINE;
	}
	
	protected double getTolNearLine2( Point2d start ) {
		return P.FL_NEAR_LINE_2;
	}

	private Line regress(Set<Line> things, LinearForm lfDir) {
		SimpleRegression fit = new SimpleRegression();

		// regression isn't happy on lines with infinite slope: so swap params!
		boolean flip = Math.abs ( lfDir.x ) > Math.abs (lfDir.y);
		
		for (Line l : things) 
			if (flip) {
				fit.addData(l.start.y, l.start.x);
				fit.addData(l.end.y, l.end.x);
			}
			else {
				fit.addData(l.start.x, l.start.y);
				fit.addData(l.end.x, l.end.y);
			}

		double intercept = fit.getIntercept(), slope = fit.getSlope();
		
		if (Double.isNaN(intercept))
			return null;
		
		LinearForm lf;
		if (flip)
			 lf = new LinearForm(  1, -slope );
		else
			 lf = new LinearForm( -slope, 1 );
		
		
		if ( lf.unitVector().angle(lfDir.unitVector())> Math.PI / 2) {
			lf.x = -lf.x; // if regression is pointing wrong way, flip
			lf.y = -lf.y;
		}
		
		if (flip)
			lf.findC( intercept, 0 );
		else
			lf.findC( 0, intercept );

		double[] minMax = things.stream()
				.map(x -> new double[] { lf.findPParam(x.start), lf.findPParam(x.end) })
				.collect(new InAxDoubleArray());

//		Line line = new Line(lfDir, r.min, r.max);
		return new Line(lf, minMax[0], minMax[1]);  // do regression
	}

	private boolean debugFilter(int count, int debugLine) {
		return debugLine == -1 || count == debugLine;
	}

//	private double findNextAngle2(GBias bias, Set<Line> remaining, Bin directionBias, double remainingLength) {
//		
//		Bin aBin = buildNormalized(remaining, remainingLength);
//		
//		aBin.multiply(directionBias, 0.5);
//		
//		int aBinI = aBin.maxI();
//		return aBin.val(aBinI);
//		
//	}

//	private Bin buildNormalized(Set<Line> remaining, double length) {
//		double delta = Math.PI / P.FL_BINS;
//		Bin<Line> aBin = new Bin(-Math.PI - delta, Math.PI + delta, P.FL_BINS, true); // angle bin
//		
//		for (Line l : remaining) {
//			double len = l.length();
//			aBin.add( l.aTan2(), len, l );
//		}
//		
//		aBin.multiply(1/length);
//		return aBin;
//	}

	private double findNextAngle(Set<Line> remaining) {
			
		double delta = Math.PI / P.FL_BINS;
		Bin<Line> aBin = new Bin(- Math.PI - delta, Math.PI + delta, P.FL_BINS * 2, true); // angle bin
		
		for (Line l : remaining) {
			double len = l.length();
			double angle = l.aTan2();
			aBin.add( angle, len, l );
		}
		
		int aBinI = aBin.maxI();
		return aBin.val(aBinI);
	}
	
	protected double nextAngle(Set<Line> remaining, int iteration) {

//		if (iteration < bias.snapAngles.size())
//			return bias.snapAngles.get(iteration);
//		else
			return findNextAngle(remaining);
	}

	public static Line rotateToAngle(Line nLine, Point2d cen, double angle) {
		
		double len = nLine.length() / 2;
		
		Vector2d dir = new Vector2d( -Math.cos(angle) * len, -Math.sin(angle) * len );
		
		Point2d start = new Point2d(cen), end = new Point2d(cen);
		start.add(dir);
		end.sub(dir);
				
		return new Line ( start, end );
		
	}

//	public double adjacentDist(Line l, Point2d pt) {
//
//		Vector2d v1 = new Vector2d(l.end);
//		v1.sub(l.start);
//		Vector2d v2 = new Vector2d(pt);
//		v2.sub(l.start);
//		double param = v2.dot(v1) / v1.length();
//
//		if ( param < 0 || param > v1.length() )
//			return Double.MAX_VALUE;
//		
//		v1.normalize();
//		v1.scale( param );
//		v1.add( l.start );
//		
//		return new Point2d (v1).distance(pt);
//	}
}
