package org.twak.mmg.media;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.twak.mmg.functions.MiniFacadeImport;
import org.twak.mmg.ui.MOgramEditor;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.MMeshBuilderCache;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

import smile.math.Math;


public class GreebleMMG {

	public static void greeble2DPolygon( MOgram mogram, MMeshBuilderCache mbs, Face f, Loop<LPoint3d> ll, Matrix4d to2dXY, Vector3d along, Point3d bottomS, Point3d start, Point3d end, Loop<LPoint2d> flat, Matrix4d to3d, Matrix4d to2d, LinearForm3D facePlane ) {

		if (mogram == null) {
			mbs.WOOD.add( flat.singleton(), to3d );
			return;
		}
		
		MiniFacade mf = new MiniFacade();
		mf.facadeTexApp.resetPostProcessState();
		
		mf.facadeTexApp.postState.wallFaces.add( flat );
		
		for ( Command c : mogram)
			if ( c.function.getClass() == MiniFacadeImport.class )
				( (MiniFacadeImport) c.function ).mf = mf;
		
		
		for (Node n : mogram.evaluate( new MMG() ).findNodes()) 
			if ( n.context.mo.renderData != null && n.result instanceof org.twak.mmg.prim.Face && !n.erased ) {
				DepthColor dc = (DepthColor)n.context.mo.renderData;
				if (dc.visible) 
					createSurfacesWithDepth( dc, ((org.twak.mmg.prim.Face)n.result).getPoints().get( 0 ), mbs, to3d );
			}
		
	}


	private static void createSurfacesWithDepth( DepthColor dc, Loop<Point2d> geometry, MMeshBuilderCache mbs, Matrix4d to3d ) {
		
		if (geometry == null)
			return;
		
		Loop <Point3d> p3 = Loopz.transform( Loopz.to3d( geometry, dc.depth, 1 ), to3d );
		
		mbs.get( UUID.randomUUID().toString(), dc.color, null ).add( p3.singleton(), null, false );
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
		
		return out;
	}

	
	public static void main( String[] args ) {
		try {
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		} catch ( Throwable ex ) {
			ex.printStackTrace();
		}
		
		Medium.mediums = new Medium[] { new OneD(), new Facade2d() } ;
		
		MOgram mogram = createMOgram( createTemplateMF( 6, 5 ) );
		new MOgramEditor( mogram ).setVisible( true );
	}
}
