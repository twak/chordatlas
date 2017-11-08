package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.twak.siteplan.jme.Jme3z;
import org.twak.siteplan.jme.MeshBuilder;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.FeatureCache.MFPoint;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.tweed.gen.ProfileGen.MegaFacade;
import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.MutableDouble;
import org.twak.utils.PaintThing;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.collections.Streamz;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.ui.Plot;
import org.twak.utils.ui.Rainbow;
import org.twak.viewTrace.ColorRGBAPainter;
import org.twak.viewTrace.ModeCollector;
import org.twak.viewTrace.SuperLine;
import org.twak.viewTrace.facades.LineHeight;
import org.twak.viewTrace.facades.MiniFacade;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import com.thoughtworks.xstream.XStream;

public class SkelFootprint {
	
	SkelGen skelGen;
	Tweed tweed;
	List<Prof> globalProfs;
	
	public double megaFacadeAreaThreshold = TweedSettings.settings.megaFacadeAreaThreshold;
	public static double gisInterior = TweedSettings.settings.gisThreshold;;
	public double heightCutoff = TweedSettings.settings.heightThreshold;
	public double profMergeTol = 0.8;
	public boolean exitA = false;
	public static double exposedFaceFrac = TweedSettings.settings.exposedFaceThreshold;
	
	static boolean FALSE = new Object() == new Object(), TRUE = new Object() != new Object(); // for the interactive debugger
	

	public HalfMesh2 go(List<Line> footprint, SkelGen skelGen, ProgressMonitor m, Tweed tweed ) {
		
		this.skelGen = skelGen;
		this.tweed = tweed;
		
		PaintThing.lookup.put( ColorRGBA.class, new ColorRGBAPainter() );
		PaintThing.debug.clear();
		
		SolverState SS;

		SS = buildFootprint( footprint, m, tweed.features, skelGen.blockGen );

		dbgCountProfileEdges( SS );

		if ( SS == null )
			return null;

		if ( FALSE ) {

			PaintThing.debug.clear();
			dbgShowProfiles( SS.mesh, SS.globalProfs, SS.profFit, "edge fit" );
			SS.debugSolverResult();
			m.close();
			return SS.mesh;
		}

		solve( SS, m, skelGen.blockGen.getSolutionFile(), Long.MAX_VALUE );

		if ( TRUE ) 
			postProcesss(SS);
		
		dbgCountProfileEdges( SS );
		dbgShowProfiles( SS.mesh, SS.globalProfs, SS.profFit, "edge fit" );
		
		if (FALSE) {
			dbgShowProfiles( SS.mesh, null, SS.profFit, "postprocess" );
			PaintThing.debug.put( 1, footprint );
			SS.debugSolverResult();
		}
		
		return SS.mesh;
	}

	private void dbgCountProfileEdges( SolverState SS ) {
		Set<SuperLine> used = new HashSet<>();
		for (HalfFace f : SS.mesh) 
			for (HalfEdge e : f) {
				used.add( ((SuperEdge)e).profLine );
			}
		used.remove( null );
		System.out.println( "Input sweep edges: "+ used.size() );
	}
	
	public static void postProcesss( SolverState SS ) {
		
		mergeSameClassification ( SS.mesh );
		mergeSameClassification ( SS.mesh );
		
		mergeSmallFaces( SS ); // delme: causes infinite loops on 561.3527225284143_-555.7857439917622 			513.502095354607_-868.5858135006866 		613.198274125487_-929.9412937312637			707.5912053692705_-736.3628596400993
		
		Set<MegaFeatures> mfs = SS.minis.keySet();

		for (HalfFace f : SS.mesh)
			for (HalfEdge e : f) {
				
				double bestDist = 3;
				SuperEdge se = (SuperEdge)e;
				
				for (MegaFeatures mf : mfs)  {
					double dist = se.line().distance( mf.megafacade ) ;
					if (dist < bestDist && se.line().absAngle(mf.megafacade) < 0.3 ) {
						dist = bestDist;
						se.proceduralFacade = mf;
					}
				}
			}

		findRoofColor           ( SS.mesh );
		propogateProfilesMinis  ( SS.mesh );
		cleanFootprints         ( SS.mesh );
		cleanFootprints         ( SS.mesh );
		
		updateHeights           ( SS.mesh );
		
		if (FALSE) // not needed if we're not doing fully procedural windows
			findOcclusions ( SS.mesh ); 
	}

	private static void mergeSmallFaces( SolverState SS ) {
		Set<HalfFace> togo = new LinkedHashSet(SS.mesh.faces);
		while(!togo.isEmpty() ) {
			HalfFace f = togo.iterator().next();
			togo.remove( f );
			if (-f.area() < 5) {
				
				double bestLen = 0;
				HalfEdge longest = null;
				
				for (HalfEdge e : f) {
					double len = e.length();
					if ( e.over != null && e.over.face != f && len > bestLen) {
						bestLen = len;
						longest = e;
					}
				}
				
				if (longest != null) {
					togo.remove( f );
					longest.dissolve( SS.mesh );
					togo.add( longest.face );
				}
			}
		}
	}
	
	public  SolverState buildFootprint( List<Line> footprint, ProgressMonitor m, FeatureCache features, 
			BlockGen blockGen ) {
		
		MultiMap<MegaFeatures, MFPoint> minis = features == null ? null : features.createMinis(blockGen);
		Map<SuperEdge, double[]> profFit  = new HashMap(); 
		HalfMesh2 mesh = boundMesh( footprint );
		globalProfs = new ArrayList();

		Collections.sort( footprint, megaAreaComparator );

		for ( Line l : footprint ) {

			MegaFacade mf = ( (SuperLine) l ).getMega();
			
			if ( mf.area < megaFacadeAreaThreshold )
				break;

			insert( mesh, l, 2, true, true );
			if ( m.isCanceled() )
				return null;
		}

		if ( features != null )
			fractureOnFeatures( minis, footprint, mesh );

		m.setProgress( 2 );

		findProfiles( footprint, globalProfs, profFit );
		calcProfFit( mesh, globalProfs, profFit, m );

		if ( FALSE && profMergeTol > 0 ) 
			mergeOnProfiles (mesh, footprint);
		
		if ( exitA )
			return new SolverState( mesh, minis, globalProfs, profFit );
		
		System.out.println("sampling...");
		for ( HalfFace f : mesh ) {
			
			if ( TweedSettings.roofColours ) { //color roofs

				if ( blockGen.hasTextures && blockGen.transparency != 1 ) {
					JOptionPane.showMessageDialog( tweed.frame(), "Error sampling roof colors!\nI'll fix that; try again!" );
					m.close();
					blockGen.transparency = 1;
					blockGen.calculateOnJmeThread();

					return null;
				} else if ( !blockGen.hasTextures ) {
					JOptionPane.showMessageDialog( tweed.frame(), "No texture for roof colours;\nI'll disable that for you!" );
					TweedSettings.roofColours = false;
					break;
				}
				
				((SuperFace)f ).colors = new ArrayList<>();
			}

			meanModeHeightColor( Loopz.from( f ), (SuperFace) f, blockGen );
		}

		pushHeightsToSmallFaces( mesh );
		
		for ( HalfFace f : new ArrayList <> ( mesh.faces ) ) {
			SuperFace sf = (SuperFace) f;
			if (sf.height < heightCutoff)
				sf.remove( mesh );
		}
		
		removeExposedFaces      ( mesh );
		
		return new SolverState( mesh, minis, globalProfs, profFit );
	}

	public static void solve( SolverState SS, ProgressMonitor m, File output, long timeLimitSec ) {
		
		try {
			new GurobiSkelSolver(SS, m, timeLimitSec ).solve();
		}
		catch (Throwable th) {
			th.printStackTrace();
		}
		
		if ( output != null ) 
			SS.copy( true ).save( output, false );
	}

	private void mergeOnProfiles(HalfMesh2 mesh, List<Line> footprint) {
		
		System.out.println("merging over profiles...");
		
		TreeSet<HalfFace> togo = new TreeSet<>( (HalfFace o1, HalfFace o2) -> Double.compare(o1.area(), o2.area()) );  
		togo.addAll( mesh.faces );
		
		int count = 0;
		
		while (!togo.isEmpty()) {
			
			HalfFace f = togo.pollFirst();
			
			Cache<HalfEdge, MutableDouble> crossedBy = new Cach<>( e -> new MutableDouble(0));
			
			for (HalfEdge e : f) {
				SuperEdge se = (SuperEdge)e;
				
				if (se.profLine != null ) {

					MegaFacade mf = ((SuperLine) se.profLine).mega;
					
					if (mf != null)
					
					for (Prof p : mf.getTween(se.start, se.end, 0) ) {
						
						Line proj = new Line ( Pointz.to2(p.to3d(p.get(0))),Pointz.to2(p.to3d(p.get(p.size() -1)))  );

						for (HalfEdge e2 : f) {
							
							SuperEdge se2 = (SuperEdge)e2;
							
							if ( se2.profLine == null && (se2.over == null || ((SuperEdge)se2.over).profLine == null ) && 
									e2.over != null && e2.line().intersects(proj) != null && 
									Mathz.inRange( e2.line().absAngle( proj ), 0.25 * Math.PI, 0.75 * Math .PI)) {
								crossedBy.get(e2).d += ProfileGen.HORIZ_SAMPLE_DIST;
							}
						}
					}
				}
			}
			
			count += crossedBy.cache.size();
				
			Optional <Map.Entry<HalfEdge, MutableDouble>> longestO = crossedBy.cache.entrySet().stream()
						.filter( e1 ->  ((SuperEdge) e1.getKey()).profLine == null && e1.getValue().d > 0 ) //
						.max( (e1, e2) -> Double.compare(e1.getValue().d, e2.getValue().d) );
			
			if ( longestO.isPresent() ) {
				Map.Entry<HalfEdge, MutableDouble> longest = longestO.get();
				if ( longest.getValue().d > 0.6 * longest.getKey().length() ) {
					
					HalfFace tgf = longest.getKey().over.face;
					togo.remove( tgf );
					longest.getKey().face.merge ( mesh, tgf);
					((SuperFace)longest.getKey().face).mergeFrom( (SuperFace) tgf );
					
					togo.add( f );
				}
			}
		}
		
		System.out.println("found crossings "+count);
		killDoubleEdges( mesh );
	}

	public static void killDoubleEdges (HalfMesh2 mesh) {
		for (HalfFace f : mesh) {
			
			boolean iDidSomething;
			
			do {
				iDidSomething = false;
				for ( HalfEdge e : f ) {
					
					if (e.next.over == e) {
						e.dissolve( mesh );
						iDidSomething = true;
					}
					
				}
			} while ( iDidSomething );
		}
	}
	
	private static void propogateProfilesMinis( HalfMesh2 mesh ) {
		
		for (HalfFace f : mesh)
			for (List<HalfEdge> le : f.parallelFaces( 0.1 )) {

				int profI = -1;
				Prof prof = null;
				
				Set<MiniFacade> allMinis = new HashSet<>();
				
				for ( HalfEdge e : le ) {
					SuperEdge se = ( (SuperEdge) e );
					
					if ( profI > 0 && se.profI != -1 && profI != se.profI ) {
						System.out.println( "warning same-profile constraint failed" );
					}
					if ( se.profI >= 0 ) {
						profI = se.profI;
						prof = se.prof;
					}
					
					if (se.mini != null)
						allMinis.addAll(se.mini);
				}
				
				List<MiniFacade> allMinisL = new ArrayList(allMinis);
				
				for ( HalfEdge e : le ) {
					SuperEdge se = ( (SuperEdge) e );
					se.profI = profI;
					se.prof = prof;
					se.mini = allMinisL;
				}
			}
	}

	private static void updateHeights( HalfMesh2 mesh ) {
		for (HalfFace hf : mesh) {
			SuperFace sf = (SuperFace)hf;
			sf.height = sf.heights.stream().
					sorted().collect( new ModeCollector( 0.5 ) );
		}
	}

	private void fractureOnFeatures( MultiMap<MegaFeatures, MFPoint> minis , List<Line> footprint, HalfMesh2 mesh ) {
		
		for (MegaFeatures mf : minis.keySet())
			pt: for ( MFPoint pt : minis.get(mf) ) {
				
				if ( !Mathz.inRange( mf.megafacade.findPPram( pt ), 0, 1 ) )
						continue;
				
				Vector2d dir = pt.mega.megafacade.dir();
				dir.set( dir.y, -dir.x );

				Point2d probe = new Point2d( dir );
				probe.scale( 2 / dir.length() );
				probe.add( pt );

				
				for (Point2d avoid : pt.mega.megafacade.points())  // don't fracture near minifacade boundaries...we can't distinguish nice block bondaries
					if (avoid.distanceSquared( pt ) < 4)
						continue pt;

				double bestDist = Double.MAX_VALUE;
				
				for ( HalfFace f : mesh.faces )
					for ( HalfEdge e : f )
						if ( e.line().dir().angle( dir ) < 0.4 ) {
							double dist = e.line().distance( probe );
							if ( dist < bestDist ) 
								bestDist = dist;
						}

				if ( bestDist > 0.3 ) {

					Vector2d end = new Vector2d( dir );
					end.scale( 3 / end.length() );
					end.add( probe );

					Vector2d start = new Vector2d( dir );
					start.scale( 0.5 / start.length() );
					start.add( pt );

					Line extra = new Line( new Point2d( start ), new Point2d( end ) );

					SkelFootprint.insert( mesh, extra, 2, false, false );
				}
			}
	}

	public static HalfMesh2 boundMesh( List<Line> footprint ) {
		double[] minMax = minMax( 10, footprint );
		HalfMesh2.Builder builder = new HalfMesh2.Builder( SuperEdge.class, SuperFace.class );
		
		builder.newPoint( new Point2d( minMax[ 0 ], minMax[ 3 ] ) );
		builder.newPoint( new Point2d( minMax[ 1 ], minMax[ 3 ] ) );
		builder.newPoint( new Point2d( minMax[ 1 ], minMax[ 2 ] ) );
		builder.newPoint( new Point2d( minMax[ 0 ], minMax[ 2 ] ) );
		builder.newFace ();

		HalfMesh2 mesh = builder.done();
		return mesh;
	}

	private static void cleanFootprints( HalfMesh2 mesh ) {
		
		for (HalfFace hf : mesh.faces) 
			for (HalfEdge e : hf.edges())
				if (e.over != null && e.over.face != e.face)
					e.over = null;
		
		Map<HalfEdge, Double> mergePoint = new HashMap();
		
		Predicate<HalfEdge> badEdges = new Predicate<HalfMesh2.HalfEdge>() {

			@Override
			public boolean test( HalfEdge t ) {
				
				
				if (t.over != null ) // is edge within a single face 
					return false; // preserve as hole-marker
				
				double len = t.length();
				
				if (t.length() < 0.2) {
					mergePoint.put(t, 0.5);
					return true;
				}
				
				double angleNext = t.line().absAngle( t.next.line() );
				
				final double tol = 0.1;
				
				if ( t.next.over == null && len < t.next.length() && angleNext > Math.PI - tol ) {
					mergePoint.put( t, 0. );
					return true;
				}

				if ( t.next.over == null && angleNext < tol ) {
					mergePoint.put( t, 0. );
					return true;
				}

				HalfEdge prev = t.findBefore();
				double anglePrev = t.line().absAngle( prev.line() );

				if ( prev.over == null && len <= prev.length() && anglePrev > Math.PI - tol ) {
					mergePoint.put( t, 1. );
					return true;
				}

				if ( prev.over == null && anglePrev < tol ) {
					mergePoint.put( t, 1. );

					return true;
				}
				
				return false;
			}
		};
		
		f: for ( HalfFace f : new ArrayList<>( mesh.faces ) ) {

			Set<HalfEdge> togo = Streamz.stream( f.edges() ).filter( badEdges ).collect( Collectors.toSet() );

			while ( !togo.isEmpty() ) {

				HalfEdge g = togo.iterator().next(), p = g.findBefore(), n = g.next;
				
				togo.remove( g );
				togo.remove( p );
				togo.remove( n );
				
				if ( g.replaceByPoint( mesh, g.line().fromPPram( mergePoint.get(g) ) ) )
					continue f;

				
				HalfEdge pp = p.findBefore();
				
				Streamz.stream( pp, p, n, n.next ).forEach( o -> togo.remove( o ) );
				Streamz.stream( pp, p, n, n.next ).filter( badEdges ).forEach( e -> togo.add( e ) );
			}
		}
		
		
		for (HalfFace f : mesh.faces) {
			Set<Point2d> seen = new HashSet<>();
			for (HalfEdge e : f) {
				
				if (seen.contains( e.end ) && e.over == null && e.next.over == null) {
					
					HalfEdge n = e.next;
					
					Point2d edited;
					Vector2d b4 = e.line().dir(), af = n.line().dir();
					b4.normalize();
					af.normalize();
					
					b4.set (b4.y, - b4.x );
					af.set (af.y, - af.x );
					
					b4.add(af);
					
					b4.scale( 1 / b4.length() );
					edited = new Point2d ( b4 );
					edited.add(e.end);
					
					n.start = edited;
					e.end = new Point2d ( edited );
					
				}
				
				seen.add(e.end);
			}
		}
	}


	public static void insert( HalfMesh2 mesh, Line line, double softDist, boolean backwardsToo, boolean setLine ) {

		Vector2d dir = line.dir(), nDir = new Vector2d( dir );
		nDir.negate();

		double remaining = line.length();

		for ( HalfFace f : mesh.faces ) {
			if ( f.contains( line.start ) ) {

				HalfEdge n = f.fracture( line.start, nDir ), p = f.fracture( line.start, dir );

				if (n == null || p == null) {
					System.err.println( "geometry failure" );
					return; 
				}
				
				HalfEdge next = p.next.over, prev = n.next.over;

				HalfEdge dividing = f.split( mesh, n, p );

				dividing.split( line.start );

				( (SuperEdge) dividing.next ).profLine = setLine ? (SuperLine) line : null;
				
				double l = dividing.next.length();

				if ( remaining < l ) {

					Point2d softStart = new Point2d( dir );
					softStart.scale( remaining / dir.length() );
					softStart.add( line.start );

					dividing.next.split( softStart );
					
					( (SuperEdge) dividing.next.next ).profLine = null;
					( (SuperEdge) dividing.next.next.over ).profLine = null;
					
					double remSoftDist = softDist - dividing.next.next.line().length();
					
					if ( remSoftDist > 0 )
						fracture(mesh, next, dir, 0, remSoftDist, null,  setLine );
					
				} else if ( next != null )
					fracture( mesh, next, dir, remaining - l, softDist ,line, setLine );

				
				double softDistN = softDist - dividing.start.distance( line.start );
				
				
				
				if ( backwardsToo && softDistN > 0 && prev != null) {
					fracture( mesh, prev, nDir, 0, softDistN, null, setLine );
				}
				
				return;
			}
		}
	}

	private static void fracture( HalfMesh2 mesh, HalfEdge previous, Vector2d dir, double remainingHard, double remainingSoft, Line line, boolean setLine ) {

		if ( previous == null )
			return; // at edge of shape: done

		HalfEdge p2 = previous.face.fracture( previous.end, dir, previous, previous.next );

		if ( p2 == null ) {
			System.err.println( "failed to find intersection from " + previous.end + " dir " + dir );
			return;
		}

		HalfEdge next = p2.next.over;

		HalfEdge dividing = previous.face.split( mesh, previous, p2 );

		( (SuperEdge) dividing ).profLine = setLine ? (SuperLine) line : null;

		double l = dividing.length();

		if ( remainingHard != 0 )
			if (remainingHard < l ) {

			Point2d softStart = new Point2d( dir );
			softStart.scale( remainingHard / dir.length() );
			softStart.add( previous.end );

			dividing.split( softStart );

			( (SuperEdge) dividing.next ).profLine = null;
			( (SuperEdge) dividing.next.over ).profLine = null;
			
			fracture( mesh, next, dir, 0, remainingSoft - l + remainingHard, null, setLine );
			}
			else
				fracture( mesh, next, dir, remainingHard-l, remainingSoft, line, setLine );
		else if (remainingSoft > l)
			fracture( mesh, next, dir, 0, remainingSoft - l, line, setLine );
	}

	
	private static void findRoofColor( HalfMesh2 mesh ) {		
		
		class Wrapper implements Clusterable {

			double[] col;
			
			public Wrapper(float[] col) {
				this.col = new double[] {col[0], col[1], col[2]};
			}
			
			@Override
			public double[] getPoint() {
				return col;
			}
		}

		for ( HalfFace hf : mesh.faces ) {
			
			List<Wrapper> toCluster = new ArrayList();
			
			SuperFace sf = (SuperFace)hf;
			
			if (sf.colors == null || sf.colors.isEmpty()) {
				float grey = (float) (Math.random() * 0.3 + 0.2 );
				sf.roofColor = new float[] {grey, grey, grey};
				continue;
			}
			
			for (float[] v : sf.colors)
				toCluster.add(new Wrapper(v));
			
			sf.colors = null;
				
			DBSCANClusterer<Wrapper> cr = new DBSCANClusterer<>( 0.2, 5 );
			List<Cluster<Wrapper>> results = cr.cluster( toCluster );
			float[] col = new float[] {0.3f, 0.3f, 0.3f};
			
			try {
				Cluster<Wrapper> biggest = results.stream().max(
						( a, b ) -> Double.compare( a.getPoints().size(), b.getPoints().size() ) ).get();

				col = new float[3];
				
				for ( Wrapper w : biggest.getPoints() )
					for ( int i = 0; i < 3; i++ )
						col[ i ] += (float) w.col[ i ];

				int size = biggest.getPoints().size();

				for ( int i = 0; i < 3; i++ )
					col[ i ] /= size;
			}
			catch (NoSuchElementException e ) {}
			
			sf.roofColor = col;
		}
	}

	public static void mergeSameClassification( HalfMesh2 mesh ) {
		
		// goal is to encode a polygon with holes as a single perimeter loop that backtracks along itself....
		
		Set<HalfEdge> edges = new LinkedHashSet();
		
		for (HalfFace hf : mesh.faces)
			for (HalfEdge e2 : hf.edges())
				if (e2.over != null && ((SuperFace)e2.face).classification == ((SuperFace)e2.over.face).classification )
					edges.add(e2);
		edges:
		while (!edges.isEmpty()) {
			
			HalfEdge e = edges.iterator().next();
			
			if (e.over == null) { // debug condition
				edges.remove( e );
				continue;
			}
			
			HalfFace a = e.face, b = e.over.face;
			
			if (a==b) {
				edges.remove( e );
				continue;
			}
				
			
			{
				HalfEdge start = null;
				
				while ( a.e.over != null && // set a's edge to be one that we won't fuck with
						a.e.over.face == b
					) {

					if (a.e == start) { // if we can't find an edge that isn't b, then we are a hole with the same classification: delete ourselves, and all adjacent edges of b. 
						
						for ( HalfEdge f : a.edges() ) {
							
							
							edges.remove ( f );
							edges.remove (f.over );
							
							if (f.next.over.next.over != f) {
								HalfEdge beforeBreak = f.over.findBefore();
								beforeBreak.next = f.next.over.next;
								b.e = beforeBreak;
							}
						}
						
						((SuperFace)b).mergeFrom((SuperFace)a);
						mesh.faces.remove( a );
						
						
						continue edges;
					}
					
					if (start == null)
						start = a.e;
					
					a.e = a.e.next;
				}
			}
			
			for (HalfEdge oeb : a.edges() ) {
				
				HalfEdge oe  = oeb.next;
				
				edges.remove (oe);
				
				if ( ( oeb.over == null || oeb.over.face != b) && oe.over != null && oe.over.face == b) {
					
					
					edges.remove (oe.over);
					
					HalfEdge le = oe;
					
					while (le.next.over != null && le.next.over.face == b &&
						   le.next.over.next == le.over ) {
						   le = le.next;
						   edges.remove( le );
						   edges.remove( le.over );
					}

					
					le.over.findBefore().next = le.next;
					oeb.next = oe.over.next;
					
					((SuperFace)a).mergeFrom((SuperFace)b);
					mesh.faces.remove(b);

					for (HalfEdge e2 : a.edges()) {
						
						if (e2.over != null && e2.over.face == b) {
							edges.remove( e2 );
							edges.remove( e2.over );
						}
						
						e2.face = a;
					}
					
					// only remove the first boundary between a and b
					break;
				}
			}
		}
		
	}
	
	public static void removeExposedFaces( HalfMesh2 mesh ) {
		
		System.out.println("removing exposed faces....");
		
		Set<HalfFace> togo = new HashSet<>(mesh.faces);
		
		while (!togo.isEmpty()) {
//		for ( HalfFace hf : new ArrayList<HalfFace>( mesh.faces ) ) {
			
			HalfFace hf = togo.iterator().next();
			togo.remove(hf);
			
			double exposed = 0, safe = 0;
			
			for (HalfEdge e : hf.edges()) {
				if (e.over == null && ((SuperEdge)e).profLine == null ) 
					exposed += e.length();
				else
					safe += e.length();
			}
			
			if (exposed > exposedFaceFrac * safe) {
				togo.addAll( hf.getNeighbours() );
				hf.remove( mesh );
			}
		}
	}

	private final static Vector3f UP = new Vector3f( 0, 1, 0 );

	public static void meanModeHeightColor( Loop<Point2d> pts, SuperFace sf, BlockGen blockgen ) {
		
		double[] minMax = Loopz.minMax2d( pts );

		double sample = 2;

		double missCost = 30;
		
		if (sf.colors != null)
			sf.colors.clear();
		
		sf.heights.clear();
		
		int insideGIS = 0, outsideGIS = 0;

		LoopL<Point2d> gis = blockgen.profileGen.gis;
		gis = Loopz.removeInnerEdges( gis );
		gis = Loopz.removeNegativeArea( gis, -1 );
		gis = Loopz.mergeAdjacentEdges( gis, 1, 0.05 );
		
		for ( double x = minMax[ 0 ]; x < minMax[ 1 ]; x += sample )
			for ( double y = minMax[ 2 ]; y < minMax[ 3 ]; y += sample ) {
				
				Point2d p2d = new Point2d( x, y );
				
				if ( Loopz.inside( p2d, pts ) ) {

						CollisionResults results = new CollisionResults();
						blockgen.gNode.collideWith( new Ray( Jme3z.toJmeV( x, 0, y ), UP ), results );
						CollisionResult cr = results.getFarthestCollision();

						double height;

						if ( cr != null ) {

							height = cr.getDistance();
							sf.heights.add( height );
							
							if ( sf != null && sf.colors != null ) {
								ColorRGBA col = getColor( cr.getGeometry(), cr.getContactPoint(), cr.getTriangleIndex(), blockgen.tweed );
								sf.colors.add( new float[] { col.r, col.g, col.b } );
							}
						}
						
						
					if ( Loopz.inside( p2d, gis ) ) { 
						insideGIS ++;
//						PaintThing.debug( Color.yellow, 1, p2d);
					}
					else {
						outsideGIS ++;
//						PaintThing.debug( Color.green, 1, p2d);
					}
				}
			}
		

		if ( sf.heights.size() < 5 )
			sf.height = -Double.MAX_VALUE;
		else if ( TweedSettings.settings.useGis &&  insideGIS  <  gisInterior * outsideGIS )
			sf.height = -missCost;
		else {
			sf.updateHeight();
		}
		
		sf.maxProfHeights = new ArrayList();
		
		for (HalfEdge e : sf) {
			SuperEdge se = ((SuperEdge) e);
			if ( se.profLine!= null ) 
				for (Prof p : ((SuperLine)se.profLine).getMega().getTween( e.start, e.end, 0.3) )
					sf.maxProfHeights.add( p.get( p.size()-1 ).y );
		}
	}

	private static double[] minMax( double expand, List<Line> footprint ) {
		double[] out = new double[] { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE };

		for ( Line l : footprint ) {
			out[ 0 ] = Math.min( l.start.x, out[ 0 ] );
			out[ 1 ] = Math.max( l.start.x, out[ 1 ] );
			out[ 2 ] = Math.min( l.start.y, out[ 2 ] );
			out[ 3 ] = Math.max( l.start.y, out[ 3 ] );
		}

		out[ 0 ] -= expand;
		out[ 1 ] += expand;
		out[ 2 ] -= expand;
		out[ 3 ] += expand;

		return out;
	}

	private static void pushHeightsToSmallFaces( HalfMesh2 mesh ) {
		for (HalfFace f : mesh.faces) {
			
			SuperFace sf = (SuperFace)f;
			if (sf.height == -Double.MAX_VALUE ) {//|| sf.area() < 1  ) {
				
				double bestLength = -Double.MAX_VALUE;
				SuperFace bestFace = null;
				
				overProfile: for ( boolean overProfiles : new boolean[] { false, true } ) {

					for ( HalfEdge e2 : f.edges() ) {

						double len = e2.length();
						if ( len > bestLength && e2.over != null )
							if ( overProfiles ||  
								  ((SuperEdge)e2)     .profLine == null && 
								  ((SuperEdge)e2.over).profLine == null ) {
							
							SuperFace sf2 = (SuperFace) e2.over.face;
							if ( sf2.height != -Double.MAX_VALUE ) {
								bestFace = sf2;
								bestLength = len;
							}
						}
					}
					
					if ( bestFace != null ) {
						sf.height = bestFace.height;
						break overProfile;
					}
				}
			}
		}
	}
	

	private static void findOcclusions( HalfMesh2 mesh ) {

		int count = 0;
		
		for ( HalfFace hf : mesh.faces )
			for ( HalfEdge e1 : hf.edges() ) {

				Line el1 = e1.line();

				for ( HalfFace hf2 : mesh.faces ) {
					for ( HalfEdge e2 : hf2.edges() )
						if ( e1 != e2 ) {

							Line e2l = e2.line();

							if ( 	el1.distance( e2l ) < 1 && 
									e2l.absAngle( el1 ) > Math.PI * 0.7  
//									!el1.isOnLeft(  e2l.fromPPram( 0.5 ) ) 
									) {
								
								( (SuperEdge) e1 ).occlusions.add(
										new LineHeight( 
												el1.project( e2l.start, true ), 
												el1.project( e2l.end, true ), 
												0, ( (SuperFace) hf2 ).height ) );
								count++;
							}
						}
				}
			}
		System.out.println("found "+count+" occluding surfaces");
	}

	
	public static ColorRGBA getColor( Geometry geom, Vector3f pt, int index, Tweed tweed ) {
		
		MatParam param = geom.getMaterial().getParam( "DiffuseMap" );
		
		ImageRaster ir = ImageRaster.create( tweed.getAssetManager().loadTexture( ((Texture2D)param.getValue()).getName() ).getImage() );
    	
		Mesh mesh = geom.getMesh();
//		geom.getMaterial().getMaterialDef().
		
        VertexBuffer pb = mesh.getBuffer(Type.Position);
        VertexBuffer tb = mesh.getBuffer( Type.TexCoord );
        
        IndexBuffer  ib = mesh.getIndicesAsList();
        
        Vector2f uva = new Vector2f(), uvb = new Vector2f(), uvc = new Vector2f();
        Vector3f la  = new Vector3f(), lb  = new Vector3f(), lc  = new Vector3f();
        
        if (pb != null && pb.getFormat() == Format.Float && pb.getNumComponents() == 3) {
        	
            FloatBuffer fpb = (FloatBuffer) pb.getData();
            FloatBuffer ftb = (FloatBuffer) tb.getData();

            // aquire triangle's vertex indices
            int vertIndex = index * 3;
            
            int va = ib.get(vertIndex);
            int vb = ib.get(vertIndex+1);
            int vc = ib.get(vertIndex+2);
            
            BufferUtils.populateFromBuffer( la, fpb, va );
            BufferUtils.populateFromBuffer( lb, fpb, vb );
            BufferUtils.populateFromBuffer( lc, fpb, vc );
            
            BufferUtils.populateFromBuffer( uva, ftb, va );
            BufferUtils.populateFromBuffer( uvb, ftb, vb );
            BufferUtils.populateFromBuffer( uvc, ftb, vc );
            
//            PaintThing.debug.put(1, new Line ( la.x, la.z, lb.x, lb.z) );
//            PaintThing.debug.put(2, new Line ( lb.x, lb.z, lc.x, lc.z) );
//            PaintThing.debug.put(3, new Line ( lc.x, lc.z, la.x, la.z) );
            
            float[] bary = barycentric( pt, la, lb, lc );
            
            int x = (int)( ( uva.x * bary[0] + uvb.x * bary[1] + uvc.x * bary[2] ) * ir.getWidth ()) ,
            	y = (int)( ( uva.y * bary[0] + uvb.y * bary[1] + uvc.y * bary[2] ) * ir.getHeight()) ;
            
            ColorRGBA out = ir.getPixel( x, y );//ir.getHeight() - y -1 );

//            for (Pair<Vector3f, Vector2f> pair : new Pair[]{ new Pair( la, uva), new Pair (lb, uvb), new Pair (lc, uvc)}) {
//            	
//            	int xx = (int)(pair.second().x * ir.getWidth () ),
//            	    yy = (int)(pair.second().y * ir.getHeight() );
//            	
//            	System.out.println("xx "+xx+" yy "+ yy );
//            	
//            	ColorRGBA o = ir.getPixel( 
//            			xx,
//            			yy );
//            	
//            	PaintThing.debug.put(1, new ColPt( pair.first().x, pair.first().z, o.r, o.g, o.b ));
//            }
            
//            System.out.println("<< "+ ((Texture2D)param.getValue()).getName());
//			System.out.println( x + " " + y + " :: " + bary[ 0 ] + " " + bary[ 1 ] + " " + bary[ 2 ] + 
//					" --> " + out.r + "," + out.g + "," + out.b );
            
            return out;
            
        }else{
        	
            throw new UnsupportedOperationException("Position buffer not set or has incompatible format");
        }
    }

	// http://gamedev.stackexchange.com/questions/23743/whats-the-most-efficient-way-to-find-barycentric-coordinates
	private static float[] barycentric(Vector3f p, Vector3f a, Vector3f b, Vector3f c )
	{
	    Vector3f v0 = b.subtract( a ),
	    	 	 v1 = c.subtract( a ),
	    		 v2 = p.subtract( a );
	    
	    float d00 = v0.dot(v0);
	    float d01 = v0.dot(v1);
	    float d11 = v1.dot(v1);
	    float d20 = v2.dot(v0);
	    float d21 = v2.dot(v1);
	    
	    float denom = d00 * d11 - d01 * d01;
	    
	    float v = (d11 * d20 - d01 * d21) / denom,
	    	  w = (d00 * d21 - d01 * d20) / denom,
	          u = 1.0f - v - w;
	    
	    return new float[] {u, v, w};
	}


	private void findProfiles( List<Line> footprint, List<Prof> globalProfs, Map<SuperEdge, double[]> profFit ) {
		
		MultiMap<SuperLine,List<Prof>> profSets = new MultiMap<>();

		System.out.println("clustering "+globalProfs.size() + " profiles over sweep edges...");
		
		for (Line l : footprint)
			profileRuns( (SuperLine) l, profSets );

		Prof example = null;
		
		if ( false ) {
			Node dbg = new Node();
			for ( SuperLine sl : profSets.keySet() ) {

				for ( List<Prof> lp : profSets.get( sl ) ) {
					ColorRGBA color = new ColorRGBA( (float) Math.random(), (float) Math.random(), (float) Math.random(), 1 );

					for ( Prof p : lp )
						p.render( tweed, dbg, color, 1 );
				}
			}
			tweed.frame.addGen( new JmeGen( "global src", tweed, dbg ), true );
		}
		
		System.out.println("cleaning "+ profSets.values().stream().flatMap( c -> c.stream() ).count() +" profiles...");
		
		for ( SuperLine sl : profSets.keySet() )
			for ( List<Prof> lp : profSets.get( sl ) ) {
				
				if (example == null)
					example = lp.get( 0 );
				
				Prof p = Prof.parameterize( sl, lp );
				if (p != null && p.size() > 1)
					globalProfs.add( SkelGen.moveToX0( p ) );
			}

		Iterator<Prof> git = globalProfs.iterator();
		int c = 0;
		while (git.hasNext()) {
			Prof p = git.next();
			if (p.size() < 2)
				git.remove();
			else if (p.size() == 2) {
				if (p.get(0).x == p.get(1).x) {
					git.remove();
					c++;
				}
			}
			else if (p.size() == 3) {
				if (p.get(0).x == p.get(1).x &&
						p.get(2).y == p.get(1).y)
					git.remove();
			}
		}
		

		System.out.println( "found " + globalProfs.size() + " interesting profiles" +" from " + profSets.size() +" runs" );
//		removeSimilar( globalProfs, 40 );// TweedSettings.settings.profileCount );
		removeSimilarOld( globalProfs, TweedSettings.settings.profilePrune );
		
		System.out.println( "after remove similar " + globalProfs.size() );
		{
			Prof vertical = new Prof( example ); 
			vertical.clear();
			vertical.add( new Point2d() );
			vertical.add( new Point2d( 0, 20 ) );
			globalProfs.add( 0, vertical );
		}
		
	}
	
	private void removeSimilar( List<Prof> globalProfs, int count ) {

		class PPS implements Comparable<PPS>{
			Prof a, b;
			double s;
			
			public PPS (Prof a, Prof b, double s) {
				this.a = a;
				this.b = b;
				this.s = s;
			}
			
			@Override
			public int compareTo( PPS o ) {
				return Double.compare (s,o.s);
			}
		}
		
		TreeSet<PPS> closest = new TreeSet<>();
		
		Set<Prof> deadpool = new HashSet<>();
		
		for (Prof a : globalProfs)
			for (Prof b : globalProfs)
				closest.add( new PPS (a, b, a.distance( b, true, false, false) ) );
		
		
		while (globalProfs.size() - deadpool.size() > count) {
			
			PPS g = closest.pollFirst();
			
			if (deadpool.contains( g.a ) || deadpool.contains( g.b ))
				continue;
			
			Prof togo = g.a.get( g.a.size()-1 ).y > g.b.get( g.b.size()-1 ).y ? g.b : g.a;
			deadpool.add( togo );
		}
		
		globalProfs.removeAll( deadpool );
		
	}
	
	private void removeSimilarOld( List<Prof> globalProfs, double tol ) {

		Iterator<Prof> pit = globalProfs.iterator();
		
		while (pit.hasNext()) {
			Prof p1 = pit.next();
			boolean remove = false;
			for (Prof p2 : globalProfs) {
				if ( p1 != p2 && p1.distance( p2, true, false, false) < tol  ) {
					remove = true;
					break;
				}
			}
			
			if (remove)
				pit.remove();
		}
	}

	private static void profileRuns( SuperLine sl, MultiMap<SuperLine, List<Prof>> profSets ) {

		MegaFacade mf = sl.getMega();
		
		Cache<Integer, Double> distance2Next = new Cache<Integer, Double>() {

			@Override
			public Double create( Integer i ) {
				
				Prof pc = mf.profiles.get(i), pn = mf.profiles.get(i+1);

				if (pc == null || pn == null)
					return 1e6;
				
				return pc.distance (pn, true, false, false );
				
			}
			
		};
//				i -> mf.profiles.get( i ).distance( mf.profiles.get(i+1), true ));
		
		
		int start = mf.hExtentMin;
		
		for (int i = mf.hExtentMin; i < mf.hExtentMax; i++) {
			
			if ( distance2Next.get(i) > 4 || i == mf.hExtentMax - 1)  
			{
				
//				if ( (Math.random() > 0.95 || i == mf.hExtentMax - 1)  ){//0.5 / ProfileGen.HORIZ_SAMPLE_DIST) {
					if (i - start > 0.5 / ProfileGen.HORIZ_SAMPLE_DIST) {
					
					List<Prof> lp = IntStream.range( start, i+1 ).
							mapToObj( p -> mf.profiles.get(p) ).
							filter (p -> p != null).
							collect( Collectors.toList() );
					
					if (lp != null && !lp.isEmpty())
						profSets.put (sl, lp); 
							
					}
					start = i+1;
//					i++;
//				}
			}
		}

//		System.out.println( (mf.hExtentMax - mf.hExtentMin)+  " mm " + min+ " / " + max +" found " + profSets.size() );
	}
	
//	private static Prof boringProf( double h ) {
//		Prof prof = Prof.buildProfile( new Line3d( new Point3d( 0, 0, 0 ), new Point3d( 1, 0, 0 ) ), new Point3d( 0, 0, 0 ) );
//		prof.add(new Point2d (0,0));
//		prof.add(new Point2d (0,h));
//		return prof;
//	}
	
	private void calcProfFit(HalfMesh2 mesh, List<Prof> globalProfs, Map<SuperEdge, double[]> out, ProgressMonitor m) {
		
		System.out.println("Building F...");
		
		boolean[] used = new boolean[globalProfs.size()];
		
		Map<SuperEdge, double[]> tmp = new HashMap();
		
		double[] noData =  new double[globalProfs.size()];
		Arrays.fill( noData, 0 );
		noData[0] = -1;
		used[0] = true;
		
		for (int i = 0; i < mesh.faces.size(); i++) {
			
//			System.out.println( "calculating profile fit for face " + i + "/" + mesh.faces.size() );
			HalfFace f= mesh.faces.get(i);
			
			for (HalfEdge e : f)  {
				
				double[] fits = new double[globalProfs.size()];
				
				for (int g = 0; g < fits.length; g++ ) {
					 Double d = meanDistance( globalProfs.get(g), (SuperEdge) e );
					 if (d == null || Double.isNaN( d )) {
						 fits = noData;
						 break;
					 }
					 else
						 fits[g] = d;
				}
				
				tmp.put((SuperEdge)e, fits );
				used[Arrayz.min( fits )] = true;
			}
			
			if (m.isCanceled())
				return;
		}
		
		Arrays.fill(used, true);
		
		int count = 0;
		
		for (boolean u : used)
			if (u)
				count ++;
		
		for (Map.Entry<SuperEdge, double[]> e: tmp.entrySet()) {
			
			double[] fits = new double[count];
			
			int c = 0;
			
			for (int i = 0; i < e.getValue().length; i++) 
				if (used[i])
					fits[c++] = e.getValue()[i];
			
			out.put( e.getKey(), fits );
		}
		
		for (int g = globalProfs.size() -1; g >= 0; g--)
			if (!used[g])
				globalProfs.remove(g);
	}

	private Double meanDistance( Prof clean, SuperEdge he ) {
		
		SuperLine sl = (SuperLine) he.profLine;
		
		if (sl == null) 
			return null;
		
		MegaFacade mf = sl.getMega();

		int s = mf.getIndex( he.start ),
			e = mf.getIndex( he.end   );
		
		if (s > e) {
			int tmp = e;
			e = s;
			s = tmp;
		}
		
		int delta = e - s;
		
		if (delta >= 8) {
			delta *= 0.2;
			s += delta;
			e -= delta;
		}
		
		return meanDistance (mf, s,e, clean);
	}

	public static Double meanDistance( MegaFacade mf, int s, int e, Prof clean ) {
		
		double tDist = 0;
		
		int count = 0;
		
		for ( int i = s; i <= e; i++ ) {
			Prof p = mf.profiles.get(i);
			if (p == null)
				continue;
			
			count++;
			
			double d = clean.distance ( p, false, true, false );
			tDist += d < Double.MAX_VALUE ? d : 100;
		}
		
//		if (count < 0.5/ProfileGen.HORIZ_SAMPLE_DIST)
//			return null;
		
		return tDist / count;
	}
	
	
	public static void debugFindCleanProfiles(List<Line> footprint, SkelGen skelGen, ProgressMonitor m, Tweed tweed ) {

		MultiMap<SuperLine, List<Prof>> profSets = new MultiMap<>();

		for (Line l : footprint)
			profileRuns( (SuperLine) l, profSets );

		
		List<List<Prof>>ordered= new ArrayList<>();
		Map<List<Prof>, SuperLine> pairs = new LinkedHashMap<>();
		
		for (SuperLine l : profSets.keySet())
			for (List<Prof> lp : profSets.get(l)) {
				pairs.put (lp, l);
				ordered.add(lp);
			}
		
		
		JSlider bunch = new JSlider(0,ordered.size()-1);
		JButton button = new JButton("go");
		Plot plot = new Plot (bunch, button);
		
		button.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				PaintThing.debug.clear();
				plot.toPaint.clear();
				
				List<Prof> ps = ordered.get (bunch.getValue() );
				SuperLine sl =  pairs.get(ps);
				
				if ( sl != null && ps != null ) {

					PaintThing.debug( new Color( 0, 0, 0, 50 ), 1, ps );

					Prof clean = Prof.parameterize( sl, ps );

					Prof c2 = new Prof( clean );
					for ( Point2d p : c2 )
						p.x += 10;

					PaintThing.debug( new Color( 0, 170, 255 ), 3f, c2 );//plot.toPaint.add( clean ) );

					Prof mid = ps.get( ps.size() / 2 );

					tweed.enqueue( new Runnable() {
						@Override
						public void run() {

							Jme3z.removeAllChildren( tweed.debug );

							for ( Prof p : ps ) {

								//							p = p.moveToX0();

								p.render( tweed, tweed.debug, ColorRGBA.Blue, (float) ProfileGen.HORIZ_SAMPLE_DIST );
							}

							Point3d pt = mid.to3d( mid.get( 0 ) );
							pt.y = 0;

							Geometry geom = new Geometry( "material_", clean.renderStrip( 1, null ) );
							Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
							mat.setColor( "Diffuse", ColorRGBA.Blue );
							mat.setColor( "Ambient", ColorRGBA.Red );

							geom.setMaterial( mat );

							//						tweed.debug.attachChild( geom );

							tweed.debug.updateGeometricState();
							tweed.debug.updateModelBound();
							tweed.gainFocus();
						}
					} );
				}
				plot.repaint();
			}
		} );
		
		bunch.addChangeListener( new ChangeListener() {
			
			@Override
			public void stateChanged( ChangeEvent e ) {
				button.doClick();
			}
		} );
	}
	
	private void dbgShowProfiles( HalfMesh2 mesh, List<Prof> globalProfs, Map<SuperEdge, double[]> profFit, String name ) {

		Node n = new Node();
		
		Jme3z.removeAllChildren( n );

		
		int colI = 0;
		
		for ( HalfFace f : mesh ) {
			
			ColorRGBA col = Jme3z.toJme( Rainbow.getColour( colI++ ) ); 
			colI = colI %6;
			
			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
			mat.setColor( "Diffuse", col );
			mat.setColor( "Ambient", col );
			mat.setBoolean( "UseMaterialColors", true );
			
			if (true)
			{
				Loop<Point3d> loop = new Loop<>();

				for (HalfEdge e : f) 
					loop.append( new Point3d (e.start.x, 0, e.start.y) );
				
				MeshBuilder mb = new MeshBuilder();
				mb.add(loop.singleton(), false);
				
				Geometry g = new Geometry("floorplan", mb.getMesh());

				
				g.setMaterial(mat);
				
				n.attachChild( g );
				
			}
			
			for ( HalfEdge e : f ) {

				SuperEdge se = (SuperEdge) e;

				
				Prof bestProf = null;

				if ( globalProfs == null )
					bestProf = se.prof;
				else {

					
//					if (se.profLine != null) {
//						
//						SuperLine sl = ((SuperLine)se.profLine);
//						MegaFacade mf = (MegaFacade) sl.properties.get( MegaFacade.class.getName() );
//						
//						List<Prof> pfs = mf.getTween( se.start, se.end, 0.3 );
//						
//						if (!pfs.isEmpty())
//							bestProf = Prof.parameterize( sl, pfs );
//						else {
//							bestProf = clean.get( 0 );
//							double bestScore = Double.MAX_VALUE;
//
//							for ( Prof c : clean ) {
//
//								double score = 0;
//								boolean good = false;
//								for ( Prof r : mf.getTween( se.start, se.end, 0.3 ) ) {
//									score += c.distance( r, true, false, true );
//									good = true;
//								}
//								if ( good && score < bestScore ) {
//									bestScore = score;
//									bestProf = c;
//								}
//
//							}
//						}
//						
//							
//					}
						
					
//					if (bestProf)
//					((SuperLine))
//					
					double[] fitV = profFit.get( se );

					if (fitV == null)
						continue;
					
					double bestScore = Double.MAX_VALUE;

					for ( int ii = 0; ii < fitV.length; ii++ ) {
						double d = fitV[ ii ];
						if ( d < bestScore ) {
							bestScore = d;
							bestProf = globalProfs.get( ii );
						}
					}
//					
//					double bestScore = Double.MAX_VALUE;
//
//					for ( int ii = 0; ii < fitV.length; ii++ ) {
//						double d = fitV[ ii ];
//						if ( d < bestScore ) {
//							bestScore = d;
//							bestProf = globalProfs.get( ii );
//						}
//					}
					
					// if (false)
//					se.prof = bestProf;
				}

				if ( bestProf != null ) {
					
					Geometry g = new Geometry();

					Point3d cen = Pointz.to3( se.line().fromPPram( 0.5 ) );

					Prof goodOrientation = Prof.buildProfile( Pointz.to3( se.line() ), cen );

					for ( Point2d p : bestProf )
						goodOrientation.add( p );

					g.setMesh( goodOrientation.renderStrip( 1.5, cen ) );
					g.setMaterial( mat );
					n.attachChild( g );

					g.updateGeometricState();
					g.updateModelBound();
				}
			}
		}
		
		skelGen.tweed.frame.addGen( new JmeGen( name, tweed, n ), false );
		
	}

	public void debugCompareProfs (List<Prof> profs) {
			
			JSlider slider1 = new JSlider( SwingConstants.VERTICAL, -1, profs.size() - 1, 0 );
			JSlider slider2 = new JSlider( SwingConstants.VERTICAL, -1, profs.size() - 1, 0 );
			final Plot pot = new Plot( slider1, slider2 );
			
			ChangeListener cl = new ChangeListener() {

				@Override
				public void stateChanged( ChangeEvent arg0 ) {
					pot.toPaint.clear();
					PaintThing.debug.clear();
					
					pot.toPaint.add(""+profs.size());
					
					if (slider1.getValue() == -1 || slider2.getValue() == -1 ) {
						
						for (Prof p : profs)
							PaintThing.debug (new Color(0,0,0,50), 1f, p);
						 
						pot.repaint();
						
						return;
					}
					
					Prof p = profs.get(slider1.getValue()),
						 d = profs.get(slider2.getValue());
					
					pot.toPaint.add( p );
					pot.toPaint.add( d );

					pot.toPaint.add("pair distance: "+
							d.distance( p, false, true, true )
					);
					
					pot.repaint();
				}
			};
			
			slider1.addChangeListener( cl ); 
			slider2.addChangeListener( cl ); 
		}
	
	private Comparator<Line> megaAreaComparator = new Comparator<Line>() {

		@Override
		public int compare( Line o1, Line o2 ) {
			return Double.compare( ( (SuperLine) o2 ).getMega().area, ( (SuperLine) o1 ).getMega().area );
		}
		
	};
}
