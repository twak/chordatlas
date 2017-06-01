package org.twak.tweed.gen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.ProgressMonitor;
import javax.vecmath.Point2d;

import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.FeatureGen.ImageFeatures;
import org.twak.tweed.gen.FeatureGen.MFPoint;
import org.twak.tweed.gen.FeatureGen.MegaFeatures;
import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.Cache2;
import org.twak.utils.DumbCluster1D;
import org.twak.utils.HalfMesh2;
import org.twak.utils.HalfMesh2.HalfEdge;
import org.twak.utils.HalfMesh2.HalfFace;
import org.twak.utils.Line;
import org.twak.utils.LinearForm;
import org.twak.utils.MUtils;
import org.twak.utils.MultiMap;
import org.twak.utils.PaintThing;

import gurobi.GRB;
import gurobi.GRB.DoubleAttr;
import gurobi.GRB.StringAttr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBQuadExpr;
import gurobi.GRBVar;

public class SkelSolver {

	Map<HalfEdge, EdgeVars> edgeInfo = new HashMap<>();
	Map<HalfFace, FaceVars> faceInfo = new HashMap<>();
	HalfMesh2 mesh;

	GRBModel model;
	GRBQuadExpr target;
	
	MultiMap<MegaFeatures, MFPoint> minis;
	Map<MegaFeatures, List<MiniPtCluster>> mega2clusters = new HashMap();
	
	List<Prof> globalProfs;
	Map<SuperEdge, double[]> profFit;
	List<HalfEdge> edges = new ArrayList();
	
	double totalEdgeLength = 0;
	
	private transient ProgressMonitor progress;
	
	boolean gurobiDone = false;
	
	final static int colors = 6;
	
	int countBadEdges = 0, countBadCorners = 0, countNearbyProfiles = 0;
	private SolverState ss;
	private long maxTimeSecs = Long.MAX_VALUE;
	
	public SkelSolver( SolverState ss, ProgressMonitor m ) {
		this (ss, m, Long.MAX_VALUE);
	}
	
	public SkelSolver( SolverState ss, ProgressMonitor m, long maxTimeSecs ) {
		this.ss = ss;
		this.mesh = ss.mesh;
		this.minis = ss.minis;
		this.globalProfs = ss.globalProfs;
		this.profFit = ss.profFit;
		this.progress = m;
		this.maxTimeSecs = maxTimeSecs;
	}
	
	public void dbg() {
		for ( HalfFace f : mesh.faces )
			for ( HalfEdge e : f.edges() ) {
				edges.add ( e );
				totalEdgeLength += e.length();
			}
		
		try {
			buildProblem();
		} catch ( GRBException e1 ) {
			e1.printStackTrace();
		}
	}
	
	public void solve() {
		
		try {
			
			for ( HalfFace f : mesh.faces )
				for ( HalfEdge e : f.edges() ) {
					edges.add ( e );
					totalEdgeLength += e.length();
				}
			
			print("total edge length " + totalEdgeLength);

			progress.setProgress( (int) ( Math.random() * 90 ) );

			buildProblem();
			model.getEnv().set( GRB.DoubleParam.TimeLimit, GRB.INFINITY );			
			
			long time = System.currentTimeMillis();
			
			new Thread( () -> {
				while (!gurobiDone) {
					
					try {
						Thread.sleep( 300 );
						
						if ( ( System.currentTimeMillis() - time ) / 1000 > maxTimeSecs ) {
							print( "time's up" );
							model.terminate();
							progress.close();
							return;
						}
						
					} catch ( InterruptedException e1 ) {}
					
					progress.setProgress( (int) ( Math.random() * 90 ) );
					
					if (progress.isCanceled()) {
						print("user cancelled");
						model.terminate();
					}
				}
				
				progress.setProgress(100);
				
			}).start();
			
			try {
  			    model.write( "./problem"+System.currentTimeMillis()+".mps");
				model.optimize();
			} finally {
				gurobiDone = true;				
				progress.setProgress(100);
			}
			
			print("time " + (System.currentTimeMillis() - time)/1000.+" seconds" );

		     if (model.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE ) {
		    	 print("Can't solve; won't solve");
		    	 return;
		     }

//			if (!edgeInfo.isEmpty())
//			for (int i = 0; i < edges.size(); i++) {
//				SuperEdge se = (SuperEdge ) edges.get( i );
//				se.debug =  edgeInfo.get(se).edgeNoProfile.get( GRB.DoubleAttr.X ) > 0.5;
//			}
			
			for (HalfFace f : mesh.faces)
				((SuperFace)f).classification = index (faceInfo.get(f).color);
			
			if (minis != null)
			for ( MegaFeatures mf : minis.keySet() ) {
				
				if (mega2clusters.containsKey( mf ))
				for (MiniPtCluster pt : mega2clusters.get(mf)) {
					
					for (Map.Entry<MFPoint, MiniSelectEdge> selectPt :  pt.entrySet() ) {
						
							for (Map.Entry<HalfEdge, GRBVar> selectEdge : selectPt.getValue().edge.entrySet()) {
								if (selectEdge.getValue().get(GRB.DoubleAttr.X) > 0.5 ) {
									
									HalfEdge e = selectEdge.getKey();
									
									selectPt.getKey().selectedEdge = e;
									selectPt.getKey().sameCluster = pt.keySet();
									
									if (e.over != null && e.next.over == null) {
										for (MFPoint p : pt.keySet())
											((SuperEdge)e.next).addMini ( p.right );
									
										for (MFPoint p : pt.keySet()) 
											((SuperEdge) e.over.findBefore()).addMini ( p.left );
									}
									
									if (e.over == null && e.next.over == null) {
										
										Line parallel = selectPt.getKey().mega.megafacade;
										
										if (parallel.absAngle(e.line()) < parallel.absAngle(e.next.line()))
											for (MFPoint p : pt.keySet())  
												((SuperEdge)e).addMini ( p.left );
										else
											for (MFPoint p : pt.keySet())  
												((SuperEdge)e.next).addMini ( p.right );
									}
								}
						}
					}
				}
			}
			
			if (false)
			for (HalfEdge he : edges) {
				if ( he.over != null && he.next.over == null ) { // face comes to boundary
					
					boolean viz = false;
					EdgeVars ei = edgeInfo.get( he );
					if (ei.edgeNoMini != null) {
//						if ( ei.edgeNoMini.get( DoubleAttr.X ) > 0.5 )
							viz = true;
					}
//					else if ( ei.isEdge.get( DoubleAttr.X ) > 0.5  ) {
//						viz = true;
//					}
					
					if (viz)
						PaintThing.debug.put("dd", new Point2d(he.end));
						
				}
			}
			
			for (HalfEdge e : edges) {
				
				EdgeVars ei = edgeInfo.get(e);
				GRBVar plot = ei.debug;
				
				if (plot != null) {// && plot.get(GRB.DoubleAttr.X) > 0.5 ) { 
					Point2d o = new Point2d (e.end);
					PaintThing.debug.put( 1, o );
				}
				
				int p = index( ei.profile );
				((SuperEdge)e).profI = p;
				((SuperEdge)e).prof = p < 0 ? null : globalProfs.get(p);
			}

			dumpModelStats(edges);
			
			model.getEnv().dispose();
			model.dispose();

		} catch ( GRBException e ) {
			print( "Error code: " + e.getErrorCode() + ". " + e.getMessage() );
			e.printStackTrace();
		}
	}

	public void buildProblem() throws GRBException {

		model = new GRBModel( new GRBEnv( "./"+System.currentTimeMillis()+".log" ) );
		target = new GRBQuadExpr();
		edgeInfo = new HashMap<>();
		faceInfo = new HashMap<>();
		
		buildColouringProblem();
		buildIsEdge();
		buildBadGeom( false );
		
		if (minis != null && !minis.isEmpty())
			buildMini();
		
		buildProfiles();
		
		for (HalfEdge he : edges) {
			target.addTerm( 10  * he.length(), edgeInfo.get(he).edgeNoProfile );
			target.addTerm( 20 * he.length(), edgeInfo.get(he).profileNoEdge );
//			target.addTerm( 50 * he.length(), edgeInfo.get(he).isEdge );
		}
		
		double isEdgeHeight = 0, isNotEdgeHeight = 0;
		
		for ( HalfEdge e : edges ) {
			double cost;
			if ( e.over != null ) {
				cost = e.length() * Math.abs( ( (SuperFace) e.face ).height - ( (SuperFace) e.over.face ).height );
				isEdgeHeight +=cost;
				isNotEdgeHeight += cost;
//				target.addTerm (      cost, edgeInfo.get(e).isNotEdge );
				target.addTerm ( -4 * cost, edgeInfo.get(e).isEdge    );
			}
		}
		
		print ("is not edge height penalty " + isNotEdgeHeight + " (" +(isNotEdgeHeight / faceInfo.size()) +")" );
		print ("is edge height penalty "     + isEdgeHeight );

		model.setObjective( target, GRB.MINIMIZE );
	}

	private static int index(GRBVar[] vars) throws GRBException{
		
		if (vars == null)
			return -1;
		
		for (int i = 0; i < vars.length; i++)
			if (vars[i].get( GRB.DoubleAttr.X ) > 0.5 )
				return i;
		return -1;
	}
	
	Map<GRBVar, Double> setVars = new HashMap<>();
	
	private void set (GRBVar var, double val) throws GRBException {
		var.set (DoubleAttr.Start, val);
		setVars.put( var, val);
	}
	
	
	private static class FaceVars {
		public GRBVar[] color;
	}
	private static class EdgeVars {

		GRBVar isEdge;//, isNotEdge;
		int hasProfile;
		GRBVar edgeNoProfile, profileNoEdge;
		public GRBVar isMini; // only set on boundaries
		public GRBVar edgeNoMini;
		public GRBVar[] profile;
		public GRBVar debug;
		
		public EdgeVars( GRBVar isEdge, GRBVar isNotEdge, int hasProfile, GRBVar edgeNoProfile, GRBVar profileNoEdge ) {
			
			this.isEdge = isEdge;
//			this.isNotEdge = isNotEdge;
			this.hasProfile = hasProfile;
			this.edgeNoProfile = edgeNoProfile;
			this.profileNoEdge = profileNoEdge;
			
//			try {
//				isEdge.set( DoubleAttr.Start, 1 );
//				isNotEdge.set( DoubleAttr.Start, 0 );
//				edgeNoProfile.set( DoubleAttr.Start, 1-hasProfile );
//				profileNoEdge.set( DoubleAttr.Start, 0 );
//			} catch ( GRBException e ) {
//				e.printStackTrace();
//			}
		}

		public void startAt( EdgeVars old ) throws GRBException {

			isEdge        .set( DoubleAttr.Start, old.isEdge       .get ( DoubleAttr.X ) );
//			isNotEdge     .set( DoubleAttr.Start, old.isNotEdge    .get ( DoubleAttr.X ) );
			hasProfile = old.hasProfile;
			edgeNoProfile .set( DoubleAttr.Start, old.edgeNoProfile.get ( DoubleAttr.X ) );
			profileNoEdge .set( DoubleAttr.Start, old.profileNoEdge.get ( DoubleAttr.X ) );
			if (old.isMini != null)
				isMini    .set( DoubleAttr.Start, old.isMini       .get ( DoubleAttr.X ) );
			if (old.edgeNoMini != null)
				edgeNoMini.set( DoubleAttr.Start, old.edgeNoMini   .get ( DoubleAttr.X ) );
			
		}
	}
	
	private final static String IS_EDGE = "is edge", IS_NOT_EDGE = "is not edge",
			EDGE_NO_PROFILE = "edge not profile", PROFILE_NO_EDGE = "profile not edge", XOR="xor", FACE_COLOR = "face color",
					EDGE_NO_MINI = "edge", PROFILE_SELECT = "profile select", PROFILE_DIFFERENT = "profile different",
					IS_MINI = "is mini", MINI_TO_EDGE = "mini to edge", BAD_GEOM = "bad geometry";
			

	private void buildColouringProblem() throws GRBException {
		
		Random randy = new Random();
		
		
		for ( int f = 0; f < mesh.faces.size(); f++ ) { // create face colors

			SuperFace sf = (SuperFace) mesh.faces.get( f );
//			f2i.put( sf, f );
			
//			int startCol = 0;
//			
//			for (HalfFace n : sf.getNeighbours()) {
//				Integer ni;
//				if ( (ni = f2i.get( (SuperFace) n ) ) != null ) {
//					
//					int nc = -1;
//					for ( int c = 0; c < colors; c++ )  
//						if ( setVars.get(  xfc[ni][c] ) == 1)
//							nc = c;
//					
//					if (nc == startCol)
//						startCol++;
//					
//				}
//			}
//			if (startCol == colors)
			int	startCol = randy.nextInt( colors );
			
			FaceVars fv;
			faceInfo.put (sf, fv = new FaceVars() );
			fv.color = new GRBVar[colors];
			
			for ( int c = 0; c < colors; c++ ) { 
				fv.color[ c ] = model.addVar( 0.0, 1.0, 0.0, GRB.BINARY, FACE_COLOR );
				set ( fv.color[ c ], c == startCol ? 1 : 0 );
			}
		}
		
//		for ( int f = 0; f < mesh.faces.size(); f++ ) { // pick one color per face

		for (HalfFace f : mesh) {
			
			FaceVars fv = faceInfo.get(f);
			
			GRBLinExpr expr = new GRBLinExpr();

			for ( int c = 0; c < colors; c++ ) 
				expr.addTerm( 1, fv.color[ c ] );

			model.addConstr( expr, GRB.EQUAL, 1, "overlap_" + f );
		}
	}
	
	private  void buildIsEdge () throws GRBException {
		
		
		for ( int e = 0; e < edges.size(); e++) {
			
			SuperEdge se = (SuperEdge) edges.get(e);
			edgeInfo.put( se, new EdgeVars (
					model.addVar( 0.0, 1.0, 0, GRB.BINARY, IS_EDGE ),
					model.addVar( 0.0, 1.0, 0, GRB.BINARY, IS_NOT_EDGE  ),
					se.profLine != null || ( se.over != null && ((SuperEdge)se.over).profLine != null ) ? 1 : 0,
					model.addVar( 0.0, 1.0, 0, GRB.BINARY, EDGE_NO_PROFILE ),
					model.addVar( 0.0, 1.0, 0, GRB.BINARY, PROFILE_NO_EDGE )
				) );
			
			GRBLinExpr notEdge = new GRBLinExpr();
			
			notEdge.addConstant( 1 );
			notEdge.addTerm( -1, edgeInfo.get(se).isEdge );
			
			model.addConstr( notEdge, GRB.EQUAL, notEdge, "is not isEdge" );
		}
		
		for ( int e = 0; e < edges.size(); e++) {

				SuperEdge se = (SuperEdge) edges.get(e);
				
				EdgeVars ei = edgeInfo.get(se);
				
				if ( se.over == null ) {
					model.addConstr( ei.isEdge, GRB.EQUAL, 1, "is outside edge" );
					continue;
				}
				
				buildIsDifferentColor( ei.isEdge, faceInfo.get( ( SuperFace )se.face ).color,
						faceInfo.get( ( SuperFace )se.over.face ).color, "face colouring" );
				
				GRBLinExpr expr;
				
				{ // edge no profile from isEdge, hasProfile
					expr = new GRBLinExpr();
					expr.addTerm( 1, ei.isEdge );
					expr.addConstant( ei.hasProfile );
					model.addConstr( ei.edgeNoProfile, GRB.LESS_EQUAL, expr, null );
				
					expr = new GRBLinExpr();
					expr.addTerm( 1, ei.edgeNoProfile );
					expr.addConstant( ei.hasProfile );
					model.addConstr( expr, GRB.LESS_EQUAL, 1, null );
					
					expr = new GRBLinExpr();
					expr.addTerm(  1, ei.isEdge );
					expr.addConstant( -ei.hasProfile );
					model.addConstr( ei.edgeNoProfile, GRB.GREATER_EQUAL, expr, null );
				}
				
				{ // profile no edge from isEdge, hasProfile
					expr = new GRBLinExpr();
					expr.addTerm( 1, ei.isEdge );
					expr.addConstant( ei.hasProfile );
					model.addConstr( ei.profileNoEdge, GRB.LESS_EQUAL, expr, null );
					
					expr = new GRBLinExpr();
					expr.addTerm( 1, ei.profileNoEdge );
					expr.addTerm( 1, ei.isEdge );
					model.addConstr( expr, GRB.LESS_EQUAL, 1, null );
					
					expr = new GRBLinExpr();
					expr.addTerm( -1, ei.isEdge );
					expr.addConstant( ei.hasProfile );
					model.addConstr( ei.profileNoEdge, GRB.GREATER_EQUAL, expr, null );
				}
		}
	}

	private void buildIsDifferentColor( GRBVar isDifferent, GRBVar[] ac, GRBVar[] bc, String desc ) throws GRBException {
		GRBLinExpr isEdgeEx = new GRBLinExpr();
		GRBLinExpr expr;
		
		for ( int c = 0; c < ac.length; c++ ) {

			GRBVar  a   = ac[c],
					b   = bc[c],
					xor = model.addVar( 0.0, 1.0, 0, GRB.BINARY, XOR + " "+desc );
			
//			if (setVars.containsKey( a ) && setVars.containsKey( b ))
//			xor.set( DoubleAttr.Start,  (setVars.get(a) == 1) ^ ( setVars.get(b) == 1 ) ? 1 : 0 );
			
			expr = new GRBLinExpr();
			expr.addTerm( 1, a );
			expr.addTerm( 1, b );
			model.addConstr( xor, GRB.LESS_EQUAL, expr, "same color " + c );
			
			expr = new GRBLinExpr();
			expr.addTerm( 1, a );
			expr.addTerm( -1, b );
			model.addConstr( xor, GRB.GREATER_EQUAL, expr, "same color " + c );
			
			expr = new GRBLinExpr();
			expr.addTerm( -1, a );
			expr.addTerm(  1, b );
			model.addConstr( xor, GRB.GREATER_EQUAL, expr, "same color " + c );
			
			expr = new GRBLinExpr();
			expr.addTerm( 1, a );
			expr.addTerm( 1, b );
			expr.addTerm( 1, xor );
			model.addConstr( expr, GRB.LESS_EQUAL, 2, "same color " + c );

			isEdgeEx.addTerm(1, xor);
		}
		
		isEdgeEx.addTerm( -2,  isDifferent);
		
		model.addConstr( isEdgeEx, GRB.LESS_EQUAL,  0.5, "is different " +desc );
		model.addConstr( isEdgeEx, GRB.GREATER_EQUAL, -1.5, "is different " +desc );
	}

	public static List<HalfEdge> findNear( Line l, Point2d start, HalfMesh2 mesh2 ) {
		
		double tol = 4;
		
		l = new Line(l);
		l.moveLeft( tol );
		
		List<HalfEdge> out = new ArrayList<>();
		
		for (HalfFace f : mesh2) 
			try {
			for (HalfEdge e : f) {
				
				if ( 
						e.end.distanceSquared(start) < tol * tol &&
						l.isOnLeft(e.start) && l.isOnLeft ( e.end ) &&
						MUtils.inRangeTol( e.line().absAngle( e.next.line() ), MUtils.PI2, 1 )  )
					{
						double angle = l.absAngle( e.line() );
						if ( ( e.next.over == null && MUtils.inRangeTol( angle, MUtils.PI2, 1 ) ) || // regular T-jn on edge or left corner
						     ( e.over == null && e.next.over == null && angle < 1) ) // right corner	
							out.add(e);
					}
			}
			}
		catch (Throwable th) {
			th.printStackTrace();
		}
		
		return out;
	}

	
	static class MiniPtCluster extends HashMap<MFPoint, MiniSelectEdge> { // select a single point on the minifacade
		public Point2d mean;
	} 
	
	static class MiniSelectEdge { // given that we select this MFPoint, which edge do we select?
		Map<HalfEdge, GRBVar> edge = new HashMap<>();
		public MiniSelectEdge() {}
	}
	
	private void buildMini( ) throws GRBException {
		
		Cache<HalfEdge, GRBLinExpr> isMiniExpr = new Cach<HalfMesh2.HalfEdge, GRBLinExpr>( he -> new GRBLinExpr() );
		
		if (minis == null)
			return;
		
		for ( MegaFeatures mf : minis.keySet() ) {
			
			List<MiniPtCluster> clusterVars = new ArrayList<>();
			mega2clusters.put(mf,clusterVars);
			
			double mLen = mf.megafacade.length();
			
			DumbCluster1D<MFPoint> clusters = clusterMinis( mf, minis );
			
			for (DumbCluster1D.Cluster<MFPoint> d : clusters) { // for each cluster, we pick a single MFPoint as the boundary
				
				
				MiniPtCluster miniPtVar = new MiniPtCluster();
				
				Cache<HalfEdge, GRBLinExpr> isEdgeBind = new Cach<HalfMesh2.HalfEdge, GRBLinExpr>( he -> new GRBLinExpr() );
				
				GRBLinExpr selectHE = new GRBLinExpr();
				
				boolean one = false;
				
				for ( MFPoint pt : d.things ) {

					MiniSelectEdge miniSelectEdge = new MiniSelectEdge();
					miniPtVar.put( pt, miniSelectEdge );

					List<HalfEdge> nearCorners = findNear( mf.megafacade, pt, mesh );

					try {
						for ( HalfEdge he : nearCorners ) {

							GRBVar heVar = model.addVar( 0.0, 1.0, 0, GRB.BINARY, MINI_TO_EDGE );

							miniSelectEdge.edge.put( he, heVar );

							selectHE.addTerm( 1, heVar );

							double cost = he.end.distance( pt );

							if ( he.over != null ) { // not corner

								if ( pt.right != null )
									cost += Math.abs( pt.right.height - ( (SuperFace) he.face ).height );

								if ( pt.left != null )
									cost += Math.abs( pt.left.height - ( (SuperFace) he.over.face ).height );

								isEdgeBind.get( he ).addTerm( 1, heVar );
							} else
								cost -= totalEdgeLength * 0.1; // bonus for being on a corner;

							target.addTerm( cost, heVar );

							isMiniExpr.get( he ).addTerm( 1, heVar );

							one = true;
						}
					} catch ( Throwable th ) {
						th.printStackTrace();
					}
				}
				
				if (one) {
					clusterVars.add(miniPtVar);
					model.addConstr( selectHE, GRB.EQUAL, 1, "pick one near " + d.things.iterator().next() );
				}
				else
					print("warning skipping minifacade loction " + d.things.size());
				
				for (HalfEdge he : isEdgeBind.cache.keySet()) 
					model.addConstr( isEdgeBind.get( he ), GRB.EQUAL, edgeInfo.get(he).isEdge, "minifacade boundary must terminate on edge "+ he );
				
				
				miniPtVar.mean = mf.megafacade.fromPPram( d.mean / mLen );
			}
		}
		
		double penalty = totalEdgeLength * 0.1;
		for (HalfEdge he : edges) {
			
			
			if ( he.over != null && he.next.over == null ) { // edge comes to boundary without minifacade --> penalty
				
				OptionalDouble miniDist = minis.keySet().stream().
						map( mf -> mf.megafacade ).
						mapToDouble( line -> line.distance(he.end, true) ).min();
				
				if ( !miniDist.isPresent() || miniDist.getAsDouble() > 4 )
					continue;
				
				EdgeVars ei = edgeInfo.get( he ); 
				
				ei.edgeNoMini = model.addVar( 0.0, 1.0, 0, GRB.BINARY, EDGE_NO_MINI );
				
				if ( isMiniExpr.cache.containsKey( he ) ) {
					
					ei.isMini =  model.addVar( 0.0, 1.0, 0, GRB.BINARY, IS_MINI );
					
					GRBLinExpr is = isMiniExpr.get( he ); /* ei.isMini might take 0 or positive integer... assume it's below 10 (0.1 = 1/10) */
					model.addConstr(  ei.isMini      , GRB.LESS_EQUAL, is, "is minifacade on edge "+ he );
					model.addConstr(  scale (0.1, is), GRB.LESS_EQUAL, ei.isMini, "is minifacade on edge "+ he );
					
					GRBLinExpr expr = new GRBLinExpr();
					expr.addTerm( 1, ei.isEdge );
					expr.addTerm( 1, ei.isMini );
					model.addConstr( ei.edgeNoMini, GRB.LESS_EQUAL, expr, null );
					
					expr = new GRBLinExpr();
					expr.addTerm( 1, ei.edgeNoMini );
					expr.addTerm( 1, ei.isMini );
					model.addConstr( expr, GRB.LESS_EQUAL, 1, null );
					
					expr = new GRBLinExpr();
					expr.addTerm(  1, ei.isEdge );
					expr.addTerm( -1, ei.isMini );
					model.addConstr( ei.edgeNoMini, GRB.GREATER_EQUAL, expr, null );
					
					
				} else { // no mini, but easier debug
					model.addConstr( ei.edgeNoMini, GRB.EQUAL, ei.isEdge, null );
				}
				
				target.addTerm( penalty, ei.edgeNoMini );
							
			}
		}
	}

	public static DumbCluster1D<MFPoint> clusterMinis( MegaFeatures mf, MultiMap<MegaFeatures, MFPoint> minis ) {

		double mLen = mf.megafacade.length();
		
		DumbCluster1D<MFPoint> clusters = new DumbCluster1D<MFPoint>(4, minis.get(mf)) {
			@Override
			public double toDouble( MFPoint pt ) {
				return mf.megafacade.findPPram( pt ) * mLen;
			}
		};
		
		Iterator<DumbCluster1D.Cluster<MFPoint>> dit = clusters.iterator(); // remove unpopular facade edges
		while (dit.hasNext()) {

			DumbCluster1D.Cluster<MFPoint> d = dit.next();
			
			Set<ImageFeatures> coveringImages = d.things.stream().flatMap( p -> p.covering.stream() ).collect( Collectors.toSet() );
			Set<ImageFeatures> usedImages = d.things.stream().map( p -> p.image ).collect( Collectors.toSet() );
			
			if (usedImages.size() < coveringImages.size() - usedImages.size())
				dit.remove();
		}
		return clusters;
	}
	
	private GRBLinExpr scale(double scale, GRBLinExpr is) throws GRBException {
		
		if (scale == 1)
			return is;
		
		GRBLinExpr out = new GRBLinExpr();
		
		for (int i = 0; i < is.size(); i++) {
			out.addTerm(is.getCoeff(i) * scale, is.getVar(i));
		}
		
		out.addConstant(is.getConstant() * scale);
		
		return out;
	}

	private void buildProfiles() throws GRBException {
		
		
		for (HalfEdge e : edges) {
			
			if ( ((SuperEdge)e).profLine == null) // when a profile ends, we assume it can't start again...
				continue;
			
			EdgeVars ev = edgeInfo.get( e );
			ev.profile = new GRBVar[globalProfs.size()];
			
			for (int p = 0; p < globalProfs.size(); p++)  {
				ev.profile[p] = model.addVar( 0.0, 1.0, 0, GRB.BINARY, PROFILE_SELECT);
				set ( ev.profile[p], p==0 ? 1 : 0 );
			}
		}
		
		
		Cache2<HalfEdge, HalfEdge, GRBVar> isProfileDifferent = new Cache2<HalfMesh2.HalfEdge, HalfMesh2.HalfEdge, GRBVar>() {

			@Override
			public GRBVar create( HalfEdge e1, HalfEdge e2 ) {
				try {
					GRBVar s =  model.addVar( 0.0, 1.0, 0.0, GRB.BINARY, PROFILE_DIFFERENT );
					buildIsDifferentColor( s, edgeInfo.get(e1).profile, edgeInfo.get(e2).profile, "profile" );
					s.set( DoubleAttr.Start, 0 );
					return s;
				} catch ( GRBException e ) {
					e.printStackTrace();
				}

				return null;
			}
		}; 
		
		for (HalfEdge e1 : edges) {
			
//			if (e1.over != null)
//				continue;
			

			EdgeVars ev = edgeInfo.get( e1 );
			
			if (ev.profile == null)
				continue;
				
			
			double[] fit = profFit.get(e1);
			
			GRBLinExpr pickOneProfile = new GRBLinExpr();
			for (int p = 0; p < globalProfs.size(); p++) {
				GRBVar a = ev.profile[p];
				pickOneProfile.addTerm( 1, a );
				target.addTerm( .001*fit[p] * e1.length(), a );
			}
			
			model.addConstr( pickOneProfile, GRB.EQUAL, 1 /*ev.isEdge*/, "pick only one profile for "+ e1  );
			
			List<HalfEdge> atEnd = e1.collectAroundEnd();
			
			for (HalfEdge e2 : atEnd ) {
				if ( 
					  e1 != e2  &&
					  edgeInfo.get(e2).profile != null && 
					  e1.line().absAngle( e2.line() ) < 0.1 // only constrain if ~parallel
					) {
					
					GRBVar s = isProfileDifferent.get(e1, e2);
					
//					ev.debug = s;
					
//					Point2d dbg = new Point2d(e1.end);
//					dbg.add(new Point2d(Math.random() * 0.1, Math.random() * 0.1));
					
					GRBLinExpr perpIsEdge = new GRBLinExpr();
					
					for (HalfEdge e3 : atEnd) {
						
						if (e3 == e1 || e3 == e2 || e3 == e1.over || e3 == e2.over || 
								(e3.over != null && ( e3.over == e1.over || e3.over == e2.over ) ) )
							continue;
						
						if (!e1.line().isOnLeft( 
								e1.end.distanceSquared( e3.start ) > e1.end.distanceSquared( e3.end ) ? 
										e3.start : e3.end ) )
							continue;
						
						perpIsEdge.addTerm(1, edgeInfo.get(e3).isEdge);
						
//						PaintThing.debug.put(e2, dbg);
//						Point2d d2 = new Point2d(e3.line().dir());
//						d2.scale (0.1/new Vector2d(d2).length());
//						d2.add(dbg);
//						PaintThing.debug.put(e2, new Line (dbg, d2));
						
//						model.addConstr( s, GRB.LESS_EQUAL, edgeInfo.get(e3).isEdge, "only change profile if no adjacent edge "+e1 );
					}
					
					model.addConstr( s, GRB.LESS_EQUAL, perpIsEdge, "dont' change profile over "+e1 );
					
//					PaintThing.debug.put(e2, dbg);
//					Point2d d2 = new Point2d(e2.line().dir());
//					d2.scale (0.2/new Vector2d(d2).length());
//					d2.add(dbg);
//					PaintThing.debug.put(e2, new Line (dbg, d2));
				}
			}
		}
		
		countNearbyProfiles = 0;
		
		if (false)
		for (int i = 0; i < edges.size(); i++) {
			
			HalfEdge e1 = edges.get(i);
			
			if ( edgeInfo.get(e1).profile == null)
				continue;
			
			print("building edge locality term " + i +" / " + edges.size() );
			
			for (HalfEdge e2 : edges ) 
				if (lt (e1, e2) &&
				    edgeInfo.get(e2).profile != null) {
					
					if (e1.line().distance( e2.line() ) < 2) {
						GRBVar s = isProfileDifferent.get(e1, e2);
						target.addTerm( 0.1 *  ( e1.length() + e2.length() ), s );
						countNearbyProfiles++;
					}
				}
		}
	}
	
	private static boolean lt (HalfEdge a, HalfEdge b) { // arbitrary & consistent ordering over a,b
		return MUtils.order(a.start.x, b.start.x, a.start.y, b.start.y, a.end.x, b.end.x, a.end.y, b.end.y);
	}

	private void buildBadGeom( boolean isContraint ) throws GRBException {
		
		countBadCorners = countBadEdges = 0;
		
		for (HalfEdge e1 : edges) {
			if ( e1.over != null && e1.next.over != null )
				if ( e1.line().absAngle( e1.next.line() ) > Math.PI - TweedSettings.settings.badGeomAngle ) {
					notBoth(e1, e1.next, isContraint, totalEdgeLength * 0.5);
					countBadCorners++;
				}
		}
		
		// if over both h
		for (HalfFace f : mesh) {
			Set<HalfEdge> togo = new HashSet<>();
			for (HalfEdge e : f) 
				togo.add(e);
			
			while (!togo.isEmpty()) {

				HalfEdge start = togo.iterator().next();
				togo.remove( start );
				
//				if (start.over == null)
//					continue;
				
				boolean parallelHasOverProfile = start.over == null ? false : ((SuperEdge) start.over).profLine != null,
						oppositeHasOverProfile = false;
				
				Line sl = start.line();
				LinearForm slf = new LinearForm (sl);
				
				Map<HalfFace, HalfEdge> parallel = new HashMap<>();
				
				parallel.put(start.over == null ? null : start.over.face, start);
				
 				Iterator <HalfEdge> tig = togo.iterator();
				while (tig.hasNext() ) {
					HalfEdge e2 = tig.next();
					HalfFace f2 = e2.over == null ? null : e2.over.face;
					if ( sl.absAngle( e2.line() ) < 0.05) {
						parallel.put( f2, e2); // one from each non-f face
						tig.remove();
						
						parallelHasOverProfile |= (e2.over != null && ((SuperEdge) e2.over).profLine != null);
					}
				}
				
				Map<HalfFace, HalfEdge> opposite = new HashMap();
				tig = togo.iterator();
				while (tig.hasNext() ) {
					HalfEdge e2 = tig.next();
					HalfFace f2 = e2.over == null ? null : e2.over.face;
					if (
//			  				   e2.over != null &&
//							   e1l.lengthSquared() > 1 && 
//							   e2l.lengthSquared() > 1 &&
			  				   sl.absAngle( e2.line() ) > Math.PI - 0.3  &&
 	  						 ( slf.distance( e2.start ) < 0.3 ||  
 	  						   slf.distance( e2.end   ) < 0.3 ) 
 	  						 ){
						opposite.put(f2, e2);
						tig.remove();
						
						oppositeHasOverProfile |= (e2.over != null &&  ((SuperEdge) e2.over).profLine != null );
					}
				}

				if (parallelHasOverProfile && oppositeHasOverProfile)
					continue;
				
				for (HalfEdge e1 : parallel.values())
					for (HalfEdge e2 : opposite.values()) {
						notBoth(e1, e2, isContraint, totalEdgeLength * 0.5);
						countBadEdges++;
					}
			}
		}
		
		print( countBadCorners + " " + countBadEdges );
		
	}
	
	private void notBoth( HalfEdge e1, HalfEdge e2, boolean isContraint, double badThingsAreBad ) throws GRBException {
		
		GRBLinExpr no = new GRBLinExpr();
		no.addTerm( 1, edgeInfo.get( e1 ).isEdge );
		no.addTerm( 1, edgeInfo.get( e2 ).isEdge );
		
		if (isContraint) {
			model.addConstr( no, GRB.LESS_EQUAL, 1, "bad geom < 1" );
		}
		else {
			GRBVar bothSelected = model.addVar( 0.0, 1.0, 0, GRB.BINARY, BAD_GEOM);
			no.addTerm( -2, bothSelected );
			model.addConstr( no, GRB.LESS_EQUAL, 1, "bad geom <" );
			model.addConstr( no, GRB.GREATER_EQUAL, -0.5, "bad geom >" );
			target.addTerm( badThingsAreBad,  bothSelected );

			{
				Line l1 = e1.line(), l2 = e2.line();
				
				l1.start = new Point2d(l1.start);
				l1.end   = new Point2d(l1.end  );
				l2.start = new Point2d(l2.start);
				l2.end   = new Point2d(l2.end  );
				
				l1.moveLeft( -0.1 );
				l2.moveLeft( -0.1 );
				
				PaintThing.debug( new Color (255,170,0), 2, l1 );
				PaintThing.debug( new Color (170,0,255), 2, l2 );

				edgeInfo.get( e1 ).debug = bothSelected;
			}
		}
	}

	private static double maxDistSq (Line a, Line b) {
		
		Line al = projLine (a, b), bl = projLine( b, a );
		
		return MUtils.max ( 
					al.start.distanceSquared( bl.start ),  
					al.start.distanceSquared( bl.end ),  
					al.end.distanceSquared( bl.start ),  
					al.end.distanceSquared( bl.end )  
				);
	}
	
	private static Line projLine( Line a, Line b ) {
		
		double s = MUtils.clamp01 ( a.findPPram( b.start ) ),
				   e = MUtils.clamp01 ( a.findPPram( b.end ) );
			
			return new Line (a.fromPPram( s ), a.fromPPram( e ));
	}

	private void dumpModelStats(List<HalfEdge> edges) throws GRBException {

		print ("profile count " + globalProfs.size());
		print ("acute corner constraints " + countBadCorners);
		print ("parallel edge constraints " + countBadEdges);
		print ("nearby profile terms " + countNearbyProfiles);
		print ("total half edges " + edges.size() );
		print ("total faces " + faceInfo.size() );
		if ( profFit.get( edges.get(0)  ) != null)
			print ("total profiles " + profFit.get( edges.get(0)  ).length );
		print( "total edge length " + totalEdgeLength );
		
		print( "\nobjective fn breakdown follows...\n" );

		Cache<String, Double> penalties = new Cach<String, Double>( x -> 0. );
		Cache<String, Integer> count = new Cach<String, Integer>( x -> 0 );

		for ( GRBVar v : model.getVars() ) {
			penalties.cache.put( v.get( StringAttr.VarName ), 
					penalties.get( v.get( StringAttr.VarName ) ) + v.get( GRB.DoubleAttr.Obj ) * v.get( GRB.DoubleAttr.X ) );
			
			count.cache.put( v.get( StringAttr.VarName ), count.get( v.get( StringAttr.VarName ) ) + 1 );
		}

		List<String> vals = new ArrayList<>( penalties.cache.keySet() );
		Collections.sort( vals );

		double obj = penalties.cache.values().stream().mapToDouble( x -> x ).sum();

		for ( String k : vals )
			print( k + "(" + count.get( k ) + ") :: " + penalties.get( k ) + " " );

		print( "\ntotal obj " + obj + "\n\n" );

	}
	


	private void print (String s) {
		System.out.println(s);
		ss.dbgInfo.append( s +"\n" );
	}
	
}
