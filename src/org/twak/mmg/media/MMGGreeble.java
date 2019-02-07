package org.twak.mmg.media;

import java.util.Iterator;
import java.util.Set;

import javax.swing.UIManager;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output.Face;
import org.twak.mmg.Command;
import org.twak.mmg.Function;
import org.twak.mmg.MMG;
import org.twak.mmg.MO;
import org.twak.mmg.MOgram;
import org.twak.mmg.Node;
import org.twak.mmg.functions.AddLabel;
import org.twak.mmg.functions.FacadeFountain;
import org.twak.mmg.functions.FeatureFaceFountain;
import org.twak.mmg.functions.FeatureFountain;
import org.twak.mmg.functions.FixedEdge;
import org.twak.mmg.functions.FixedLabel;
import org.twak.mmg.prim.Edge;
import org.twak.mmg.prim.Label;
import org.twak.mmg.prim.Path;
import org.twak.mmg.prim.Path.Segment;
import org.twak.mmg.prim.ScreenSpace;
import org.twak.mmg.ui.MOgramEditor;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.MMGSkelGen;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.WallTag;
import org.twak.utils.Cache;
import org.twak.utils.CloneSerializable;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.ui.Colourz;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MatMeshBuilder;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;


public class MMGGreeble extends GreebleSkel {

	MOgram mogram;

	
	public MMGGreeble( Tweed tweed, SuperFace sf, MOgram mogram ) {
		super (tweed, sf);
		this.mogram = mogram;
	}

	@Override
	
	protected void mapTo2d( 
			Face f, 
			Loop<LPoint3d> ll, 
			MiniFacade mf,
			WallTag wallTag, 
			Set<QuadF> features, 
			MatMeshBuilder faceMaterial, 
			Line megafacade ) {
//	protected void mapTo2d( Face f, Loop<LPoint3d> ll, MiniFacade mf, WallTag wallTag, Set<QuadF> features, MatMeshBuilder faceMaterial ) {

		Matrix4d to2dXY = new Matrix4d();

		Vector3d up = f.edge.uphill, along = f.edge.direction(), out = f.edge.getPlaneNormal();

		along.normalize();

		to2dXY.setRow( 2, up.x, up.y, up.z, 0 );
		to2dXY.setRow( 1, out.x, out.y, out.z, 0 );
		to2dXY.setRow( 0, -along.x, -along.y, -along.z, 0 );

		Point3d bottomS = f.definingSE.iterator().next().getStart( f ), bottomE = f.definingSE.iterator().next().getEnd( f );

		Point3d start = new Point3d( bottomS );
		Point3d end = new Point3d( bottomE );

		to2dXY.m33 = 1;
		to2dXY.transform( start );

		to2dXY.m03 = -start.x;
		to2dXY.m13 = -start.y;
		to2dXY.m23 = -start.z;

		start = new Point3d( bottomS );
		to2dXY.transform( start );
		to2dXY.transform( end );

		Loop<LPoint2d> flat = GreebleHelper.to2dLoop( GreebleHelper.transform( ll, to2dXY ), 1 );

		Matrix4d to3d = new Matrix4d( to2dXY );
		to3d.invert();

		{ // face in z-up, we're in y-up
			double[] one = new double[4], two = new double[4];

			to3d.getRow( 1, one );
			to3d.getRow( 2, two );
			to3d.setRow( 1, two );
			to3d.setRow( 2, one );
		}

		Matrix4d to2d = new Matrix4d( to3d ); // now in jme space
		to2d.invert();

		MiniFacade forFace = null;
		if ( mf != null ) {
			forFace = new MiniFacade( mf );
			forFace.featureGen.clear();
		}

		LinearForm3D facePlane = new LinearForm3D( new Vector3d( out.x, out.z, out.y ), new Point3d( bottomS.x, bottomS.z, bottomS.y ) );

		Iterator<QuadF> quit = features.iterator();

		while ( quit.hasNext() ) {

			QuadF n = quit.next();

			if ( n.project( to2d, to3d, flat, facePlane, new Vector3d( along.y, 0, -along.x ) ) && wallTag != null && forFace != null ) {

				// set the vertical bounds, so we can just render in 2d
				FRect bounds = new FRect( n.original );
				n.setBounds( to2d, bounds );

				forFace.featureGen.put( n.original.getFeat(), bounds );
				quit.remove();
			}
		}

		mmg( to2d, to3d, flat, forFace );
	}

	
	public static MOgram createTemplateMOgram() {
		
		MOgram mogram = new MOgram();
		mogram.medium = new Facade2d();
		
		ScreenSpace.lastPosition = 0;
		
		Loop<LPoint2d> boundary = new Loop<>(
				new LPoint2d( 0, 0     , GreebleHelper.FLOOR_EDGE ),
				new LPoint2d( 7, 0     , GreebleHelper.WALL_EDGE  ),
				new LPoint2d( 7, -5    , GreebleHelper.ROOF_EDGE  ),
				new LPoint2d( 3.5, -10 , GreebleHelper.ROOF_EDGE  ),
				new LPoint2d( 0, -5    , GreebleHelper.WALL_EDGE  )
				);

		Cache<String, MO> getLabel = new Cache<String, MO>() {

			@Override
			public MO create( String i ) {
				FixedLabel fl = new FixedLabel( new Label( i ) );
				MO flm = new MO(fl);
				mogram.add(flm);
				return flm;
			}
		};
		
		for (Loopable<LPoint2d> lp : boundary.loopableIterator()) {
			
			FixedEdge fe= new FixedEdge( new Edge (lp.get(), lp.getNext().get()) );
			MO ffm = new MO (fe);
			mogram.add (ffm);
			AddLabel.label (mogram, ffm, getLabel.get( lp.get().label ) );
			
		}

		MiniFacade templateMF = new MiniFacade();

		for (int i = 0; i < 3; i++)
			templateMF.featureGen.put( Feature.WINDOW, new FRect( Feature.WINDOW, i * 1.7 + 1, -4., 1.5, 1., templateMF ) );
		
		templateMF.featureGen.put( Feature.DOOR, new FRect( Feature.DOOR, 1, -2.5, 1.5, 2.4, templateMF ) );
		
		for (Feature f : new Feature[] {Feature.WINDOW, Feature.DOOR }) {
			
			FixedLabel fl = new FixedLabel( new Label ( f.name().toLowerCase()) );
			MO flm = new MO(fl);
			mogram.add(flm);
			
			Function fff = new FeatureFaceFountain ( templateMF.featureGen.get( f ), f.toString() );
			MO wfm = new MO( fff );
			mogram.add( wfm  );
			
//			for (FRect r : templateMF.featureGen.get( f )) {
//				
//				FixedOBB wf = new FixedOBB(new OBB(r.x, r.y, r.width, r.height, 0));
//				MO wfm = new MO( wf );
//				mogram.add( wfm  );
//
//				AddLabel.label( mogram, flm, wfm );
//			}
		}
		
		return mogram;
	}

	private void mmg( Matrix4d to2d, Matrix4d to3d, Loop<LPoint2d> flat, MiniFacade forFace ) {

		MOgram m2 = (MOgram) CloneSerializable.xClone( mogram );
		
		for ( Command c : m2)
			if ( c.function.getClass() == FacadeFountain.class )
				( (FacadeFountain) c.function ).face = flat;
			else if ( c.function.getClass() == FeatureFountain.class )
				( (FeatureFountain) c.function ).mini = forFace;

		MMG mmg = new MMG();
		m2.evaluate( mmg );
		
		for (Node n : m2.evaluate( new MMG() ).findNodes()) 
			if ( n.context.mo.renderData != null) {
				DepthColor dc = (DepthColor)n.context.mo.renderData;
				greebleGrid.mbs.get( n.name, Colourz.toF4( dc.color ) ).add( toLoop( (Path) n.result ), to3d );
			}
		
	}

	private LoopL<Point2d> toLoop( Path result ) {
		
		LoopL<Point2d> out = new LoopL<>();
		Loop<Point2d> lp = out.loop();
		
		for (Segment s: result.segments) 
			lp.append( lp.getFirst() );
		
		return out;
	}
	
	public static void main( String[] args ) {
		try {
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		} catch ( Throwable ex ) {
			ex.printStackTrace();
		}
		MMGSkelGen sg = new MMGSkelGen();
		sg.mogram = MMGGreeble.createTemplateMOgram();
		new MOgramEditor( sg.mogram ).setVisible( true );
	}
}
