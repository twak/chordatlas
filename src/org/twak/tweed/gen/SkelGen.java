package org.twak.tweed.gen;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.WindowConstants;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Skeleton;
import org.twak.camp.Tag;
import org.twak.camp.ui.Bar;
import org.twak.mmg.MOgram;
import org.twak.mmg.media.Facade2d;
import org.twak.mmg.ui.MOgramEditor;
import org.twak.siteplan.campskeleton.Plan;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.Profile;
import org.twak.siteplan.campskeleton.Siteplan;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.ProfileGen.MegaFacade;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.SuperLine;
import org.twak.viewTrace.facades.Greeble;
import org.twak.viewTrace.facades.Greeble.OnClick;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.Regularizer;
import org.twak.viewTrace.facades.RoofTag;
import org.twak.viewTrace.facades.WallTag;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class SkelGen extends Gen implements IDumpObjs {


	BlockGen blockGen;

	public HalfMesh2 toRender = null;

	public SkelFootprint skelFootprint = new SkelFootprint();
	protected List<Line> footprint;
	
	public SkelGen() {
		super("headless", null);
	}
	
	public SkelGen( Tweed tweed ) {
		super( "house", tweed );
	}
	
	public SkelGen( List<Line> footprint, Tweed tweed, BlockGen blockGen ) {
		super( "skel", tweed );
		this.footprint = footprint;
		this.blockGen = blockGen;
		
		ProgressMonitor m = new ProgressMonitor( tweed.frame(), "Optimizing", "", 0, 100 );

		m.setProgress( 1 );
		m.setMillisToPopup( 0 );
		
		new Thread( () -> { 
			doCalc(m);
			SkelGen.this.calculateOnJmeThread(); } ).start();
	}
	
	public SkelGen( HalfMesh2 mesh, Tweed tweed, BlockGen blockGen ) {
		
		super( "skel", tweed );
		
		this.toRender = mesh;
		
		ProgressMonitor m = new ProgressMonitor( tweed.frame(), "Optimizing", "", 0, 100 );
		
		m.setProgress( 1 );
		m.setMillisToPopup( 0 );
	}
	
	private void doCalc(ProgressMonitor m) {
		toRender = skelFootprint.go(footprint, this, m, tweed);
	}

	@Override
	public void calculate() {

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();

		Node pNode = new Node();
		
		if ( toRender != null )
			for (int i = 0; i < toRender.faces.size(); i++)
				try {
					HalfFace f = toRender.faces.get( i );
					
					SuperFace sf = (SuperFace)f;
					PlanSkeleton skel = calc (sf);
					if (skel != null)
						setSkel( skel, skel.output , sf);
					
			}
			catch (Throwable th) {
				th.printStackTrace();
			}
		
		

		if (!pNode.getChildren().isEmpty())
			tweed.frame.addGen( new JmeGen( "sProfs", tweed, pNode ), false );
		
		gNode.updateModelBound();
		gNode.updateGeometricState();
		
		super.calculate();
	}

//	private List<Debug> debug = new ArrayList();
//	static class Debug {
//		List<Prof> profs = new ArrayList();
//		List<Prof> cleans = new ArrayList();	
//		Prof clean;
//	}
	
	protected static PlanSkeleton calc( SuperFace sf ) {
		
		double pHeight = findHeightFromProfiles( sf );
		
		PlanSkeleton skelP = buildCamp( sf, pHeight ); // good for pointy roofs
		PlanSkeleton skelS = buildCamp( sf, sf.height ); // good for flat roofs
		
		PlanSkeleton skel;
		
		if (skelP == null && skelS == null)
			return null;
		
		if (skelP == null)
			skel = skelS;
		else if (skelS == null)
			skel = skelP;
		else {
			if (skelP.capArea < 0.5 * skelS.capArea)
				skel = skelP;
			else
				skel = skelS;
		}
		
		return skel;
	}

	private static Double findHeightFromProfiles( SuperFace sf ) {

		Collections.sort( sf.maxProfHeights );

		if (sf.maxProfHeights.isEmpty())
			return sf.height;
		
		return sf.maxProfHeights.get( ( int ) (sf.maxProfHeights.size() * 0.9 ) ) + ProfileGen.HEIGHT_DELTA;
	}

	private static PlanSkeleton buildCamp( SuperFace sf, Double cap ) {
		
		Plan plan = new Plan();

		LoopL<Bar> loopl = new LoopL();
		Loop<Bar> loop = new Loop();
		loopl.add( loop );

		Cache<Point2d, Point2d> cache = new Cache<Point2d, Point2d>() {
			@Override
			public Point2d create( Point2d i ) {
				return new Point2d( i.x, i.y );
			}
		};

		LoopL<HalfEdge> edges = sf.findHoles();
		LoopL <Point2d> lpd = new LoopL();
		
		for ( Loop<HalfEdge> loopHE : edges ) {

			
			Map<Point2d, SuperEdge> ses = new HashMap();

			Loop<Point2d> lp = new Loop();
			lpd.add( lp );

			for ( HalfEdge he : loopHE ) {
				SuperEdge se = (SuperEdge) he;
				lp.append( se.start );
				ses.put( se.start, se );
			}

			lp = Loopz.mergeAdjacentEdges2( lp, 0.001 );
			
//			if ( Loopz.area( lpd ) < 5 )
//				return null;

			for ( Loopable<Point2d> lpb : lp.loopableIterator() ) {

				Bar b = new Bar( cache.get( lpb.getNext().get() ), cache.get( lpb.get() ) );
				
				SuperEdge se = ses.get( lpb.get() );

				Profile profile = null;

				if ( se.prof == null ) {

					List<Point2d> defpts = new ArrayList<>();
					defpts.add( new Point2d( 0, 0 ) );
					defpts.add( new Point2d( 0, -sf.height * 1.2 ) );

					profile = new Profile( defpts );
				} else {
					profile = toProfile( se.prof );
				}
				tagWalls( profile, ( (SuperFace) se.face ).roofColor, se, lpb.get(), lpb.getNext().get() );
				plan.addLoop( profile.points.get( 0 ), plan.root, profile );
				
				b.tags.add( new SETag(se) );
				
				loop.prepend( b );
				plan.profiles.put( b, profile );
			}
		}

		plan.points = loopl;
		
		PlanSkeleton skel = new PlanSkeleton( plan );
		
		if (cap != null)
			skel.capAt( cap, a -> skel.capArea = a );
		
		skel.skeleton();
		
		return skel;
	}

	private double maxZ( Output output ) {
		
		double maxZ = -Double.MAX_VALUE;
		
		for (Face f : output.faces.values())
			for (Loop<Point3d> ll : f.points)
				for (Point3d pt : ll)
					maxZ = Math.max(pt.z, maxZ);
			
		return maxZ;
	}

	Map<SuperFace, Node> geometry = new IdentityHashMap<>();
	MOgram mogram = null;
	
	public synchronized void setSkel( PlanSkeleton skel, Output output, SuperFace sf ) {
		
		if (geometry.get(sf) != null)
			geometry.get(sf).removeFromParent();

		Node house;
		
		OnClick onclick = new OnClick() {
			@Override
			public void selected( Output output, Node house2, SuperEdge se ) {
				SkelGen.this.selected(skel, house2, sf, se);
			}
		};
		
		if (mogram == null)
			house = new Greeble(tweed).showSkeleton( output, onclick );
		else
			house = new MMGGreeble(tweed).showSkeleton(output, onclick);
			
		gNode.attachChild( house );
		geometry.put(sf, house);

		tweed.getRootNode().updateGeometricState();
		tweed.getRootNode().updateModelBound();
		tweed.gainFocus();
		
	}

	private void selected( PlanSkeleton skel, Node house, SuperFace sf, SuperEdge se ) {
		
//		importFeatures (skel, geom);
		
		JPanel ui = new JPanel();
		ui.setLayout( new ListDownLayout() );
		

		JButton fac = new JButton("edit facade");
		
		fac.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				if (se.toEdit == null) {
					se.toEdit = new MiniFacade();
					se.toEdit.left = 0;
					se.toEdit.width = se.length();
					se.toEdit.height = 30;
					se.toEdit.groundFloorHeight = 2;
				}
					
				Plot p = new Plot (se.toEdit);
								
				p.addEditListener( new Changed() {
					@Override
					public void changed() {
						
						tweed.enqueue( new Runnable() {
							@Override
							public void run() {
								PlanSkeleton skel = calc (sf);
								setSkel( skel, skel.output , sf);
							}
						} );
					}
				} );
			}
		} );
		
		ui.add( fac );
		
		
		JButton camp = new JButton("siteplan");
		camp.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				Siteplan cs = new Siteplan( skel.plan, false ) {

					public void show( Output output, Skeleton threadKey ) {

						super.show( output, threadKey );

						tweed.enqueue( new Runnable() {
							@Override
							public void run() {
								
								setSkel( skel, output, sf);
								
							}
						} );
					};
				};
				cs.setVisible( true );
				cs.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
			}
		} );

		ui.add( camp );
		
		JButton mmg = new JButton("mmg");
		mmg.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				MOgram mg = buildMOGram (sf, se);
				
				new MOgramEditor( mg ).setVisible( true );;
			}
		} );
		
		ui.add( mmg );

		JButton mini = new JButton("minis");
		mini.addActionListener( e -> new MiniViewer( se ) );
		if (sf != null)
			ui.add(mini);
		
		JButton prof = new JButton("prof");
		prof.addActionListener( e -> new ProfileAssignmentViewer(sf, skelFootprint.globalProfs ) );
		ui.add( prof );
		
		JButton plan = new JButton("plan");
		plan.addActionListener( e -> new Plot (toRender, footprint) );
		ui.add( plan );
		
		JButton remove = new JButton("remove");
		remove.addActionListener( e -> {
			visible = false;
			calculateOnJmeThread();
			tweed.frame.removeGen( SkelGen.this );
		});
		ui.add( plan );
		
		tweed.frame.setGenUI(ui);
	}
	
	private MOgram buildMOGram( SuperFace sf, SuperEdge se ) {
		
		MOgram mg;
		
		if (se.mogram != null)
			mg = se.mogram;
		else {
			
			mg = se.mogram = new MOgram();
			
//			- compute to2d for sf 
//			- add face boundaries to mogram
//			- add windows to mogram
//			- label face boundaries
			
		}

		mg.medium = new Facade2d() {
			public void doRender( MOgram mogram ) {

				SkelGen.this.mogram = mogram;

				PlanSkeleton skel = calc( sf );
				if ( skel != null )
					setSkel( skel, skel.output, sf );
			};
		};
		
		return mg;
	}

	public static Profile tagWalls( Profile profile, float[] roofColor, SuperEdge se, Point2d s, Point2d e ) {

		MiniFacade mini;
		
		if (se.toEdit == null) { // first time through, use regularizer 
			 
			if ( se.mini == null || ( se.mini.isEmpty() && se.proceduralFacade == null ) )
				mini = null;
			else {
				
				double[] range = findRange( se, s, e, se.proceduralFacade == null ? null : se.proceduralFacade.megafacade );
				
				if (range == null)
					mini = null;
				else
					mini = new Regularizer().go( se.mini, range[0], range[1], se.proceduralFacade );
			}
		}
		else // second time through, use the edited results
			mini = se.toEdit;
		
		Tag wall = new WallTag ( se.profLine, se.occlusions, mini ), 
			roof = new RoofTag ( roofColor );
		
		boolean first = true;
		for (Loop<Bar> lb : profile.points) { 
			for (Loopable<Bar> ll : lb.loopableIterator()) {
				
				Bar b = ll.get();
				
				if (isRoof( b ) ) // || ll != lb.start && isRoof ( ll.getPrev().get() ) && b.start.distanceSquared( b.end ) < 16 )
					b.tags.add(roof);
				else {
					if (first)
						b.tags.add(new WallTag(se.profLine, se.occlusions, mini, true));
					else
						b.tags.add(wall);
				}
			}
			first = false;
		}
				
		return profile;
	}
	
	private static double[] findRange(SuperEdge se, Point2d s, Point2d e, Line backup ) { 
		
		Line mf;
		
		if ( se.mini.isEmpty() || se.mini.get( 0 ).imageFeatures == null ) {
			if ( backup == null )
				return null;
			else
				mf = backup;
		}
		else
			mf = se.mini.get(0).imageFeatures.mega.megafacade; // todo: bad place for this method.
		
		double mfL = mf.length();
		return new double[] { mf.findPPram( s ) * mfL, mf.findPPram( e ) * mfL };
	}

	private static boolean isRoof( Bar b ) {
		return Math.abs ( Mathz.PI2 + new Line ( b.start, b.end).aTan2() ) > 0.2;
	}

	private static Profile toProfile( Prof prof ) {
		
		Prof out = moveToX0( prof );
		
		return new Profile ( out.stream().map( x -> new Point2d(-x.x, -x.y) ).collect(Collectors.toList()) );
	}

	public static Prof moveToX0( Prof prof ) {
		Prof out = new Prof(prof);
		
		
		double tol = 0.01;
		
		if (out.get(out.size()-1).x == -5.224493710986535)
			System.out.println("bah");
		
		if (out.get( 1 ).y == tol && out.get(2).y == tol && out.size() >= 4 ) { // post-process out steps :(
			out.remove( 0 );
			out.remove( 0 );
			out.set(0, new Point2d (out.get(0).x, 0));
			prof = out;
			out = new Prof (prof);
		}
		
		Point2d first = out.get(0);
		
		if ( first.x > -100 ) { // always move to origin
//			if ( first.x > -1.5 ) { // move to origin

			double offset = first.x;

			out.clear();
			
			for ( Point2d p : prof )
				out.add( new Point2d( p.x - offset, p.y ) );
			
		} else { // add "step" and keep it where it is
			
			first.y = tol;

			out.add( 0, new Point2d( 0.0, tol ) );
			out.add( 0, new Point2d( 0, 0 ) );
			
		}
		return out;
	}
	
	@Override
	public JComponent getUI() {

		JPanel ui = new JPanel(new ListDownLayout());		
		JButton b = new JButton("p runs");
		b.addActionListener( e -> 
			SkelFootprint.debugFindCleanProfiles( footprint, this, new ProgressMonitor( null, "", "", 0, 100 ), tweed) );
		
		JButton c = new JButton("compare p");
		c.addActionListener( e -> skelFootprint.debugCompareProfs( skelFootprint.globalProfs) );
		
//		JButton d = new JButton("show p");
//		d.addActionListener( e -> skelFootprint.debugShowMeshProfs( skelFootprint.globalProfs) );
		
		ui.add(b);
		ui.add(c);
//		ui.add(d);
		
		return ui;
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		
		Jme3z.dump( dump, gNode, 0 );
		
//		int i = 0;
//		for ( Spatial s : gNode.getChildren() ) {
//			if ( s instanceof Geometry ) {
//				Mesh m = ( (Geometry) s ).getMesh();
//				dump.setCurrentTexture( this.hashCode()+"_"+(i++), 1, 1 );
//				Jme3z.toObj( m, dump );
//			}
//		}
	}

	public static class SimpleMass {
		
		public double start, end, height;
		public Line line;
		
		public SimpleMass( Line l, double s, double e, double h ) {
			this.line   = l;
			this.start  = s;
			this.end    = e;
			this.height = h;
		}
	}
	
	public List<SimpleMass> findMasses( Line mega ) {
		
		List<SimpleMass> out = new ArrayList();
		
		List<Line> dbg = new ArrayList();
		
		dbg.add(new Line ( mega ) );
		
		double mlen = mega.length();
		for (HalfFace hf : toRender.faces ) 
			for (HalfEdge he : hf.edges()) {
				Line l = he.line();
				double llen = l.length();
				double angle = l.absAngle( mega );
				
				if (l.distance( mega ) < 1 && llen > 3 && angle < 0.2 ) {
				
					dbg.add(new Line ( l ) );
					
					double height = ((SuperFace)hf).height;
					SuperLine sl = (SuperLine)(((SuperEdge)he).profLine);
					if (sl != null) {
						MegaFacade mf = sl.getMega();
						height = -Double.MAX_VALUE;
						for (int i = mf.getIndex( l.start ); i < mf.getIndex( l.end ); i++ ) {
							Prof p = mf.profiles.get( i );
							if (p != null)
								height = Math.max(height, p.get( p.size()-1 ).y);
						}
					}
					
					out.add(new SimpleMass ( 
							l,
							mega.findPPram( l.start ) * mlen, 
							mega.findPPram( l.end ) * mlen,
							height ) );
							
				}
			}
		
//		new Plot(toRender, dbg);
		
		return out;
	}





// old code for pulling profiles from megafacades here (we do it in the optimisation step now)
//try {
//	
//	MegaFacade mf = (MegaFacade) sl.properties.get(MegaFacade.class.getName());
//		
//	Prof p = findCleanProfile (se.start, se.end, mf, heights );
//	
//	profile = tagWalls ( toProfile ( p ), color, se.line() );
//	
//	{
//		Point2d pt2 = se.line().fromFrac( 0.5 ); 
//		Geometry r = new Geometry( "strip", p.renderStrip( 1, new Point3d(pt2.x, 0, pt2.y) ) );
//		r.setMaterial( mat );
//		pNode.attachChild( r );
//	}
//}
//catch (Throwable th) {
//	profile = crapProfile();
//	th.printStackTrace();
//}


//private Profile crapProfile() {
//	List<Point2d> defpts = new ArrayList<>();
//	defpts.add( new Point2d( 0, 0 ) );
//	defpts.add( new Point2d( 0, -6 ) );
//	defpts.add( new Point2d( 1, -7 ) );
//
//	return new Profile( defpts );
//}
//
//private Prof findCleanProfile( Point2d start, Point2d end, MegaFacade mf, List<Double> heights ) {
//
//	int s = mf.getIndex(start),
//		e = mf.getIndex( end ) + 1;
//	
//	for (int i = s; i <= e; i++) {
//		Prof p = mf.profiles.get(i);
//		if (p != null) 
//			heights.add( p.get( p.size() -1 ).y );
//	}
//	
//	int boundary = ( e - s ) / 3;
//	
//	
//	s += boundary;
//	e -= boundary;
//	
//	if (s > e )
//		e = s+1;
//	
//	class P {
//		
//		Prof prof, clean;
//		double failLength = 0;
//		
//		public P( Prof p, Prof c ) {
//			this.prof = p;
//			this.clean = c;
//		}
//	}
//	
//	Debug deb = new Debug();
//	debug.add(deb);
//	
//	List<P> ps = new ArrayList();
//
//	for (int i = s; i <= e; i++) {
//		Prof p = mf.profiles.get(i);
//		
//		
//		deb.profs.add( p );
//		
//		if (p != null) {
//			
//			heights.add( p.get( p.size() -1 ).y );
//			
//			Prof c = p.parameterize();
//			deb.cleans.add( c );
//			if (c != null) {
//				ps.add(new P(p,c));
//			}
//		}
//	}
//
//	for (P c : ps) {
//		for (P p : ps) {
//			double d = c.clean.distance(p.prof, true);
//			if (d < Double.MAX_VALUE)
//				c.failLength += d;
//			else
//				c.failLength += 100;
//		}
//		c.failLength /= c.prof.length();
//	}
//	
//	double shortestScore = Double.MAX_VALUE;
//	P shortestFail = ps.get( 0 );
//	
//	for (P p : ps) 
//		if (p.failLength <= shortestScore) {
//			shortestScore = p.failLength;
//			shortestFail = p;
//		}
//	
//	
//	Prof out = shortestFail.clean;
//	
//	deb.clean = new Prof(out);
//	
//	return out.moveToX0();
//	
//}
}
