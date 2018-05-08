package org.twak.tweed.gen.skel;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Skeleton;
import org.twak.camp.Tag;
import org.twak.camp.ui.Bar;
import org.twak.camp.ui.Marker;
import org.twak.siteplan.anchors.Ship;
import org.twak.siteplan.anchors.Ship.Instance;
import org.twak.siteplan.campskeleton.Plan;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.Profile;
import org.twak.siteplan.campskeleton.Siteplan;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.CompareGens;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.BlockGen;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.JmeGen;
import org.twak.tweed.gen.MiniViewer;
import org.twak.tweed.gen.Prof;
import org.twak.tweed.gen.ProfileAssignmentViewer;
import org.twak.tweed.gen.SkelFootprint;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.FileDrop;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.GreebleSkel.OnClick;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.NSliders;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Regularizer;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class SkelGen extends Gen implements IDumpObjs {

	public BlockGen blockGen;

	public HalfMesh2 toRender = null;

	public SkelFootprint skelFootprint;
	protected List<Line> footprint;
	private  Map<Object, Face> lastOccluders = new HashMap<>();

	
	static {
		PlanSkeleton.TAGS = new String[][]
			    {
	        {"org.twak.tweed.gen.skel.WallTag", "wall"},
	        {"org.twak.tweed.gen.skel.RoofTag", "roof"},
	    };
	}
	
	public SkelGen() {
		super( "headless", null );
		skelFootprint = new SkelFootprint();
	}

	public SkelGen( Tweed tweed ) {
		super( "house", tweed );
		skelFootprint = new SkelFootprint( tweed );
	}

	public SkelGen( List<Line> footprint, Tweed tweed, BlockGen blockGen ) {
		super( "model", tweed );
		this.footprint = footprint;
		this.blockGen = blockGen;
		skelFootprint = new SkelFootprint( tweed );

		ProgressMonitor m = new ProgressMonitor( tweed.frame(), "Optimizing", "", 0, 100 );

		m.setProgress( 1 );
		m.setMillisToPopup( 0 );

		new Thread( () -> {
			optimize( m );
			SkelGen.this.calculateOnJmeThread();
		} ).start();
	}

	public SkelGen( HalfMesh2 mesh, Tweed tweed, BlockGen blockGen ) {

		super( "skel", tweed );

		this.toRender = mesh;
		this.blockGen = blockGen;

		ProgressMonitor m = new ProgressMonitor( tweed.frame(), "Optimizing", "", 0, 100 );

		m.setProgress( 1 );
		m.setMillisToPopup( 0 );
	}

	private void optimize( ProgressMonitor m ) {
		toRender = skelFootprint.buildAndSolve( footprint, this, m );
	}

	@Override
	public void calculate() {

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();

		Node pNode = new Node();


		
//		Map<Object, Face> occluderIDs = new HashMap<>();
//		
//		// find some sensible defaults to propogate
//		for ( Face f : toRender output.faces.values() )  {
//			
//			WallTag wt = ((WallTag)t);
//			if (t != null ) {
//				
//				if (f.parent == null /*is bottom */)
//					for (Object o : wt.occlusions)
//						occluderIDs.put( wt.occlusionID, f );
//
//		
		
		if ( toRender != null ) {

			 Map<Object, Face> occluderLookup = lastOccluders = new HashMap<>();
			
			for ( int i = 0; i < toRender.faces.size(); i++ )
				try {
					HalfFace f = toRender.faces.get( i );
					SuperFace sf = (SuperFace) f;
					
					Rendered previouslyRendered = geometry.get( sf );
					
					if (previouslyRendered.skel == null)  
						previouslyRendered.skel = calc( sf );

					if (previouslyRendered.skel != null) 
						for (Face ff :previouslyRendered.skel.output.faces.values()) {
							WallTag wt = (WallTag) GreebleHelper.getTag( ff.profile, WallTag.class );
							if (wt != null && ff.parent == null) {
								occluderLookup.put(wt.occlusionID, ff);
							}
						}
					
				} catch ( Throwable th ) {
					th.printStackTrace();
				}
		
			for ( int i = 0; i < toRender.faces.size(); i++ )
				
				try {
					SuperFace sf = (SuperFace) toRender.faces.get( i );
					Rendered previouslyRendered = geometry.get( sf );
					
					if (previouslyRendered.skel != null) 
							setSkel ( previouslyRendered.skel, sf, occluderLookup);

				} catch ( Throwable th ) {
					th.printStackTrace();
				}
		}
		
		if ( !pNode.getChildren().isEmpty() )
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

		if ( skelP == null && skelS == null )
			return null;

		if ( skelP == null )
			skel = skelS;
		else if ( skelS == null )
			skel = skelP;
		else {
			if ( skelP.capArea < 0.5 * skelS.capArea )
				skel = skelP;
			else
				skel = skelS;
		}

		return skel;
	}

	private static Double findHeightFromProfiles( SuperFace sf ) {

		if ( sf.maxProfHeights == null || sf.maxProfHeights.isEmpty() )
			return sf.height;

		Collections.sort( sf.maxProfHeights );

		return sf.maxProfHeights.get( (int) ( sf.maxProfHeights.size() * 0.9 ) ) + TweedSettings.settings.profileVSampleDist;
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
		LoopL<Point2d> lpd = new LoopL();
		
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

				if ( se.prof == null || se.prof.size() < 2 ) {

					List<Point2d> defpts = new ArrayList<>();
					defpts.add( new Point2d( 0, 0 ) );
					defpts.add( new Point2d( 0, -sf.height * 1.2 ) );

					profile = new Profile( defpts );

				} else {
					profile = toProfile( se.prof );
				}

				tagWalls( ( SuperFace) se.face, profile, se, lpb.get(), lpb.getNext().get() );
				plan.addLoop( profile.points.get( 0 ), plan.root, profile );

				b.tags.add( new SETag( se ) );

				loop.prepend( b );
				plan.profiles.put( b, profile );
			}
		}
		
		plan.points = loopl;

		if ( cap != null ) {
			
			// skel.capAt( cap, a -> skel.capArea = a ); simple...but doesn't show in the siteplan ui
			
			Ship s = new FlatRoofShip( cap, plan) ;
			
			for ( Profile prof : plan.profiles.values() ) {
				for ( Loop<Bar> lb : prof.points ) {
					boolean addedMarker = false;
					for ( Bar b : lb ) {

						if ( -b.start.y < cap && -b.end.y > cap || ( !addedMarker && b == lb.start.getPrev().get() ) ) {

							Marker m = new Marker();
							m.set( b.toLine().xAtY( -cap ), -cap );
							m.bar = b;
							m.bar.mould.create( m, null );

							Instance i = s.newInstance();
							i.anchors[ 0 ].setProfileGen( m.generator );
							addedMarker = true;
						}
					}
				}
			}
			
	        plan.ships.add( s );
		}

		PlanSkeleton skel = new PlanSkeleton( plan );
		skel.skeleton();
		
		return skel;
	}

	private static class Rendered {
		
		PlanSkeleton skel;
		Node node;
		Output output;
		
		public void set( Node node, Output output, PlanSkeleton skel ) {
			this.node = node;
			this.output = output;
			this.skel = skel;
			
		}
	}
	
	Cache<SuperFace, Rendered> geometry = new Cach<> (sf -> new Rendered() );

	public synchronized void setSkel( PlanSkeleton skel, SuperFace sf, Map<Object, Face> occluderLookup ) {

		removeGeometryFor( sf );

		Node house;

		OnClick onclick = new OnClick() {
			@Override
			public void selected( Output output, Node house2, SuperEdge se ) {
				SkelGen.this.selected( skel, house2, sf, se );
			}
		};

		GreebleSkel greeble = new GreebleSkel( tweed );
		
		house = greeble.showSkeleton( skel.output, onclick, occluderLookup );

		gNode.attachChild( house );
		geometry.get( sf ).set (house, skel.output, skel );

		tweed.getRootNode().updateGeometricState();
		tweed.getRootNode().updateModelBound();
		tweed.gainFocus();
	}

	private void removeGeometryFor( SuperFace sf ) {
		Rendered rd = geometry.get( sf );
		if ( rd != null ) {
			
			if (rd.node != null ) {
				rd.node.removeFromParent();
				rd.node = null;
			}
		}
	}

	private void selected( PlanSkeleton skel, Node house, SuperFace sf, SuperEdge se ) {

		JPanel ui = new JPanel();
		ui.setLayout( new ListDownLayout() );

		JButton fac = new JButton( "edit facade" );
		fac.addActionListener( e -> editFacade( skel, sf, se ) );
		ui.add( fac );

		JButton proc = new JButton( "procedural facade" );
		proc.addActionListener( e -> cgaFacade( skel, sf, se ) );
		ui.add( proc );

		JButton camp = new JButton( "edit plan/pofile" );
		camp.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {

				closeSitePlan();
				Plot.closeLast();

				for (HalfEdge he :sf)
				{
					SuperEdge ee = (SuperEdge)he;
					if (ee.toEdit != null)
						ee.toEdit.texture = null;
				}
				
				siteplan = new Siteplan( skel.plan, false ) {

					SuperFace workon = sf;

					public void show( Output output, Skeleton threadKey ) {

						
						super.show( output, threadKey );

						tweed.enqueue( new Runnable() {

							@Override
							public void run() {

								removeGeometryFor( workon );
								tweed.frame.setGenUI( null ); // current selection is invalid

								setSkel( (PlanSkeleton) threadKey, workon, lastOccluders );

							}

						} );
					};
				};
				siteplan.setVisible( true );
				siteplan.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
			}

		} );
		ui.add( camp );
		
		JButton plan = new JButton( "plan" );
		plan.addActionListener( e -> new Plot( toRender, footprint ) );
		ui.add( plan );

		JButton b = new JButton( "view clean profiles" );
		b.addActionListener( e -> SkelFootprint.debugFindCleanProfiles( footprint, this, new ProgressMonitor( null, "", "", 0, 100 ), tweed ) );
		ui.add( b );

		JButton c = new JButton( "compare profiles" );
		c.addActionListener( e -> skelFootprint.debugCompareProfs( skelFootprint.globalProfs ) );
		ui.add( c );

		JButton mini = new JButton( "street-view" );
		mini.addActionListener( e -> new MiniViewer( se ) );
		if ( sf != null )
			ui.add( mini );

		JButton prof = new JButton( "profiles" );
		prof.addActionListener( e -> new ProfileAssignmentViewer( sf, skelFootprint == null ? null : skelFootprint.globalProfs ) );
		ui.add( prof );

		JButton remove = new JButton( "remove" );
		remove.addActionListener( e -> {
			visible = false;
			calculateOnJmeThread();
			tweed.frame.removeGen( SkelGen.this );
		} );
		ui.add( remove );


		tweed.frame.setGenUI( ui );
	}


	private WallTag findWallMini( LoopL<Bar> points ) {

		Optional<Tag> wall = points.streamE().flatMap( b -> b.tags.stream() ).filter( t -> t instanceof WallTag ).findAny();

		if ( wall.isPresent() )
			return (WallTag) wall.get();

		return null;
	}

	private void setWallTag( LoopL<Bar> points, WallTag w2 ) {

		b: for ( Bar b : points.eIterator() ) {
			Iterator<Tag> tig = b.tags.iterator();
			while ( tig.hasNext() ) {
				if ( tig.next() instanceof WallTag ) {
					tig.remove();
					b.tags.add( w2 );
					continue b;
				}
			}
		}

	}

	private static Siteplan siteplan;

	private void closeSitePlan() {
		if ( siteplan != null ) {
			siteplan.setVisible( false );
			siteplan.dispose();
			siteplan = null;
		}
	}

	public static Profile tagWalls( SuperFace sf, Profile profile, SuperEdge se, Point2d s, Point2d e ) {

		
		if ( se.toEdit == null ) { // first time through, use regularizer 

			if ( se.mini != null && ! se.mini.isEmpty() ) {
				double[] range = findRange( se, s, e, null );

				if ( range != null )
					se.toEdit = new Regularizer().go( se.mini, range[ 0 ], range[ 1 ], null );
			}
			ensureMF( sf, se );
		} 

		Tag wall = new WallTag( se.profLine, se, new HashSet<>( se.occlusions ), se.toEdit ), 
			roof = new RoofTag( sf.roofColor );

		boolean first = true;
		for ( Loop<Bar> lb : profile.points ) {
			for ( Loopable<Bar> ll : lb.loopableIterator() ) {

				Bar b = ll.get();

				if ( isRoof( b ) ) // || ll != lb.start && isRoof ( ll.getPrev().get() ) && b.start.distanceSquared( b.end ) < 16 )
					b.tags.add( roof );
				else {
					if ( first )
						b.tags.add( new WallTag( se.profLine, se, new HashSet<>( se.occlusions ), se.toEdit, true ) );
					else
						b.tags.add( wall );
				}
			}
			first = false;
		}

		return profile;
	}

	private static double[] findRange( SuperEdge se, Point2d s, Point2d e, Line backup ) {

		Line mf;

		if ( se.mini.isEmpty() || se.mini.get( 0 ).imageFeatures == null ) {
			if ( backup == null )
				return null;
			else
				mf = backup;
		} else
			mf = se.mini.get( 0 ).imageFeatures.mega.megafacade; // todo: bad place for this method.

		double mfL = mf.length();
		return new double[] { mf.findPPram( s ) * mfL, mf.findPPram( e ) * mfL };
	}

	private static boolean isRoof( Bar b ) {
		return Math.abs( Mathz.PI2 + new Line( b.start, b.end ).aTan2() ) > 0.2;
	}

	private static Profile toProfile( Prof prof ) {

		Prof out = moveToX0( prof );

		return new Profile( out.stream().map( x -> new Point2d( -x.x, -x.y ) ).collect( Collectors.toList() ) );
	}

	private Prof toProf( Profile profile ) {

		List<Point2d> pts = profile.points.get( 0 ).stream().map( b -> new Point2d( -b.end.x, -b.end.y ) ).collect( Collectors.toList() );

		pts.add( 0, new Point2d() );

		Prof prof = new Prof();
		for ( Point2d p : pts )
			prof.add( p );

		return prof;
	}

	public static Prof moveToX0( Prof prof ) {
		Prof out = new Prof( prof );

		double tol = 0.01;

		if ( out.get( 1 ).y == tol && out.get( 2 ).y == tol && out.size() >= 4 ) { // post-process out steps :(
			out.remove( 0 );
			out.remove( 0 );
			out.set( 0, new Point2d( out.get( 0 ).x, 0 ) );
			prof = out;
			out = new Prof( prof );
		}

		Point2d first = out.get( 0 );

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

		JPanel ui = new JPanel( new ListDownLayout() );
		ui.add( new JLabel( "To edit: use select tool, right click on buildings" ) );

		JButton compare = new JButton( "compare to mesh" );
		compare.addActionListener( l -> new CompareGens( this, blockGen ) );
		ui.add( compare );
		
		JButton pf = new JButton( "procedural all facades" );
		pf.addActionListener( l -> cgaAll() );
		ui.add( pf );
		
		JButton tf = new JButton( "texture all facades" );
		tf.addActionListener( l -> textureAll() );
		ui.add( tf );
		
		return ui;
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		Jme3z.dump( dump, gNode, 0 );
	}

	public void editFacade( PlanSkeleton skel, SuperFace sf, SuperEdge se ) {
		closeSitePlan();

		JToggleButton texture = new JToggleButton( "textured" );
		texture.setSelected( se.toEdit != null && se.toEdit.texture != null );
		
		if ( se.toEdit == null ) {
			ensureMF( sf, se );
			if ( !texture.isSelected() )
				se.toEdit.groundFloorHeight = 2;
		}

		if ( texture.isSelected() )  {
			patchWallTag (skel, se, se.toEdit);
			se.toEdit.width = se.length();
		}
		else
			se.toEdit.texture = null;
		
		if (se.toEdit.featureGen instanceof CGAMini) { // de-proecuralize before editing 
			((CGAMini) se.toEdit.featureGen).update();
			se.toEdit.featureGen = new FeatureGenerator( se.toEdit, se.toEdit.featureGen );
		}
		
		
		
		double[] z;
		
		FeatureGenerator gf = (FeatureGenerator) se.toEdit.featureGen;
		if ( gf.style != null )
			z = gf.style;
		else
			z = gf.style = new double[Pix2Pix.LATENT_SIZE];
		
		
		List<MiniFacade> sameStyle = new ArrayList();
		for (HalfEdge e : sf ){
			SuperEdge se2 = (SuperEdge)e;
			if ( se2.toEdit.featureGen.style == z)
				sameStyle.add( se2.toEdit );
		}
		
		
		
		Changed c = new Changed() {

			@Override
			public void changed() {

				PaintThing.debug.clear();
				if ( texture.isSelected() )
					new Thread( new Runnable() {
						@Override
						public void run() {
							new Pix2Pix().facade( sameStyle, z, new Runnable() {

								public void run() {
									tweed.enqueue( new Runnable() {
										@Override
										public void run() {
											setSkel( skel, sf, lastOccluders );
											tweed.getRootNode().updateGeometricState();
										}
									} );
								}
							} );
						}
					} ).start();
				else {
					
					se.toEdit.texture = null;
					
					tweed.enqueue( new Runnable() {
						@Override
						public void run() {
							setSkel( skel, sf, lastOccluders );
						}
					} );
				}
			}
		};

		NSliders sliders = new NSliders(z, c);
		
		FileDrop drop = new FileDrop( "style" ) {
			public void process(java.io.File f) {
				new Pix2Pix().encode( f, z, new Runnable() {
					@Override
					public void run() {
						sliders.setValues( z );
					}
				} );
			};
		};
		
		Plot p = new Plot( se.toEdit, texture, sliders, drop );
		
		texture.addActionListener( l -> c.changed() );
		
		c.changed();
		p.addEditListener( c );
	}
	
	private static void ensureMF( SuperFace sf, SuperEdge se ) {

		if (se.toEdit == null) {
		se.toEdit = new MiniFacade();
		se.toEdit.left = 0;
		se.toEdit.width = se.length();
		}
		
		if (se.mini != null && !se.mini.isEmpty())
			se.toEdit.height = se.mini.get( 0 ).height;
		else if (se.prof != null) {

			if (se.prof.size() > 2)
				se.toEdit.height = se.prof.get( 1 ).y;
			
			if (se.toEdit.height < 1.5)
				se.toEdit.height = sf.height;
		}
		else
			se.toEdit.height = sf.height;
	}

	private void cgaFacade( PlanSkeleton skel, SuperFace sf, SuperEdge se ) {
		
		ensureMF(sf, se);
		
		se.toEdit.featureGen = new CGAMini( se.toEdit );
		se.toEdit.featureGen.update();
		
		patchWallTag( skel, se, se.toEdit);
		
		calculateOnJmeThread();
	}
	

	private void cgaAll() {

		for (HalfFace hf : toRender )
			for (HalfEdge he : hf) {
				SuperEdge se = (SuperEdge) he;
				
				ensureMF((SuperFace)hf, se);
				se.toEdit.featureGen = new CGAMini( se.toEdit );
				se.toEdit.featureGen.update();
			}
		
		calculateOnJmeThread();
	}
	
	private void textureAll() {
		
		List<MiniFacade> mfs = new ArrayList<>();
		
		double[] style = new double[ Pix2Pix.LATENT_SIZE ];
		
		for (int i = 0; i < style.length; i++)
			style[i] = Math.random() - 0.5;
		
		for (HalfFace hf : toRender )
			for (HalfEdge he : hf) {
				SuperEdge se = (SuperEdge) he;
				
				ensureMF((SuperFace)hf, se);
				mfs.add( se.toEdit );
				se.toEdit.featureGen.style = style;
				se.toEdit.featureGen.update();
			}
		
		new Thread( new Runnable() {
			@Override
			public void run() {
				new Pix2Pix().facade( mfs, new double[8], new Runnable() {
					public void run() {
						tweed.enqueue( new Runnable() {
							@Override
							public void run() {
								for (MiniFacade mf : mfs)
									mf.featureGen = new FeatureGenerator( mf.featureGen ); /* remove procedural facade...it overwrites features */
								calculateOnJmeThread();
							}
						} );
					}
				} );
			}
		} ).start();
		
	}


	private void patchWallTag( PlanSkeleton skel, SuperEdge se, MiniFacade mf ) {
		
		for (Bar b : skel.plan.profiles.keySet()) {
			if (b.end.equals( se.start ) && b.start.equals( se.end )) {
				skel.plan.profiles.get( b ) // simple one-liner thank you streams
				.points.streamE()
				.flatMap( bar -> bar.tags.stream() )
				.filter( tag -> tag instanceof WallTag )
				.map( tag -> (WallTag) tag )
				.forEach( t -> t.miniFacade = mf );
			}
		}
	}
}
