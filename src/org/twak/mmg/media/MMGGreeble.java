package org.twak.mmg.media;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.UIManager;
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
import org.twak.mmg.functions.*;
import org.twak.mmg.prim.Path;
import org.twak.mmg.prim.Path.Segment;
import org.twak.mmg.prim.ScreenSpace;
import org.twak.mmg.ui.MOgramEditor;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.MMGSkelGen;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.WallTag;
import org.twak.utils.CloneSerializable;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MatMeshBuilder;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

import smile.math.Math;


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

	public static MOgram createMOgram(MiniFacade mf) {
		
		MOgram mogram = new MOgram();
		mogram.medium = new Facade2d();

		Map<String,MO> labels = MiniFacadeImport.createLabels();
		
		mogram.addAll( labels.values() );
		
		mogram.add( new MO ( new MiniFacadeImport(mf, labels) ) );

		return mogram;
	}

	
	public static MiniFacade createTemplateMF(double w, double h ) {
		
		MiniFacade  out = new MiniFacade();
		
		Loop<LPoint2d> boundary = new Loop<>(
				new LPoint2d( 0, 0     , GreebleHelper.FLOOR_EDGE ),
				new LPoint2d( w, 0     , GreebleHelper.WALL_EDGE  ),
				new LPoint2d( w, -h    , GreebleHelper.ROOF_EDGE  ),
				new LPoint2d( w/2, -h - Math.random()*4 , GreebleHelper.ROOF_EDGE  ),
				new LPoint2d( 0, -h    , GreebleHelper.WALL_EDGE  )
				);

		out.facadeTexApp.resetPostProcessState();
		out.facadeTexApp.postState.wallFaces.add( boundary );

		List<DRectangle> floors = new DRectangle(1,-h,w-2,h).splitY( r -> CGAMini.splitFloors( r, 2, 2, 2 ) );

		
		for (int fi = 0; fi < floors.size(); fi++ ) {
			
			DRectangle floor = floors.get( fi );
			List<DRectangle> fPanels = floor.splitX( r -> CGAMini.stripe( r, 1.5, 0.8 ) );

			for ( int p = 0; p < fPanels.size(); p++ ) {
				if ( p % 2 == 0 ) {
					DRectangle dr = fPanels.get( p );
					

					if (fi == floors.size()-1 && p == 0) {
						
						dr.height = Math.min( 2, dr.height );
						dr.y = -dr.height - 0.1;
						
						out.featureGen.add( Feature.DOOR, dr );
					}
					else {
						List<DRectangle> winPanel = dr.splitY( r -> CGAMini.split3Y( r, 0.2, dr.height - 0.2 - 0.8 ) );
					if ( winPanel.size() == 3 ) {
						{
							DRectangle window = winPanel.get( 1 );
							out.featureGen.add( Feature.WINDOW, window );
						}
					}
					}
				}
			}
		}
		
//		out.featureGen = new CGAMini( out );
//		out.featureGen.update();
		
		
		
//		for (int x = 0; x < 3; x++)
//			out.featureGen.put( Feature.WINDOW, new FRect( Feature.WINDOW, x * 1.7 + 1, -4., 1.5, 1., out ) );
//		
//		out.featureGen.put( Feature.WINDOW, new FRect( Feature.WINDOW, 4, -2., 1.5, 1., out ) );
//		
//		out.featureGen.put( Feature.DOOR, new FRect( Feature.DOOR, 1, -2.5, 1.5, 2.4, out ) );
		
		return out;
	}

	private void mmg( Matrix4d to2d, Matrix4d to3d, Loop<LPoint2d> flat, MiniFacade forFace ) {

		MOgram m2 = (MOgram) CloneSerializable.xClone( mogram );
		
		for ( Command c : m2)
			if ( c.function.getClass() == MiniFacadeImport.class )
				( (MiniFacadeImport) c.function ).mf = forFace;

		MMG mmg = new MMG();
		m2.evaluate( mmg );
		
		Map<Loop<Point2d>, DepthColor> geometry = new HashMap<>();
		
		for (Node n : m2.evaluate( new MMG() ).findNodes()) 
			if ( n.context.mo.renderData != null && n.result instanceof org.twak.mmg.prim.Face) {
				DepthColor dc = (DepthColor)n.context.mo.renderData;
				if (dc.visible) {
					geometry.put( ((org.twak.mmg.prim.Face)n.result).getPoints().get( 0 ), dc );
				}
			}
		
		new DepthGraph(greebleGrid.mbs, to3d, geometry);
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
		
		Medium.mediums = new Medium[] { new OneD(), new Facade2d() } ;
		
		MMGSkelGen sg = new MMGSkelGen();
		sg.mogram = createMOgram( createTemplateMF( 6, 5 ) );
		new MOgramEditor( sg.mogram ).setVisible( true );
	}
}
