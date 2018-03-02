package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.WindowConstants;
import javax.vecmath.Point2d;

import org.twak.camp.Output;
import org.twak.camp.Skeleton;
import org.twak.camp.Tag;
import org.twak.camp.ui.Bar;
import org.twak.siteplan.campskeleton.Plan;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.Profile;
import org.twak.siteplan.campskeleton.Siteplan;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.CompareGens;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.CMPz;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.GreebleSkel.OnClick;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Regularizer;
import org.twak.viewTrace.facades.RoofTag;
import org.twak.viewTrace.facades.WallTag;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class SkelGen extends Gen implements IDumpObjs {

	BlockGen blockGen;

	public HalfMesh2 toRender = null;

	public SkelFootprint skelFootprint;
	protected List<Line> footprint;
	
	public SkelGen() {
		super("headless", null);
		skelFootprint = new SkelFootprint();
	}
	
	public SkelGen( Tweed tweed ) {
		super( "house", tweed );
		skelFootprint = new SkelFootprint(tweed);
	}
	
	public SkelGen( List<Line> footprint, Tweed tweed, BlockGen blockGen ) {
		super( "model", tweed );
		this.footprint = footprint;
		this.blockGen = blockGen;
		skelFootprint = new SkelFootprint(tweed);
		
		ProgressMonitor m = new ProgressMonitor( tweed.frame(), "Optimizing", "", 0, 100 );

		m.setProgress( 1 );
		m.setMillisToPopup( 0 );
		
		new Thread( () -> { 
			optimize(m);
			SkelGen.this.calculateOnJmeThread(); } ).start();
	}
	
	public SkelGen( HalfMesh2 mesh, Tweed tweed, BlockGen blockGen ) {
		
		super( "skel", tweed );
		
		this.toRender = mesh;
		this.blockGen = blockGen;
		
		ProgressMonitor m = new ProgressMonitor( tweed.frame(), "Optimizing", "", 0, 100 );
		
		m.setProgress( 1 );
		m.setMillisToPopup( 0 );
	}
	
	private void optimize(ProgressMonitor m) {
		toRender = skelFootprint.go(footprint, this, m);
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

		if (sf.maxProfHeights == null || sf.maxProfHeights.isEmpty())
			return sf.height;
		
		Collections.sort( sf.maxProfHeights );
		
		return sf.maxProfHeights.get( ( int ) (sf.maxProfHeights.size() * 0.9 ) ) + TweedSettings.settings.profileVSampleDist;
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

	Map<SuperFace, Node> geometry = new IdentityHashMap<>();
	
	public synchronized void setSkel( PlanSkeleton skel, Output output, SuperFace sf ) {
		
		removeGeometryFor( sf );

		Node house;
		
		OnClick onclick = new OnClick() {
			@Override
			public void selected( Output output, Node house2, SuperEdge se ) {
				SkelGen.this.selected(skel, house2, sf, se);
			}
		};
		
		
		GreebleSkel greeble = new GreebleSkel( tweed );
		
		house = greeble.showSkeleton( output, onclick );
			
		gNode.attachChild( house );
		geometry.put(sf, house);

		tweed.getRootNode().updateGeometricState();
		tweed.getRootNode().updateModelBound();
		tweed.gainFocus();
	}

	private void removeGeometryFor( SuperFace sf ) {
		if (geometry.get(sf) != null) {
			geometry.get(sf).removeFromParent();
			geometry.remove(sf);
		}
	}

	private void selected( PlanSkeleton skel, Node house, SuperFace sf, SuperEdge se ) {
		
		JPanel ui = new JPanel();
		ui.setLayout( new ListDownLayout() );
		
		JButton fac = new JButton("edit facade");
		fac.addActionListener( e -> editFacade( skel, sf, se, false ) );
		ui.add( fac );
		
		JButton tex = new JButton("texture facade");
		tex.addActionListener( e -> editFacade( skel, sf, se, true ) );
		ui.add( tex );
		
		
		JButton camp = new JButton("siteplan");
		camp.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				closeSitePlan();
				
				siteplan = new Siteplan( skel.plan, false ) {

					SuperFace workon = sf;
					
					public void show( Output output, Skeleton threadKey ) {

						super.show( output, threadKey );
						
						Plot.closeLast();

						
						tweed.enqueue( new Runnable() {
							
							@Override
							public void run() {
								
								removeGeometryFor( workon );
								tweed.frame.setGenUI(null); // current selection is invalid
								
								setSkel( (PlanSkeleton)threadKey, output, workon);
								
							}

						} );
					};
				};
				siteplan.setVisible( true );
				siteplan.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
			}

		} );

		ui.add( camp );
		
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
		
		JButton b = new JButton("view clean profiles");
		b.addActionListener( e -> 
			SkelFootprint.debugFindCleanProfiles( footprint, this, new ProgressMonitor( null, "", "", 0, 100 ), tweed) );
		ui.add(b);
		
		JButton c = new JButton("compare profiles");
		c.addActionListener( e -> skelFootprint.debugCompareProfs( skelFootprint.globalProfs) );
		ui.add(c);
		
		tweed.frame.setGenUI(ui);
	}

	private WallTag findWallMini( LoopL<Bar> points ) {
		
		Optional<Tag> wall = points.streamE().flatMap( b -> b.tags.stream() ).filter( t -> t instanceof WallTag ).findAny();
		
		if (wall.isPresent())
			return (WallTag)wall.get();
		
		return null;
	}
	
	private void setWallTag( LoopL<Bar> points, WallTag w2 ) {
		
		b: for (Bar b : points.eIterator()) {
			Iterator<Tag> tig = b.tags.iterator();
			while (tig.hasNext()) {
				if (tig.next() instanceof WallTag) {
					tig.remove();
					b.tags.add(w2);
					continue b;
				}
			}
		}
		
	}
	
	private static Siteplan siteplan;
	private void closeSitePlan() {
		if (siteplan != null) {
			siteplan.setVisible( false );
			siteplan.dispose();
			siteplan = null;
		}
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
	
	private Prof toProf( Profile profile ) {
		
		List<Point2d> pts = profile.points.get( 0 ).stream().
				map( b -> new Point2d(-b.end.x, -b.end.y) ).collect(Collectors.toList());
		
		pts.add(0, new Point2d());
		
		Prof prof = new Prof();
		for (Point2d p : pts)
			prof.add( p );
		
		return prof;
	}

	public static Prof moveToX0( Prof prof ) {
		Prof out = new Prof(prof);
		
		double tol = 0.01;
		
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
		ui.add(new JLabel("To edit: use select tool, right click on buildings"));
		
		JButton error = new JButton ("compare to mesh");
		error.addActionListener( l -> new CompareGens(this, blockGen)  );
		ui.add( error );
		
		return ui;
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		Jme3z.dump( dump, gNode, 0 );
	}

	public void editFacade( PlanSkeleton skel, SuperFace sf, SuperEdge se, boolean texture ) {
		closeSitePlan();
		
		if (se.toEdit == null) {
			se.toEdit = new MiniFacade();
			se.toEdit.left = 0;
			se.toEdit.width = se.length();
			se.toEdit.height = se.mini.get( 0 ).height;
			if (!texture)
				se.toEdit.groundFloorHeight = 2;
		}
		
		if (texture)
			se.toEdit.width = se.length();
		else
			se.toEdit.texture = se.toEdit.spec = se.toEdit.normal = null;
		
		Plot p = new Plot (se.toEdit);

		p.addEditListener( new Changed() {

			@Override
			public void changed() {

				PaintThing.debug.clear();
				if ( texture )
					new Thread( new Runnable() {
						@Override
						public void run() {
							CMPz.cmpRender( se.toEdit, skel, skel.output, sf, new Runnable() {
								
								public void run() {
									tweed.enqueue( new Runnable() {
										@Override
										public void run() {
											setSkel( skel, skel.output, sf );
											tweed.getRootNode().updateGeometricState();
										}
									} );
								}
							} );
						}
					} ).start();
				else
					tweed.enqueue( new Runnable() {
						@Override
						public void run() {
							setSkel( skel, skel.output, sf );
						}
					} );
			}
		} );
	}
}
