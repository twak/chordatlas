package org.twak.viewTrace.facades;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.utils.Cache2;
import org.twak.utils.DumbCluster1D;
import org.twak.utils.DumbCluster1D.Cluster;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.collections.CountThings;
import org.twak.utils.collections.MapMapList;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.collections.Streamz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.DRectangle.Bounds;
import org.twak.utils.streams.InAxDouble;
import org.twak.utils.ui.Colourz;
import org.twak.utils.DumbCluster1DImpl;
import org.twak.utils.Mathz;
import org.twak.utils.Pair;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class Regularizer {

	double lp = Double.MAX_VALUE, rp = Double.MAX_VALUE;
	
	double lt = Double.MAX_VALUE, rt = Double.MAX_VALUE;
	
	int ids = 0;
	
	final static Feature[] toReg = new Feature[] { Feature.WINDOW, Feature.DOOR, Feature.SHOP, Feature.MOULDING, Feature.GRID };
	
	MapMapList<MiniFacade, Integer, FRect> m2i2r = new MapMapList<>(); 
	
	double targetWidth = -1;
	
	public static int miniFacadesUsed = 0, regularised = 0, totalFeature = 0;
	
	public static Set<File> seenImages = new HashSet<>();
	
	public MiniFacade go (List<MiniFacade> in, double targetS, double targetE, MegaFeatures wantsFacade ) {
		
		this.lt = targetS;
		this.rt = targetE;
		
		return goDebug (in, 1, wantsFacade).get(0);
		
	}
	
	public List<MiniFacade> goDebug (List<MiniFacade> in, double targetS, double targetE, double debugFrac ) {
		
		this.lt = targetS;
		this.rt = targetE;
		
		return goDebug(in, debugFrac, null);
		
	}
	
	public List<MiniFacade> goDebug( List<MiniFacade> in, double debugFrac, MegaFeatures megaFeatures ) {
		
		miniFacadesUsed += in.size();
		regularised++;
		for (MiniFacade mf : in) 
			if (mf.imageFeatures != null)
				seenImages.add ( mf.imageFeatures.ortho );
		
		if (in.isEmpty())
			return Collections.singletonList( gridMini() );
		
		System.out.println("starting to regularize "  + in.size() +" facades...");
		in = augmentWithTween( in, megaFeatures );
		System.out.println(" adding tween for " + in.size() + " facades");
		
		double alpha = 0.2;
		
		List<MiniFacade> out;
		
		if (true)
			out = newFromWindows( in );
		else 
			out = in.stream().map( mf -> new MiniFacade( mf  ) ).collect( Collectors.toList() );
			
		System.out.println(" included grids for " + out.size() + " facades...");

		if (debugFrac < 0) {
			out.add(0, new MiniFacade() );
			return out;
		}
		
		alignMFs (out);
		
		if (debugFrac == 0) {
			out.add(0, new MiniFacade() );
			return out;
		}
		
		for (MiniFacade mf : out) {
			assignFeaturesToWindows( mf.featureGen.getRects( Feature.WINDOW, Feature.SHOP).stream().
					collect( Collectors.toList() ), mf.featureGen );
			
			mf.featureGen.get( Feature.CORNICE ).clear();
			mf.featureGen.get( Feature.BALCONY ).clear();
			mf.featureGen.get( Feature.SILL    ).clear();
			mf.featureGen.get( Feature.GRID    ).clear();
		}
		
		
		for (MiniFacade mf : out) {
			mergeRemoveSmall(mf);
			mergeRemoveSimilar(mf);
		}
		
		for (Feature f : Feature.values())
			for (MiniFacade mf : out)
				mf.featureGen.get( f ).stream().forEach( r -> r.f = f );
		
		
		for (int i = 0; i < 50 * debugFrac; i++) {
			
			for (MiniFacade mf : out)
				for (Feature f : toReg) 
					cluster1 ( mf.featureGen.getRects(f), 1,  alpha, Bounds.XMIN, Bounds.XMAX, Bounds.YMIN, Bounds.YMAX);
			
//			for (MiniFacade mf : out)
//					cluster1 ( mf.getRects(toReg), 1,  alpha, Bounds.XMIN, Bounds.XMAX, Bounds.YMIN, Bounds.YMAX);
			
			
			List<FRect> allRects = new ArrayList<>();
			
			for (MiniFacade mf : out)
				for (Feature f : toReg)
					allRects.addAll( mf.featureGen.get(f) );
			
			for (Feature f : toReg) {
				List<FRect> allF = out.stream().flatMap( mf -> mf.featureGen.get( f ).stream() ).collect( Collectors.toList() );
				cluster1 ( allF, 0.5, alpha, Bounds.WIDTH, Bounds.HEIGHT);
			}
			
			cluster1 ( allRects, 0.3,  alpha, Bounds.XMIN, Bounds.XMAX, Bounds.YMIN, Bounds.YMAX);
			
			if (i % 5 == 0)
				for (MiniFacade mf : out) 
					findNeighbours(  mf.featureGen.getRects(Feature.WINDOW ) );
			
			for (Dir dir : Dir.values())
				clusterDeltas(allRects, 0.2, alpha, dir );
			
			for ( MiniFacade mf : out ) { 
				
				for (FRect d :  mf.featureGen.get(Feature.DOOR))
					constrainDoor (mf, d, alpha);
				for (FRect m :  mf.featureGen.get(Feature.MOULDING))
					constrainMoulding( mf, m, alpha);
			}
			
			for ( MiniFacade mf : out ) {

				for ( Feature f : toReg ) {
					Iterator<FRect> rit = mf.featureGen.get( f ).iterator();

					while ( rit.hasNext() ) {
						FRect r = rit.next();
						if ( r.width <= 0.4 || r.height <= 0.4 )
							rit.remove();
						else
							for ( FRect n : r.adjacent )
								if ( n != null && similar( n, r ) ) {
									n.attached.putAll (r.attached);
									rit.remove();
									break;
								}
					}
				}
			}
		}

		
		if (debugFrac < 1) {
			out.add(0, new MiniFacade());
			return out;
		}
		
		for ( MiniFacade mf : out ) {
			findOuters( mf );
			mergeRemoveSmall( mf );
		}
		
		
		ids = 0;
		
		for ( Feature f : toReg )  // find ids...starting with the biggest
		{
			List<Pair<MiniFacade, FRect>> allRects = new ArrayList<>();
		
			for (MiniFacade mf : out)
				for (FRect r : mf.featureGen.get(f))
					allRects.add(new Pair<>(mf, r));
			
			Collections.sort(allRects, new Comparator<Pair<MiniFacade, FRect>>() {

				@Override
				public int compare( Pair<MiniFacade, FRect> a_, Pair<MiniFacade, FRect> b_ ) {
					
					FRect a = a_.second(), b = b_.second();
					
					if (a.gridCoords != null && b.gridCoords == null)
						return -1;
					if (b.gridCoords != null && a.gridCoords == null)
						return  1;
					if (a.gridCoords != null && b.gridCoords != null) { 
						int out = -Integer.compare( countCoords(a.gridCoords), countCoords(b.gridCoords) );
						if (out != 0)
							return out;
					}
					
					return Double.compare( a.area(), b.area() );
				}

				private int countCoords( int[] gridCoords ) {

					int out = 0;
					for (int i : gridCoords)
						if ( i != -Integer.MAX_VALUE)
							out++;
					
					return out;
				}
			});
		
			for (Pair<MiniFacade, FRect> pair : allRects) {
				FRect w = pair.second();
				if ( w.id == -1 )
					findId( w, f, out, pair.first(), ids++ );
			}
		}
		
		out.add(0, combine(out));
		
		totalFeature += out.get( 0 ).featureGen.getRects( Feature.values() ).size();
		
		System.out.println("done");
		

		
		return out;
	}

	private MiniFacade gridMini() {
		
		MiniFacade out = new MiniFacade();
		
		out.left = lt; out.width = rt - lt;
		out.height = 15;
		
		double sx = Math.random() * 0.3 + 0.5, gx = 1;  
		double sy = Math.random() * 0.2 + 1, gy = 1;  
		
		double xpad = 1;
		double ypad = 2;
		
		int xcount =  (int) ( ( out.width - (xpad * 2)- sx ) / (sx + gx) ) + 1;
		int ycount = (int ) (( out.height - (ypad * 2) - sy) / (sy + gy) ) + 1; 
		
		sx = (out.width  - 2 * xpad + gx) / xcount -1;
		sy = (out.height - 2 * ypad + gy) / ycount -1;
		
		for (int x = 0; x < xcount; x++)
			for (int y = 0; y < ycount; y++)
			{
				FRect win = new FRect( 
						lt + xpad + x * ( sx + gx),
						ypad + y * ( sy + gy),
						sx,
						sy );
				
				win.f = Feature.WINDOW;
				out.featureGen.put( win.f, win );
				
			}
		
		out.groundFloorHeight = ypad + sy + gy/2;
		out.app.color = Color.red;//new double[] { 1,0,0, 1.0f };
//		out.color = new double[] { 200 / 255f, 180 / 255f, 170 / 255f, 1.0f };
//		out.groundColor = new double[] { 180 / 255f, 150 / 255f, 140 / 255f, 1.0f };
		
		return out;
	}

	private List<MiniFacade> newFromWindows( List<MiniFacade> in ) {
		
		List<MiniFacade> out = new ArrayList();
		
		for ( MiniFacade old : in )
			if ( old != null ) {
				MiniFacade nmf = new MiniFacade( old );
				out.add( nmf );

				if (in.size() < 20)
				{
					MiniFacade gridMF = new MiniFacade( nmf );

					gridMF.featureGen.get(Feature.GRID).clear();
					gridMF.featureGen.get(Feature.WINDOW).clear();

					for ( FRect f : nmf.featureGen.get( Feature.GRID ) ) {
						f.f = Feature.WINDOW;
						gridMF.featureGen.put( f.f, f );
					}

					out.add( gridMF );
				}
			}
		return out;
	}

	private List<MiniFacade> augmentWithTween( List<MiniFacade> in, MegaFeatures megaFeatures ) {
		
		List<MiniFacade> out = new ArrayList<>( in );
		
		if (megaFeatures == null)
			return out;
		
		if (in.isEmpty()) {
			
			for (ImageFeatures iff : megaFeatures.features) 
				for (MiniFacade mf : iff.miniFacades) 
					if ( lt - 3 < mf.left  && mf.right() < rt + 3)
						out.add( mf );
			
			return out;
		}

		MegaFeatures mf = in.get( 0 ).imageFeatures.mega;

		double[] range = in.stream().flatMap( m -> Streamz.stream( m.left, m.left + m.width ) ).collect( new InAxDouble() );

		for ( ImageFeatures imf : mf.features )
			for ( MiniFacade mf2 : imf.miniFacades )
				if ( mf2.left > range[ 0 ] && mf2.left + mf2.width < range[ 1 ] && !out.contains( mf2 ) )
					out.add( mf2 );

		return out;
	}

	private class ArrayCache2 extends Cache2<Outer, Integer, List<FRect>> {
				@Override
				public List<FRect> create( Outer i1, Integer i2 ) {
					return new ArrayList();
				}
			};
	
	private MiniFacade combine( List<MiniFacade> in ) {
		MiniFacade out = new MiniFacade();
		
		out.left = lp;
		out.width = rp - lp;
		out.imageFeatures = in.get( 0 ).imageFeatures;
		
		double[] color = new double[] {0,0,0,1}; 
//		out.groundColor = new double[] {0,0,0,1};
		
		int gcc = 0;
		for (MiniFacade mf : in) 
			for (int i = 0; i < 3; i++) {
				color[i] += color[i];
//				if (mf.groundColor != null) {
//					out.groundColor[i] += mf.groundColor[i];
//					gcc++;
//				}
			}
		
		for (int i = 0; i < 3; i++) {
			color[i] /= in.size();
//			if (gcc > 0)
//				out.groundColor[i] /= gcc;
		}
		
		out.app.color = Colourz.to4( color );
		
		
		
		
		Cache2<Outer, Integer, List<FRect>> corniceX = new ArrayCache2();
		Cache2<Outer, Integer, List<FRect>> sillX = new ArrayCache2();
		Cache2<Outer, Integer, List<FRect>> balX = new ArrayCache2();

		out.height = in.stream().mapToDouble( mf -> mf.height ).average().getAsDouble();
		out.groundFloorHeight = in.stream().mapToDouble( mf -> mf.groundFloorHeight ).average().getAsDouble();
		
		for (int i = 0; i < ids; i++) {
			
			int yay = 0, nay = 0;
			
			int ii = i;
			
			List<FRect> found = in.stream()
					.map( mf -> m2i2r.get( mf, ii ) )
					.filter (x -> x != null)
					.flatMap(l -> l.stream())
					.collect( Collectors.toList() );
			
			Point2d avg = found.stream().map( r -> r.getCenter() ).collect( new Point2DMeanCollector() );
			
			for (MiniFacade mf : in) {
				
				List<FRect> r = m2i2r.get( mf, i );
				
				if (  mf.contains( avg ) ) {
					if ( r.isEmpty() ) {
						
						if (mf.left + 3 < avg.x && mf.left + mf.width -3 > avg.x)
							nay++;
					}
					else
						yay++;
				}
			}
			
//			System.out.println(yay +"/" + nay +" " + i);
			
			if (yay >= nay)
			{ // if we believe it exists add it as average of observed sizes
				
				FRect o;
				
				if ( dimensionSpread( found ) > 1.4 ) { // scattered -> union (typically shop windows)  
					o = new FRect( found.get( 0 ) );

					for ( FRect n : found )
						o.setFrom( o.union( n ) );
					
				} else // about same size: average position: windows on a grid
					o = new FRect( average( found.toArray( new FRect[found.size()] ) ) );
				
				{
					FRect t = found.get(0);
					o.f = t.f;
					o.id = i;
					o.outer = null;
					
					o.attachedHeight.get( Feature.SILL ).d    = averageAttached (o, Feature.SILL, found);
					o.attachedHeight.get( Feature.CORNICE ).d = averageAttached (o, Feature.CORNICE, found);
					o.attachedHeight.get( Feature.BALCONY ).d = averageAttached (o, Feature.BALCONY, found);

					if ( t.f == Feature.WINDOW || t.f == Feature.SHOP ) {
						for ( FRect r : found ) {
							corniceX.get( r.outer, r.yi ).add( o );
							sillX   .get( r.outer, r.yi ).add( o );
							balX    .get( r.outer, r.yi ).add( o );
						}
					}
				}
				
				out.featureGen.put( o.f, o );
			}
		}
		
		spreadAttachedOverGrid( Feature.SILL, sillX );
		spreadAttachedOverGrid( Feature.CORNICE, corniceX );
		spreadAttachedOverGrid( Feature.BALCONY, balX );
		
		fixOverlaps (out);
		mergeRemoveSmall( out );
		
		DRectangle mr = out.getAsRect();
		mr.width -= 0.2; // ensure everything is comfortably within the bounds
		mr.x += 0.1; 
		
		for ( Feature f : Feature.values() ) { // clip to all
			Iterator<FRect> rit = out.featureGen.get( f ).iterator();
			while ( rit.hasNext() ) {
				FRect r = rit.next();
				DRectangle section = r.intersect( mr );
				if ( section == null || section.area() < 0.5 )
					rit.remove();
				else
					r.setFrom( section );
			}
		}
		
		{ // door height
			Double hf = Double.valueOf( 0 );
			while ( out.groundFloorHeight < 6 && ( ( hf = horizontalEmpty( out, out.groundFloorHeight ) ) != null ) )
				out.groundFloorHeight = hf + 0.3;

			if ( out.groundFloorHeight >= 6 )
				out.groundFloorHeight = 0; // no ground floor!
		}

		return out;
	}

	private Double horizontalEmpty( MiniFacade mf, double doorHeight ) {
		
		Double out = null;
		
		for (FRect f : mf.featureGen.getRects()) {
		
			if (f.getMaxY() > doorHeight && f.y < doorHeight) 
				if (out == null)
					out = f.getMaxY();
				else
					out = Math.max (out, f.getMaxY());
		}
		
		return out;
	}

	private void spreadAttachedOverGrid( Feature cornice, Cache2<Outer, Integer, List<FRect>> corniceX ) {
		for (Outer o : corniceX.cache.keySet()) {
			
			if (o == null)
				continue;
			
			for (Integer i : corniceX.cache.get(o).keySet()) {
				
				double avgHeight = 0;
				int hits = 0, miss = 0;; 
				
				for (FRect f : corniceX.get( o, i )) {
					if (f.attachedHeight.get( cornice ).d > 0) {
						avgHeight += f.attachedHeight.get(cornice).d;
						hits ++;
					} else
						miss ++;
				}
				
				
				double avg = avgHeight / hits;
				
				if (hits > miss) 
					corniceX.get( o, i ).stream().forEach( w -> w.attachedHeight.cache.get( cornice ).d = avg );
				else
					corniceX.get( o, i ).stream().filter(w -> w.attachedHeight.cache.get( cornice ).d > 0).
						forEach( w -> w.attachedHeight.cache.get( cornice ).d = 0 );
			}
		}
	}

	private double averageAttached( FRect o, Feature sillF, List<FRect> found ) {
		
		int count = 0;
		double total = 0;
		for (FRect f : found) {
			
			DRectangle bounds = union( f.attached.get( sillF ) );
			
			if (bounds != null) {
				total += Math.abs ( bounds.getMaxX() - f.getMaxX() );
				count++;
			}
		}
		
		return count == 0? 0 : Mathz.clamp( total / count, 0.2, o.height/2);
	}

	private double dimensionSpread( List<FRect> found ) {
		
		DRectangle tmp = new DRectangle( found.get( 0 ) );
		
		double width = 0, height = 0;
		
		for (DRectangle d : found) {
			width += d.width;
			height += d.height;
			tmp.setFrom( d.union( tmp ) );
		}
		
		width  /= found.size();
		height /= found.size();
		
		return Math. min ( Math.abs( width - tmp.width ) / width, Math.abs ( height - tmp.height ) / height );
	}

	Feature[] forceNoOverlaps = new Feature[] {Feature.WINDOW, Feature.SHOP, Feature.DOOR };
	
	private void fixOverlaps( MiniFacade out ) {
		
		
		Set<FRect> toProcess = new LinkedHashSet<>(out.featureGen.getRects( forceNoOverlaps ));
		
		while (!toProcess.isEmpty()) {
			
			FRect r1 = toProcess.iterator().next();
			toProcess.remove( r1 );
			Set<FRect> togo = new LinkedHashSet<>();
			
			Set<FRect> toadd = new LinkedHashSet<>();
			
			for (FRect r2 : out.featureGen.getRects( forceNoOverlaps )) {
				
				if (r1 == r2 || togo.contains( r2 ))
					continue;
				
				if (r1.intersects( r2 )) {
					
					if (r1.f == r2.f) { // we've already tried and failed to merge same-feature rects in mergeRemoveSmall
						FRect tg = r1.area() < r2.area() ? r1 : r2;
						togo.add ( tg );
						toProcess.remove( tg );
						continue;
					}
					
					FRect infront = r1.f.ordinal() < r2.f.ordinal() ? r1 : r2, 
						  behind  = infront == r1 ? r2 : r1;
				
					togo.add(behind);
					toProcess.remove(behind);

					List<FRect> r = behind.fractureV (infront);
					
					for (FRect t : r)
						if ( t.area() > 0.1 ) {
							toProcess.add( t );
							toadd.add( t );
						}
				}
			}
			
			for (FRect a : toadd)
				out.featureGen.put( a.f, a );
			
			for (FRect a : togo)
				out.featureGen.remove( a.f, a );
		}
	}

	private void alignMFs( List<MiniFacade> out ) {
		
		boolean hasGeometry = lt != Double.MAX_VALUE && rt != Double.MAX_VALUE;

		if ( !hasGeometry ) {
			double[] lr = alignMFs( out, false );
			if ( lr == null )
				lr = alignMFs( out, true );
			lp = lr[ 0 ];
			rp = lr[ 1 ];

			out.stream().forEach( mf -> mf.scaleX( lp, rp ) );

			return;
		}

		double tol =3;

		double[] lr = alignMFs( out, false );
		if ( lr != null && Math.abs( ( lr[ 1 ] - lr[ 0 ] ) - ( rt - lt ) ) < 2 * tol ) {
			lp = lr[ 0 ];
			rp = lr[ 1 ];
			out.stream().forEach(  mf -> mf.scaleX( lp, rp ) );
		} else {

			List<Double> lefts = new ArrayList<>(), rights = new ArrayList<>();
			for ( MiniFacade mf : out ) {

				double mfl = mf.left, mfr = mf.left + mf.width;

				if ( mfl - lt < tol ) {
					lefts.add( mfl );
					if ( !mf.softLeft )
						lefts.add( mfl );
				}

				if ( rt - mfr < tol ) {
					rights.add( mfr );
					if ( !mf.softRight )
						rights.add( mfr );
				}
			}

			OptionalDouble la = Streamz.dStream( lefts ).average(), ra = Streamz.dStream( rights ).average();

			if ( la.isPresent() )
				lp = la.getAsDouble();
			else
				lp = lt;

			if ( ra.isPresent() )
				rp = ra.getAsDouble();
			else
				rp = rt;
		}

		double avgShift = 0;
		int avgCount = 0;
		List<MiniFacade> middle = new ArrayList<>();

		for ( MiniFacade mf : out ) {

			double oCen = mf.center();

			double dLeft = Math.abs( mf.left() - lp );
			double dRight = Math.abs( mf.right() - rp );
			if ( dLeft < tol && dRight < tol ) {
				mf.scaleX2( lp, rp );
				avgShift += mf.center() - oCen;
				avgCount++;
			} else if (  !mf.softLeft && dLeft < 2 * tol || mf.softLeft && dLeft < tol ) {
				mf.scaleX2( lp, lp + mf.width );
				avgShift += mf.center() - oCen;
				avgCount++;
			} else if ( !mf.softRight && dRight < 2 * tol || mf.softRight && dRight < tol ) {
				mf.scaleX2( rp - mf.width, rp );
				avgShift += mf.center() - oCen;
				avgCount++;
			} else
				middle.add( mf );
		}

		if ( avgCount != 0 ) {
			avgShift /= avgCount;
			for ( MiniFacade mf : middle )
				mf.scaleX2( mf.left() + avgShift, mf.right() + avgShift );
		}
	}
	

	private double[] alignMFs( List<MiniFacade> out, boolean onlyOneHard ) {
		
		OptionalDouble left = out.stream().filter( m -> !m.softLeft && (onlyOneHard || !m.softRight)).mapToDouble( m -> m.left ).average();
		if ( left.isPresent() )
			lp = left.getAsDouble();
		else if (!onlyOneHard)
			return null;
		else
			lp = out.stream().mapToDouble( m -> m.left ).min().getAsDouble();

		OptionalDouble right = out.stream().filter( m -> !m.softRight && (onlyOneHard || !m.softLeft) ).mapToDouble( m -> m.left + m.width ).average();
		if ( right.isPresent() )
			rp = right.getAsDouble();
		else if (!onlyOneHard)
			return null;
		else
			rp = out.stream().mapToDouble( m -> m.left + m.width ).max().getAsDouble();

		return new double[] {lp, rp};
	}


	private void findId( FRect w1, Feature feat, List<MiniFacade> all, MiniFacade in, int id ) {
		
		w1.id = id;
		m2i2r.put( in, id, w1 );
		
		for (MiniFacade mf : all) {
			
			if (mf == in)
				continue;
		
			FRect bestW = null;
			double bestScore = 1;
			
			for ( FRect w2 : mf.featureGen.get(feat) ) {
				
				if (w2.id != -1)
					continue;
				
				double score = distance (w1, w2);
				
				if (score < bestScore) {
					bestScore = score;
					bestW = w2;
				}
			}
			
			if (bestW != null) {
				
				bestW.id = id;
				m2i2r.put( mf, id, bestW );
			}
		}
	}
	
	private double distance( FRect w1, FRect w2 ) {
		
		if ( w1.gridCoords != null && w2.gridCoords != null ) {
			
			// if there's a really close match...just use it...
			double vDist = Math.abs( w1.x - w2.x ) + Math.abs( w1.y - w2.y ) + Math.abs( w1.getMaxX() - w2.getMaxX() ) + Math.abs( w1.getMaxY() - w2.getMaxY() ); 
			if (vDist < 0.4)
				return 0;
			
			List<Integer> 
					p1 = new ArrayList<>(), 
					p2 = new ArrayList<>();
			
			for (int i = 0; i < 4; i++) {
				if (w1.gridCoords[i] > -Integer.MAX_VALUE && w2.gridCoords[i] > -Integer.MAX_VALUE) {
					p1.add(w1.gridCoords[i]);
					p2.add(w2.gridCoords[i]);
				}
			}
			
			if (p1.size() >= 3) { 
				double score = Mathz.L2 ( Arrayz.toIntArray( p1 ) , Arrayz.toIntArray( p2 ) ) * 0.4 +
						( w1.getCenter().distance( w2.getCenter() ) ) * 2 / (w1.width + w2.width);
				if (score < 1)
					return score;
			}
		}
		
		return ( w1.getCenter().distance( w2.getCenter() ) ) * 4 / (w1.width + w2.width);
		
	}

	private void assignFeaturesToWindows(List<FRect> windows, MultiMap<Feature, FRect> rects) {
		
		int count = 0;
		for ( Feature f : new Feature[] { Feature.CORNICE, Feature.SILL, Feature.BALCONY } ) {
			for ( FRect r : rects.get( f ) ) {
				
				for (FRect w : windows) {
					
					DRectangle bounds = new DRectangle(w);
					bounds.height = bounds.height * 0.5;
					
					switch ( f ) {
					case SILL:
						bounds.y = bounds.x - bounds.height;
						break;
					case BALCONY:
						bounds.y = bounds.x;
						break;
					case CORNICE:
						bounds.y = bounds.getMaxY();
						break;
					default:
						break;
					}
					
					if (r.intersects( w )) {
						w.attached.put( f, r );
						count++;
					}
				}
				
				FRect win = nearest( windows, r, 2 );
				if ( win != null )
					win.attached.put( f, r );
			}
		}
		
//		System.out.println("atatched " + count +" cornicesesese");
	}

	private FRect nearest( List<FRect> windows, FRect sill, double maxDist ) {
		
		FRect bestWin = null;
		double bestDist = 1;
		
		for (FRect w : windows) {
			
			double dist = w.intersects( sill ) ? 0 :  w.getCenter().distance( sill.getCenter() );
			if (dist < bestDist) {
				bestDist = dist;
				bestWin = w;
			}
		}
		
		return bestWin;
	}

	private void findNeighbours( List<FRect> list ) {

		for ( FRect a : list )
			for ( Dir d : Dir.values() ) {

				FRect best = null;
				double bestDist = Double.MAX_VALUE;
				Vector2d wDir = d.dir();
				
				for ( FRect b : list ) {
					
					if ( b == a )
						continue;

					Vector2d delta = new Vector2d(b.getCenter());
					delta.sub (a.getCenter());
					
					if (wDir.angle( delta ) > Mathz.PI6 )
						continue;
					
					double dist = a.getCenter().distanceSquared( b.getCenter() );

					if ( dist < bestDist ) {
						bestDist = dist;
						best = b;
					}
				}

				a.setAdj( d, best );
			}
	}
	
	private void cluster1 (List<FRect> rects, double tol, double alpha, Bounds...axis) {
		
		class Wrapper {
			
			FRect rect;
			
			public Wrapper(FRect rect) {
				this.rect = rect;
			}
			
			public void set( double neu, Bounds b ) {
				double old = rect.get(b);
				rect.set(b, neu * alpha + old * (1-alpha));
			}
		}
		
		if (rects.isEmpty())
			return;
		
		for ( Bounds b : axis ) {

			List<Wrapper> toCluster = new ArrayList<>();

			for ( FRect r : rects )
				toCluster.add( new Wrapper( r ) );

			DumbCluster1D<Wrapper> clusterer = new DumbCluster1D<Wrapper>( tol, toCluster ) {
				@Override
				public double toDouble( Wrapper e ) {
					return e.rect.get( b );
				}
			};

			for ( DumbCluster1D.Cluster<Wrapper> e : clusterer )
				for ( Wrapper w : e.things )
					w.set( e.mean, b );
		}
	}
		

	private void clusterDeltas( List<FRect> rects, double tol, double alpha, Dir dir ) {
		
		if (rects.isEmpty())
			return;
		
		List<FRect> toCluster = new ArrayList<>();
		
		for ( FRect r : rects ) {

			FRect da = r.getAdj( dir );

			if ( da != null && da.getAdj( dir.opposite ) == r ) // only strong adjacencies
				toCluster.add( r );
		}
			
		DumbCluster1D<FRect> clusterer = new DumbCluster1D<FRect>( tol, toCluster ) {
			@Override
			public double toDouble( FRect rect ) {
				return rect.distanceToAdjacent( dir );
			}
		};
		
		Bounds b = (dir == Dir.L || dir == Dir.R) ? Bounds.XCEN : Bounds.YCEN;
		int moveDirection = dir == Dir.L || dir == Dir.D ? 1 : -1;
		
		Map<FRect, Double> desiredSpacings = new HashMap<>();
		
		for ( DumbCluster1D.Cluster<FRect> e : clusterer ) {
			for ( FRect rect : e.things )
				desiredSpacings.put ( rect, e.mean );
		}
		
		Collections.sort( rects, DRectangle.comparator( b, dir == Dir.L || dir == Dir.D ? false : true ) );
		
		for (FRect r : rects) {
			
			Double spacing = desiredSpacings.get(r);
			if (spacing == null)
				continue;
			
			DRectangle adj = r.getAdj( dir );
			
			adj.set( b, adj.get(b) - ( spacing - r.distanceToAdjacent( dir ) ) * alpha * moveDirection );
		}
	}

	private void findOuters( MiniFacade mf ) {
		
		mf.outers.clear();
		
		for (Feature f : Feature.values()) {
			
			if (f != Feature.WINDOW)
				continue;
			
			Set<FRect> togo = new LinkedHashSet( mf.featureGen.get( f ) );
			
			while (!togo.isEmpty()) {
				
				AOuter outer = new AOuter(f);
				
				FRect first = findBest ( togo );
				
				Set<FRect> neighbours = new TreeSet<>( FRect.comparatorArea(false) );
				neighbours.add(first);
				
				while (!neighbours.isEmpty()) {
					
					FRect n = neighbours.iterator().next();
					
					if (!togo.contains (n)){
						neighbours.remove( n );
						continue;
					}
					
					if ( outer.easyAdd( n )) { 
						
						neighbours.remove( n );
						add( togo, neighbours, n );
					}
					else {
						
						n = findBest( neighbours );
						
						neighbours.remove( n );
						
						if ( outer.extend( n ) ) 
							add( togo, neighbours, n );
					}
				}
				
				if (outer.building.findPositions().values().size() >= 2)
				{
					outer.done();
					mf.outers.add(outer);
					
					// re-ground the grid coordinate system
					CountThings<Integer> xes = new CountThings<>(), yes = new CountThings<>();
					for ( int x = 0; x < outer.elements.length; x++ )
						for ( int y = 0; y < outer.elements[ 0 ].length; y++ )
							if ( outer.elements[ x ][ y ] != null ) {
								xes.count( x );
								yes.count( y );
							}
					
					double 
						halfAvgX = 0.5 * xes.getSize() / xes.counts.cache.keySet().size(),
						halfAvgY = 0.5 * yes.getSize() / yes.counts.cache.keySet().size();
					
					List<Integer> rows = new ArrayList(xes.counts.cache.keySet());
					Collections.sort( rows );
					int xMin = rows.stream().filter( row -> xes.total(row) > halfAvgX ).findFirst().get();
					Collections.reverse( rows );
					int xMax = rows.stream().filter( row -> xes.total(row) > halfAvgX ).findFirst().get();
					
					List<Integer> cols = new ArrayList(yes.counts.cache.keySet());
					int yMin = cols.stream().filter( col -> yes.total(col) > halfAvgY ).findFirst().get();
					Collections.reverse( cols);
					int yMax = cols.stream().filter( col -> yes.total(col) > halfAvgY ).findFirst().get();
					
					for ( int x = 0; x < outer.elements.length; x++ )
						for ( int y = 0; y < outer.elements[ 0 ].length; y++ ) {
							
							FRect w = outer.elements[x][y];
							
							if ( w == null ) 
								continue;
							
							w.gridCoords = new int[] {
									w.xi - xMin, 
									xMax - w.xi, 
									w.yi - yMin, 
									yMax - w.yi
							};
							
							if (mf.softLeft)
								w.gridCoords[0] = -Integer.MAX_VALUE;
							if (mf.softRight)
								w.gridCoords[1] = -Integer.MAX_VALUE;
						}
				}
			}
		}
	}
	
	private FRect findBest (Set<FRect> rects ) {
		
		DumbCluster1D<FRect> 
			hS = new DumbCluster1DImpl<FRect>(0.2, rects, fr -> fr.x ),
			hE = new DumbCluster1DImpl<FRect>(0.2, rects, fr -> fr.getMaxX() ),
			vS = new DumbCluster1DImpl<FRect>(0.2, rects, fr -> fr.y ),
			vE = new DumbCluster1DImpl<FRect>(0.2, rects, fr -> fr.getMaxY() );
		
		CountThings<FRect> counts = new CountThings<>();
		
		for (DumbCluster1D<FRect> f : new DumbCluster1D[] {hS, hE, vS, vE}) {
			
			for (Cluster<FRect> c : f) 
				for (FRect r : c.things)
					counts.count( r, c.things.size() );
		}
		
		return counts.getMaxes().first().iterator().next();
	}

	private void add( Set<FRect> togo, Set<FRect> neighbours, FRect n ) {
		
		togo.remove(n);

		d:
		for ( Dir d : Dir.values() ) {
			
			
			FRect nn = n.getAdj( d );
			
			if ( nn != null && nn.getAdj( d.opposite ) == n && togo.contains( nn ) ) {

				for ( Dir p : new Dir[] { d.cc, d.cw } ) {
					FRect side = n.getAdj( p );
					if ( side != null )
						if ( side.getAdj( d ) == nn )
							continue d;
				}

				neighbours.add( nn );
			}
		}
	}
	
	private void mergeRemoveSmall( MiniFacade mf ) {
		
		for ( Feature f : toReg ) {
			
			Iterator<FRect> rit = mf.featureGen.get( f ).iterator();

			rit:
			while ( rit.hasNext() ) {
				
				FRect r = rit.next();
				
				if ( r.width <= 0.3 || r.height <= 0.3 ) {
					rit.remove();
					continue rit;
				}
			}
		}
	}
	
	private void mergeRemoveSimilar( MiniFacade mf ) {
		
		for ( Feature f : toReg ) {
			
			Iterator<FRect> rit = mf.featureGen.get( f ).iterator();
			
			rit:
				while ( rit.hasNext() ) {
					
					FRect r = rit.next();
					
					for (FRect r2 : mf.featureGen.get(f)) {
						if (r != r2 && similarMerge(r, r2)) {
							r2.setFrom ( average(r, r2) );
							r2.attached.putAll( r.attached );
							
							rit.remove();
							continue rit;
						}
					}
					
				}
		}
	}

	private DRectangle average( FRect...rs_ ) {
		
		FRect out = new FRect();
		
		List<FRect> rs= new ArrayList();

		for (FRect f : rs_ ) rs.add(f);
		
		for (int r1 = 0; r1 < rs.size(); r1++) {  // overlapping features
			
			FRect f1 = rs.get( r1 );
			
			for (int r2 = 0; r2 < rs.size(); r2++) {
				
				FRect f2 = rs.get(r2);
				
				if (r2 == r1 || f2.area() > f1.area())
					continue;
				
				double area = f2.union( f1 ).area();
				
				if ( area * 0.7> f1.area() ) {
					
					rs.remove( r2 );
					
					if (r2 < r1) 
						r1--;

					r2--;
				}
				
			}
			
			
		}
		
//		while (fit.hasNext()) {
//			FRect i = fit.next();
//			
//			for (FRect f : new Array)
//			
//		}
		
		boolean onlyInGrid = rs.stream().mapToInt( r -> r.outer == null ? 0 : 1 ).sum() >= 1;
		
		for (Bounds b : new Bounds[] {Bounds.XMIN, Bounds.XMAX, Bounds.YMIN,Bounds.YMAX} ) {
			
			double norm = 0, avg = 0;
			
			for (FRect r : rs) {
				
				if (onlyInGrid && r.outer == null)
					continue;
				
				double sl = r.sideLength( b );
				
				norm += sl;
				avg += r.get(b) * sl;
			}
			
			out.set (b, avg / norm);
		}
		
		return out;
	}
	
	private DRectangle union( Iterable<FRect> rs ) {
		
		Iterator<FRect> rit = rs.iterator();
		
		if (!rit.hasNext() )
			return null;
		
		FRect out = new FRect(rit.next());
		
		while (rit.hasNext())
			out.setFrom( out.union( rit.next() ) );
		
		return out;
	}

	private static boolean similarMerge( FRect n, FRect r ) {
		
		DRectangle i = n.intersect(r);
		double exactArea = n.area() + r.area() - (i == null ? 0 : i .area() );
		double unionArea = n.union( r ).area();
		
		return exactArea / unionArea > 0.5 &&  i != null 
				|| exactArea/unionArea > 0.9;
	}

	private static boolean similar( FRect n, FRect r ) {
		return Math.abs ( n.union( r ).area() - n.area() ) < 0.5;
	}
	
	private void constrainDoor( MiniFacade mf, FRect d, double alpha ) {
		
		if (d.y < 2)
			set(d, Bounds.YMIN, 0, alpha);
		
		if (d.height < 2.3 )
			set(d, Bounds.HEIGHT, 2.3, 5 * alpha);
		
//		if (d.height > 2.5)
//			set(d, Bounds.YMAX, 2.5, 0.5 * alpha);
		
//		if (d.width < )
	}
	
	private void constrainMoulding( MiniFacade mf, FRect d, double alpha ) {
		
		final double tol = 4;
		
		if (d.x < mf.left + tol)
			set (d, Bounds.XMIN, mf.left, alpha);
		
		if (d.x > mf.left + mf.width - tol)
			set (d, Bounds.XMAX, mf.left + mf.width, alpha);
		
		if (d.height < 0.5)
			set ( d, Bounds.HEIGHT, 0.5, alpha );
	}
	
	private void set (FRect r, Bounds direction, double value, double alpha) {
//		double top = r.get(direction.opposite);
		r.set (direction, (1 - alpha ) * r.y + alpha * value, true );
//		r.set(direction.opposite, top);
	}
}
