package org.twak.mmg.media;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.swing.UIManager;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output.Face;
import org.twak.mmg.MMG;
import org.twak.mmg.MO;
import org.twak.mmg.MOgram;
import org.twak.mmg.Node;
import org.twak.mmg.functions.MiniFacadeImport;
import org.twak.mmg.ui.MOgramEditor;
import org.twak.tweed.gen.skel.WallTag;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMeshez;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.geom.HalfMesh2.Builder;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.viewTrace.facades.*;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.MiniFacade.Feature;
//import smile.math.Math;


public class GreebleMMG {

	public GreebleMMG( ) {
	}

	MiniFacade mf;
	MOgram mogram;
	MMeshBuilderCache mbs;


	public GreebleMMG (MiniFacade mf, MOgram mogram, MMeshBuilderCache mbs) {
		this.mf = mf;
		this.mogram = mogram;
		this.mbs = mbs;
	}

	public void greeble2DPolygon(Face f, Loop<LPoint3d> ll, WallTag wallTag, Matrix4d to2dXY, Vector3d along, Point3d bottomS, Point3d start, Point3d end, Loop<LPoint2d> flat, Matrix4d to3d, Matrix4d to2d, LinearForm3D facePlane) {

		if (mogram == null || wallTag == null) {
			mbs.get("mmg gray", wallTag == null ? GreebleSkel.BLANK_ROOF : GreebleSkel.BLANK_WALL, mf).add(flat.singleton(), to3d);
//			mbs.WOOD.add( flat.singleton(), to3d );
			return;
		}
		
//		MiniFacade mf = new MiniFacade();
		mf.facadeTexApp.resetPostProcessState();
		mf.facadeTexApp.postState.wallFaces.add( flat );

		MiniFacadeImport mfi = null;
//		MiniFacade oldMF = null;

		MultiMap<DepthColor, Loop<Point2d>> shapes = new MultiMap<>();

		for ( MO c : mogram)
			if ( c.function.getClass() == MiniFacadeImport.class )
				mfi = ( (MiniFacadeImport) c.function );

		if (mfi != null) {
			mfi.mf2 = mf;
		}

//		try {

		MMG mmg = new MMG();

			for (Node n : mogram.evaluate(new MMG()).findNodes())
				if (n.context.mo.renderData != null && n.result instanceof org.twak.mmg.prim.Face && !n.erased) {
					DepthColor dc = (DepthColor) n.context.mo.renderData;
					if (dc.visible)
						shapes.put(dc, ((org.twak.mmg.prim.Face) n.result).getPoints().get(0));
				}

//		}
//		finally {
//			if (mfi != null && oldMF != null)
//				mfi.mf = oldMF; // hack for UI editor
//		}


		HalfMesh2 mesh = tesselate(shapes);

		if (mesh.faces.isEmpty())
			mbs.get("mmg gray", wallTag == null ? GreebleSkel.BLANK_ROOF : new float[]{1,0,0, 1}, mf).add(flat.singleton(), to3d);
		else
			createSurfacesWithDepth( mf, mesh, mbs, to3d );

	}

	public static class DepthHF extends HalfFace {
		
		private DepthColor dc;
		
		public DepthHF() {}
		public DepthHF(HalfEdge e) {
			super(e);
		}
	}
	
	private static HalfMesh2 tesselate( MultiMap<DepthColor, Loop<Point2d>> shapes ) {
		
		Builder b = new Builder(  HalfEdge.class, DepthHF.class );
		
		for (DepthColor dc : shapes.keySet())
			for (Loop<Point2d> loop : shapes.get(dc)) {
				
				for (Loopable<Point2d> ll : loop.loopableIterator() ) {
					b.newPoint( MiniFacadeImport.toMMGSpace( ll.get() ) );
				}
				DepthHF face = (DepthHF) b.newFace();
				face.dc = dc;
			}

		HalfMeshez.splitMergeCoincident( b.mesh, 0.01 );
		
		return b.mesh;
	}

	
	// dictated not executed, twak
	private static void createSurfacesWithDepth( MiniFacade mf, HalfMesh2 mesh, MMeshBuilderCache mbs, Matrix4d to3d ) {
		
		for (HalfFace hf : mesh) {
		
			DepthColor dc = ((DepthHF)hf).dc;
			
			MatMeshBuilder mat = mbs.get( "mmg_"+dc.color.toString(), dc.color, mf );
			
			// skirt around face
			for (HalfEdge he : hf) {
				
				double heDepth = dc.depth, overDepth = 0;
				
				if (he.over != null) 
					overDepth = ((DepthHF)he.over.face).dc.depth;
				
				if (heDepth > 0 || he.over == null) {
					
					Point3d 
						a = new Point3d (he.start.x, heDepth, he.start.y ),
						b = new Point3d (he.end.x, heDepth, he.end.y ),
						c = new Point3d (he.end.x, overDepth, he.end.y ),
						d = new Point3d (he.start.x, overDepth, he.start.y );
							
					
						for (Point3d p : new Point3d[] {a,b,d,c}  ) 
							to3d.transform( p );
					
					mat.add( a, d, c, b );
				}
			}
			
			// main polygon
			Loop <Point3d> p3 = Loopz.transform( Loopz.to3d( hf.toLoop(), dc.depth, 1 ), to3d );
			mat.add( p3.singleton(), null, true );
		}
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
		
		Medium.mediums = new Medium[] { new D1(), new Facade2d() } ;
		
		MOgram mogram = createMOgram( createTemplateMF( 6, 5 ) );
		new MOgramEditor( mogram ).setVisible( true );
	}
}
