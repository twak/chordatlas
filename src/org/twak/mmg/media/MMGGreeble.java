package org.twak.mmg.media;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

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
import org.twak.mmg.Walk;
import org.twak.mmg.functions.FacadeFountain;
import org.twak.mmg.functions.FeatureFountain;
import org.twak.mmg.functions.FeatureFountain.LabelledListWrapper;
import org.twak.mmg.prim.Label;
import org.twak.mmg.prim.ScreenSpace;
import org.twak.mmg.steps.Static;
import org.twak.tweed.Tweed;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.Greeble;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.MatMeshBuilder;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.WallTag;

public class MMGGreeble extends Greeble {

	MOgram mogram;

	public MMGGreeble( Tweed tweed, MOgram mogram ) {
		super( tweed );
		this.mogram = mogram;

	}

	
	@Override
	protected void mapTo2d( Face f, Loop<LPoint3d> ll, MiniFacade mf, WallTag wallTag, Set<QuadF> features, MatMeshBuilder faceMaterial ) {

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
			forFace.rects.clear();
		}

		LinearForm3D facePlane = new LinearForm3D( new Vector3d( out.x, out.z, out.y ), new Point3d( bottomS.x, bottomS.z, bottomS.y ) );

		Iterator<QuadF> quit = features.iterator();

		while ( quit.hasNext() ) {

			QuadF n = quit.next();

			if ( n.project( to2d, to3d, flat, facePlane, new Vector3d( along.y, 0, -along.x ) ) && wallTag != null && forFace != null ) {

				// set the vertical bounds, so we can just render in 2d
				FRect bounds = new FRect( n.original );
				n.setBounds( to2d, bounds );

				forFace.rects.put( n.original.f, bounds );
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

		mogram.add (new MO (new FacadeFountain( boundary )));

		MiniFacade templateMF = new MiniFacade();

		for (int i = 0; i < 3; i++)
			templateMF.rects.put( Feature.WINDOW, new FRect( i * 1.7 + 1, -4, 1.5, 1 ) );
		
		templateMF.rects.put( Feature.DOOR, new FRect( 1, -2.5, 1.5, 2.5 ) );
		
		for (Feature f : new Feature[] {Feature.WINDOW, Feature.DOOR }) {
			FeatureFountain ff = new FeatureFountain( f, templateMF );
			MO ffM = new MO(ff);
			mogram.add(ffM);
			
			LabelledListWrapper lwr = new LabelledListWrapper( ff.label );
			MO lwrM = new MO(lwr);
			lwrM.scrubWalks();
			mogram.add(lwrM);
			
			Walk w = new Walk(lwr.toString() + "_i:1");
            w.add(new Static(ffM));
            lwrM.setWalk(0, w); 
			
			
		}
		
		return mogram;
	}

	private void mmg( Matrix4d to2d, Matrix4d to3d, Loop<LPoint2d> flat, MiniFacade forFace ) {

		FeatureFountain fe = (FeatureFountain) find(FeatureFountain.class);
		fe.mini = forFace;

		for ( Command c : mogram )
			if ( c.function.getClass() == FacadeFountain.class )
				( (FacadeFountain) c.function ).face = flat;

		MMG mmg = new MMG();
		mogram.evaluate( mmg );
		
//		for (Node n : mmg.allNodes) {
//			if (n.result instanceof )
//			mbs.get( "mmg", new float[] {1, 1, 1, 1} ).addDepth()
//		}
	}

	private Function find(Class k) {
		for ( Command c : mogram )
			if ( c.function.getClass() == k )
				return (FacadeFountain) c.function;
		return null;
	}

//	private FeatureFountain getFF( Feature f ) {
//
//		for ( Command c : mogram )
//			if ( c.function.getClass() == FeatureFountain.class && ( (FeatureFountain) c.function ).feature == f )
//				return (FeatureFountain) c.function;
//
//		return null;
//	}
}
