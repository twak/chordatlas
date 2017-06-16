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
import org.twak.mmg.MMG;
import org.twak.mmg.MO;
import org.twak.mmg.MOgram;
import org.twak.mmg.Node;
import org.twak.mmg.functions.FaceFountain;
import org.twak.mmg.functions.FeatureFountain;
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

		MiniFacade templateMF = new MiniFacade();

		int i = 0;
		for ( Feature f : Feature.values() ) {

			templateMF.rects.put( f, new FRect( i++ * 3, 3, 2, 2.5 ) );

			mogram.add( new MO( new FeatureFountain( f, templateMF ) ) );
		}

		return mogram;
	}

	private void mmg( Matrix4d to2d, Matrix4d to3d, Loop<LPoint2d> flat, MiniFacade forFace ) {

		for ( Feature f : Feature.values() ) {
			FeatureFountain ff = getFF( f );
			ff.mini = forFace;
		}

		FaceFountain ff = findFaceFountain();
		ff.face = flat;
		
		MMG mmg = new MMG();
		mogram.evaluate( mmg );
		
		for (Node n : mmg.allNodes) {
//			if (n.result instanceof )
//			mbs.get( "mmg", new float[] {1, 1, 1, 1} ).addDepth()
		}
	}

	private FaceFountain findFaceFountain() {
		for ( Command c : mogram )
			if ( c.function.getClass() == FaceFountain.class )
				return (FaceFountain) c.function;
		return null;
	}

	private FeatureFountain getFF( Feature f ) {

		for ( Command c : mogram )
			if ( c.function.getClass() == FeatureFountain.class && ( (FeatureFountain) c.function ).feature == f )
				return (FeatureFountain) c.function;

		return null;
	}
}
