package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.camp.Edge;
import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.camp.Tag;
import org.twak.camp.ui.Bar;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.jme.Jme3z;
import org.twak.siteplan.jme.MeshBuilder;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.SETag;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.WindowGen;
import org.twak.tweed.gen.WindowGen.Window;
import org.twak.utils.Cach2;
import org.twak.utils.Cache2;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.Pair;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.Grid.Griddable;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Tube.CrossGen;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

public class Greeble {

	Tweed tweed;
	Node node = new Node();
	
	protected Cache2<String, float[], MatMeshBuilder> mbs = 
			new Cach2<String, float[], MatMeshBuilder>( (a,b) -> new MatMeshBuilder( (String) a, (float[]) b ) );
	
	final static float[] 
			glass = new float[] {0.0f, 0.0f, 0.0f, 1},
			wood = new float[] {0.8f, 0.8f, 0.8f, 1 },
			balcony = new float[] {0.2f, 0.2f, 0.2f, 1},
			moudling = new float[] {0.7f, 0.7f, 0.7f, 1};

	
	OnClick onClick;
	
	
	public Greeble( Tweed tweed ) {
		this.tweed = tweed;
	}

	public Node showSkeleton( Output output, OnClick onClick ) {
		
		this.onClick = onClick;
		createMesh( output );
		return node;
	}

	public void createMesh( Output output ) {
		
		
		float[] roofColor = new float[] {0.3f, 0.3f, 0.3f, 1 },
				wallColor = new float[] {228/255f, 223/255f, 206/255f, 1.0f };
		
//		tweed.frame.removeGens( JmeGen.class );
		
		if ( output.faces == null )
			return;
		
		double bestWallArea = 0, bestRoofArea = 0;
		
		
		for ( Face f : output.faces.values() )  {
			
			double area = Loopz.area3 ( f.getLoopL() );
			
			Tag t = getTag( f.profile, RoofTag.class );
			if (t != null && area > bestRoofArea && ( (RoofTag) t ).color  != null) {
				roofColor = ( (RoofTag) t ).color;
				bestRoofArea = area;
			}
			
			t = getTag( f.profile, WallTag.class );
			
			if (t != null && area > bestWallArea && ((WallTag)t).color != null ) {
				wallColor = ((WallTag)t).color;
				bestWallArea = area;
			}
		}
		
		mbs.clear();
		
		output.addNonSkeletonSharedEdges(new RoofTag( roofColor ));
		edges( output, roofColor );
		
		for (List<Face> chain : Campz.findChains( output )) {
			
//			for ( Face f : output.faces.values() )
//				mbs.get(roofColor).add3d( Loopz.insertInnerEdges( f.getLoopL() ), zToYup );
			
			Optional<Tag> opt = chain.stream().flatMap( f -> f.profile.stream() ).filter( tag -> tag instanceof WallTag ).findAny();
			
			WallTag wt = null;
			
			Set<QuadF> features = new HashSet<>();
			
			MiniFacade mf = null;
			
			if (opt.isPresent() && (wt = (WallTag) opt.get() ).miniFacade != null ) {
				
				MiniFacade mf2 = new MiniFacade ( wt.miniFacade );
				
				
				Line facadeLine;
				{
					Edge e = chain.get( 0 ).edge;
					facadeLine = new Line ( e.end.x, e.end.y, e.start.x, e.start.y ); // we might rotate the facade to apply a set of features to a different side of the building.
				}
				
				// move/scale mf horizontally from mean-image-location to mesh-facade-location 
				double[] meshSE = findSE ( wt.miniFacade, facadeLine, chain );
				mf2.scaleX( meshSE[0], meshSE[1] );
				
				
				// find window locations in 3 space
				mf2.rects.values().stream()
						.flatMap ( f -> f.stream() )
						.map     ( r -> new QuadF (r, facadeLine) )
						.forEach ( q -> features.add(q) );
				
				mf = mf2;
			}

			for ( Face f : chain ) {
				face( f, mf, features, roofColor, wallColor );
			}
			
			for (QuadF w : features)
				if (( w.original.f == Feature.WINDOW || w.original.f == Feature.SHOP ) && w.foundAll() ) {
					createDormerWindow( w,  mbs.get( "wood", wood ),  mbs.get( "glass", glass ), 
							(float) wt.sillDepth, (float) wt.sillHeight, (float) wt.corniceHeight, 0.6, 0.9 );
				}
			
			
			
			for ( String mName : mbs.cache.keySet() )
				for (float[] mCol : mbs.cache.get( mName ).keySet() )		
					node.attachChild( mb2Geom( output, chain, mName, mCol ) );
		}
	}

	private void edges( Output output, float[] roofColor ) {

		GreebleEdge.roowWallGreeble( output, mbs.get( "tile", roofColor ),  mbs.get( "brick", new float[] {1,0,0,1 } ) );
		
		for ( Face f : output.faces.values() )
			GreebleEdge.roofGreeble( f, mbs.get( "tile", roofColor ) );
	}

	private Geometry mb2Geom( Output output, List<Face> chain, String name, float[] col ) {
		Geometry geom;
		{
			geom = new Geometry( "material_" + col[ 0 ] + "_" + col[ 1 ] + "_" + col[ 2 ], mbs.get( name, col ).getMesh() );
			geom.setUserData( Jme3z.MAT_KEY, name );
			
			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
			mat.setColor( "Diffuse", new ColorRGBA( col[ 0 ], col[ 1 ], col[ 2 ], col[ 3 ] ) );
			mat.setColor( "Ambient", new ColorRGBA( col[ 0 ] * 0.5f, col[ 1 ] * 0.5f, col[ 2 ] * 0.5f, col[ 3 ] ) );

			mat.setBoolean( "UseMaterialColors", true );

			geom.setMaterial( mat );
			geom.updateGeometricState();
			geom.updateModelBound();

			if ( chain != null )
				geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { new ClickMe() {
					@Override
					public void clicked( Object data ) {

						try {
							SwingUtilities.invokeAndWait( new Runnable() {

								@Override
								public void run() {
									selected( output, node, findSuperEdge( output, chain ) );
								}
							} );
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
				} } );

		}
		return geom;
	}
	
	private SuperEdge findSuperEdge( Output output, List<Face> chain ) {
		Bar bar = ((PlanSkeleton)output.skeleton).getDefiningColumn( chain.get( 0 ).edge ).defBar;
		SETag set = ((SETag) getTag( bar.tags, SETag.class ));
		
		return set == null ? null : set.se;
	}

	public interface OnClick {
		void selected( Output output, Node node, SuperEdge superEdge );
	}

	private void selected( Output output, Node out, SuperEdge superEdge ) {
		if (onClick != null)
			onClick.selected (output, out, superEdge );
	}
	
	private static double[] findSE( MiniFacade mf, Line l, List<Face> chain ) {
		
		double mlen = l.length();
		
		double lowest = Double.MAX_VALUE;
		Face bestFace = null;
		
		for (Face f : chain) {
			double[] bounds = Loopz.minMax( f.getLoopL() );
			if (bounds[5] - bounds[4] > 1 && bounds[4] < lowest) {
				bestFace = f;
				lowest = bounds[4];
			}
		}
		
		if (bestFace == null)
			return new double[] {mf.left, mf.left+ mf.width}; //!
		
		List<Double> params = bestFace.getLoopL().streamE().map( p3 -> l.findPPram( new Point2d ( p3.x, p3.y ) ) ).collect( Collectors.toList() );
		
		double[] out = new double[] {
			params.stream().mapToDouble( x->x ).min().getAsDouble() * mlen,
			params.stream().mapToDouble( x->x ).max().getAsDouble() * mlen
		};

		// if good, stretch whole minifacade to mesh
		if ( Mathz.inRange( ( out[1] - out[0]) / (mf.width), 0.66, 1.4 ) )
			return out;
		
		// else snap to the closest of start/end
		if ( 
				l.fromPPram( out[0] / mlen ).distance( l.fromPPram( mf.left / mlen ) ) >
				l.fromPPram( out[1] / mlen ).distance( l.fromPPram( (mf.left + mf.width ) / mlen ) ) )
			return new double[] {out[1] - mf.width, out[1]}; 
		else
			return new double[] {out[0], out[0] + mf.width }; 
	}
		
	private void face (Face f, MiniFacade mf, Set<QuadF> features, float[] roofColor, float[] wallColor ) {

		MatMeshBuilder faceColor = mbs.get( "error", new float[] { 0.5f, 0.5f, 0.5f, 1.0f } );
		
		WallTag wallTag = null;

		for ( Tag t : f.profile ) {

			if ( t instanceof WallTag ) {
				
				wallTag = ( (WallTag) t );
				faceColor = mbs.get( "brick", wallTag.color != null ? wallTag.color : wallColor );

			} else if ( t instanceof RoofTag ) {
				
				RoofTag rt = (RoofTag)t;
				faceColor = mbs.get("tile", rt.color != null ? rt.color : roofColor );
			}
		}

		if ( f.edge.getPlaneNormal().angle( new Vector3d( 0, 0,1 ) ) < 0.1 )
			wallTag = null;
		
		for ( Loop<Point3d> ll : f.getLoopL() ) {
			for ( Loopable<Point3d> lll : ll.loopableIterator() )
				if ( lll.get().distance( lll.getNext().get() ) > 200 )
					return;
		}


		for ( Loop<Point3d> ll : f.getLoopL() ) {
				
			if (wallTag != null) 
				wallTag.isGroundFloor = f.definingCorners.iterator().next().z < 1;
				
			mapTo2d( f, ll, mf, wallTag, features, faceColor );
		}
	}

	public static class LPoint3d extends Point3d {
		String nextLabel = "none";
		
		public LPoint3d (Point3d loc, String label) {
			super (loc);
			this.nextLabel = label;
		}
	}
	
	public final static String WALL_EDGE = "wall", ROOF_EDGE = "roof", FLOOR_LABEL = "floor";
	public LoopL<LPoint3d> findPerimeter (Face f) {
		
		LoopL<LPoint3d> out = new LoopL<>();

		
		for (Loop<SharedEdge> loop : f.edges) {
			
			Loop<LPoint3d> lout = new Loop<>();
			out.add(lout);
			
			for (SharedEdge se : loop) {
				
				String label;
				
				Face other = se.getOther( f );
				
				if (other == null || se.start.z < 0.1 && se.end.z < 0.1)
					label = FLOOR_LABEL;
				else if ( GreebleEdge.isWall( other ) )
					label = WALL_EDGE;
				else
					label = ROOF_EDGE;
				
				lout.append( new LPoint3d( se.getStart( f ), label ) );
			}
		}
		
		return out;
	}
	
	protected static class QuadF {
		
		Point3d[] 
			corners = new Point3d[4],
			found   = new Point3d[4];
		
		public FRect original;
		
		public QuadF( FRect rect, Line megafacade ) {
			
			this.original = rect;
			
			double mLen = megafacade .length();
			
			Point2d l = megafacade.fromPPram( rect.x / mLen ),
					r = megafacade.fromPPram( ( rect.x + rect.width )  / mLen );
			
			corners[0] = Pointz.to3( l, rect.y );
			corners[1] = Pointz.to3( l, rect.y + rect.height );
			corners[2] = Pointz.to3( r, rect.y + rect.height );
			corners[3] = Pointz.to3( r, rect.y );
		}

		public boolean foundAll() {
			return found[0] != null && found[1] != null && found[2] != null && found[3] != null;
		}

		public boolean project (Matrix4d to2d, Matrix4d to3d, Loop<Point2d> facade, LinearForm3D facePlane, Vector3d perp ) {

			boolean allInside = true;
			
			for (int i = 0; i < 4; i++) {
				
//				Point3d proj = new Point3d(corners[i]);
				
				Point3d sec = facePlane.collide( corners[i], perp );
				
				if (sec != null) {
				
				to2d.transform( sec );
				
				boolean inside = Loopz.inside( new Point2d (sec.x, sec.z), facade );
				
				allInside &= inside;
				
				if ( inside ) {
					sec.y = 0;
					to3d.transform( sec );
					found[i] = sec;
				}
				}
			}
			
			return allInside;
		}

		public void setBounds( Matrix4d to2d, FRect bounds ) {

			List<Point2d> envelop = new ArrayList<>();
			
			for (int i = 0; i < 4; i++) {
				Point3d tmp = new Point3d(corners[i]);
				to2d.transform( tmp );
				envelop.add( Pointz.to2( tmp ) );
			}
			
			bounds.setFrom( new DRectangle( envelop ) );
		}
	}
	
	protected void mapTo2d( 
			Face f, 
			Loop<Point3d> ll, 
			MiniFacade mf,
			WallTag wallTag, 
			Set<QuadF> features, 
			MatMeshBuilder faceMaterial ) {
		
		Matrix4d to2dXY = new Matrix4d();
		
		Vector3d up    = f.edge.uphill,
				 along = f.edge.direction(),
				 out   = f.edge.getPlaneNormal();
		
		along.normalize();
		
		to2dXY.setRow( 2, up.x, up.y, up.z, 0);
		to2dXY.setRow( 1, out.x, out.y, out.z, 0);
		to2dXY.setRow( 0, -along.x, -along.y, -along.z, 0);
		
		Point3d bottomS = f.definingSE.iterator().next().getStart( f ), bottomE = f.definingSE.iterator().next().getEnd( f );
		
		Point3d start = new Point3d ( bottomS );
		Point3d end   = new Point3d ( bottomE );
		
		to2dXY.m33 = 1;
		to2dXY.transform( start );
		
		to2dXY.m03 = -start.x;
		to2dXY.m13 = -start.y;
		to2dXY.m23 = -start.z;

		start = new Point3d ( bottomS );
		to2dXY.transform( start );
		to2dXY.transform( end );
		
		Loop<Point2d> flat = Loopz.to2dLoop( Loopz.transform (ll, to2dXY), 1, new HashMap<>() );

		Matrix4d to3d = new Matrix4d( to2dXY );
		to3d.invert();
		
		{ // face in z-up, we're in y-up
			double[] 
					one = new double[4], 
					two = new double[4];
			
			to3d.getRow( 1, one );
			to3d.getRow( 2, two );
			to3d.setRow( 1, two );
			to3d.setRow( 2, one );
		}
		
		Matrix4d to2d = new Matrix4d( to3d ); // now in jme space
		to2d.invert();
		
		MiniFacade forFace = null;
		if (mf != null) {
			forFace = new MiniFacade(mf);
			forFace.rects.clear();
		}
		
		LinearForm3D facePlane = new LinearForm3D( new Vector3d( out.x, out.z, out.y ), new Point3d( bottomS.x, bottomS.z, bottomS.y ) );
		
		LoopL<Point2d> sides = null;
		DRectangle facadeRect = null;
		
		if ( wallTag != null ) {
			sides = findRectagle( flat, Pointz.to2( start ), Pointz.to2( end ) );

			if ( sides != null )
				facadeRect = findRect( sides.remove( 0 ) );
		}
		
		List<DRectangle> floors = new ArrayList();
		List<MeshBuilder> materials = new ArrayList();
		
		if (wallTag != null && facadeRect != null && mf != null && 
				wallTag.isGroundFloor && mf.groundFloorHeight > 0 && wallTag.groundFloorColor != null &&
			facadeRect.x < mf.groundFloorHeight && facadeRect.getMaxX() > mf.groundFloorHeight) 
		{
			
			floors.addAll ( facadeRect.splitY( mf.groundFloorHeight ) );
			
			MatMeshBuilder gfm = mbs.get( "brick", wallTag.groundFloorColor ); 
			
			for ( Loop<Point2d> loop : sides ) {
				
				Loop<Point2d>[] cut = Loopz.cutConvex( loop, new LinearForm( 0, 1, mf.groundFloorHeight ) );
				faceMaterial.add( cut[ 1 ].singleton(), to3d );
				gfm.add( cut[ 0 ].singleton(), to3d );
			}
			
			materials.add( gfm );
			materials.add( faceMaterial );
		}
		else {
			floors.add( facadeRect );
			materials.add( faceMaterial );
			if (sides != null)
				faceMaterial.add( sides, to3d );
		}

		for ( int j = 0; j < floors.size(); j++ ) {
			
			DRectangle floorRect = floors.get( j );
			MeshBuilder m = materials.get( j );
			
			Iterator<QuadF> quit = features.iterator();
			while ( quit.hasNext() ) {

				QuadF n = quit.next();

				if ( n.project( to2d, to3d, flat, facePlane, new Vector3d( along.y, 0, -along.x ) ) && wallTag != null && floorRect != null && forFace != null ) {

					// set the vertical bounds, so we can just render in 2d
					FRect bounds = new FRect( n.original );
					n.setBounds( to2d, bounds );

					if ( floorRect.contains( bounds ) ) {
						forFace.rects.put( n.original.f, bounds );
						quit.remove();
					}
				}
			}
		
		if (wallTag == null || forFace == null || floorRect == null) {
			m.add (flat.singleton(), to3d);
			return;
		}
		
		List<DRectangle> occlusions = new ArrayList<>();
		for (LineHeight lh : wallTag.occlusions) {

			Point3d s = new Point3d( lh.start.x, lh.start.y, lh.min ), 
					e = new Point3d( lh.end  .x, lh.end  .y, lh.max );
			
			to2dXY.transform( s );
			to2dXY.transform( e );

			occlusions.add( new DRectangle( Math.min( s.x, e.x ), s.z, Math.abs( e.x - s.x ), Math.abs ( e.z - s.z)  ) );
		}

			
		buildGrid (
				floorRect,
				to3d,
				forFace,
				m,
				wallTag );
		
		}
	}
	
	protected DRectangle findRect( Loop<Point2d> rect ) {
		double[] bounds = Loopz.minMax2d( rect );
		
		DRectangle all = new DRectangle(
				bounds[0], 
				bounds[2], 
				bounds[1] - bounds[0], 
				bounds[3] - bounds[2] );
		
		return all;
	}
	
	
	protected void moulding( Matrix4d to3d, DRectangle rect, MeshBuilder mb ) {
		
		double hh = rect.height/2;
		
		Point3d start = new Point3d (rect.x, 0, rect.y+hh), end = new Point3d (rect.getMaxX(), 0, rect.y+hh);
		
		to3d.transform( start );
		to3d.transform( end   );
		
		Line3d line= new Line3d(start, end);
		
		Vector3d dir = line.dir();
		dir.normalize();
		Vector3d nDir = new Vector3d( dir );
		nDir.scale( -1 );
		
		LinearForm3D left = new LinearForm3D( nDir, start ), right = new LinearForm3D( dir, end);
		
		LinearForm3D wall = new LinearForm3D( to3d.m01,to3d.m11,to3d.m21 );
		wall.findD(start);
		
		Tube.tube( mb, Collections.singleton( left ), Collections.singleton( right ), 
				line, wall, wall, new CrossGen() {
					
					@Override
					public List<Point2d> gen( Vector2d down, Vector2d up ) {
						
						Vector2d d = new Vector2d(down);
						d.normalize();
						
						Vector2d dP = new Vector2d(d.y, -d.x );
						
						List<Point2d> out = new ArrayList();
						
						for (double[] coords : new double[][] {
							{1.00, 0.00},
							{1.00, 0.05},
							{0.66, 0.05},
							{0.66, 0.10},
							{0.33, 0.10},
							{0.33, 0.17},
							{0.00, 0.17},
							{0.00, 0.00},
							} ) {
								Point2d tmp = new Point2d(d);
								tmp.scale (coords[0] * rect.height - hh);
								Point2d tmp2 = new Point2d( dP );
								tmp2.scale (coords[1]);
								tmp.add(tmp2);
							
								out.add(tmp);
						}
						
						return out;
					}
				} );
		
	}
	
	protected boolean visible( DRectangle dRectangle, List<DRectangle> occlusions ) {
		
		for (Point2d p : dRectangle.points()) 
			for (DRectangle d : occlusions)
				if (d.contains( p ))
					return false;
		
		return true;
	}

	protected void createDoor( DRectangle door, Matrix4d to3d, 
			MeshBuilder woof, MeshBuilder wood, double depth ) {
		
		Point2d[] pts = door.points();
		
		Point3d[] ptt = new Point3d[4];
		
		for (int i = 0; i < 4; i++) {
			ptt[i] = Pointz.to3( pts[i] );
			to3d.transform( ptt[i] ); 
		}
		
		Vector3d along = new Vector3d(ptt[3]);
		along.sub(ptt[0]);
		along.normalize();
		
		Vector3d up = new Vector3d(ptt[1]);
		up.sub(ptt[0]);
		up.normalize();

		Vector3d out = new Vector3d();
		out.cross( along, up );
		out.normalize();
		
		Vector3d loc = new Vector3d();
		loc.cross( along, up );
		loc.scale ( -depth / loc.length() );
		loc.add(ptt[0]);
		
		
		Vector3f lo = Jme3z.to ( loc ),
				ou = Jme3z.to(out), al = Jme3z.to(along), u = Jme3z.to(up);
		
		woof.addInsideRect( Jme3z.to( ptt[0] ), ou, al, u,
				 -(float)depth, (float)door.width, (float) door.height  );
		
		float height = (float)door.height;
		float width = (float)door.width;
		
		wood.addCube( lo, u, al, ou, (float) height, (float) width, 0.1f );
		
		float fWidth = 0.05f;
		
		// bottom, top
		wood.addCube( lo.add(u.mult( ( height - fWidth))), u, al, ou, fWidth, width, 0.15f );
		
		// left, right
		wood.addCube( lo,                            u, al, ou, height, fWidth, 0.15f );
		wood.addCube( lo.add(al.mult(width-fWidth)), u, al, ou, height, fWidth, 0.15f );
	}

	protected void createBalcony( DRectangle balc, Matrix4d to3d, 
			MeshBuilder mat, double _depth ) {
		
		Point2d[] pts = balc.points();
		
		Point3d[] ptt = new Point3d[4];
		
		
		Vector3f[] ptf = new Vector3f[4];
		
		for (int i = 0; i < 4; i++) {
			ptt[i] = Pointz.to3( pts[i] );
			to3d.transform( ptt[i] ); 
			ptf[i] = Jme3z.to(ptt[i]);
		}
		
		Vector3f along = ptf[3].subtract( ptf[0] );
		along.normalizeLocal();
		
		Vector3f up = ptf[1].subtract(ptf[0]);
		up.normalizeLocal();
		Vector3f out = along.cross( up );
		Vector3f loc = ptf[0];

		
		float bg = 0.08f, sm = 0.03f, height  = balc.heightF(), 
				depth = (float) _depth, width = balc.widthF(),
				spacing = 0.3f, bgsm = (bg - sm) / 2;
		
		// floor
		mat.addCube(loc, up, along, out, bg, width, (float) depth );
		
		// top railings
		mat.addCube(loc.add(up.mult( height )), up, along, out, bg, bg, depth );
		mat.addCube(loc.add(up.mult( height ).add(along.mult(width-bg))), up, along, out, bg, bg, depth );
		mat.addCube( loc.add( up.mult( height ).add( out.mult( depth - bg ) ) ), up, along, out, bg, width, bg );
		
		int count = (int)(depth/spacing);
		
		// side decorations
		for (int c = 0; c< count+1; c++) {
			mat.addCube(loc.add(out.mult(c * spacing)).add(along.mult(bgsm)) , up, along, out, height, sm, sm );
			mat.addCube(loc.add(out.mult(c * spacing)).add(along.mult(width - sm - bgsm)) , up, along, out, height, sm, sm );
		}
		
		count = (int) ( width / spacing);
		spacing = (width - sm -2*bgsm) / count;
				
		// top decorations
		for (int c = 0; c< count+1; c++) {
			
			mat.addCube(loc.add(out.mult(depth - sm-bgsm)).add(along.mult(bgsm + spacing * c)) , up, along, out, height, sm, sm);
			
		}
	}
	
	protected void createWindow( DRectangle winPanel, Matrix4d to3d, 
			MeshBuilder wall, 
			MeshBuilder window, 
			MeshBuilder glass, 
			double depth,
			float sillDepth, float sillHeight,
			float corniceHeight,
			double panelWidth, double panelHeight ) {
		
		Point2d[] pts = winPanel.points();
		
		Point3d[] ptt = new Point3d[4];
		
		for (int i = 0; i < 4; i++) {
			ptt[i] = Pointz.to3( pts[i] );
			to3d.transform( ptt[i] ); 
		}
		
		Vector3d along = new Vector3d(ptt[3]);
		along.sub(ptt[0]);
		along.normalize();
		
		Vector3d up = new Vector3d(ptt[1]);
		up.sub(ptt[0]);
		up.normalize();

		Vector3d out = new Vector3d();
		out.cross( along, up );
		out.scale(-1/out.length());
		
		Vector3d loc = new Vector3d();
		loc.cross( along, up );
		loc.scale ( -depth / loc.length() );
		loc.add(ptt[0]);
		
		WindowGen.createWindow( window, glass, new Window( Jme3z.to ( loc ), Jme3z.to(along), Jme3z.to(up), 
				winPanel.width, winPanel.height, 0.3, panelWidth, panelHeight ) ); 
		
		Vector3f u = Jme3z.to(up), o = Jme3z.to( out );
		
		wall.addInsideRect( Jme3z.to ( ptt[0] ), o, Jme3z.to(along), u,  
				 (float)depth, (float)winPanel.width,(float) winPanel.height  );
		
		if (sillDepth > 0 && sillHeight > 0)
			window.addCube( Jme3z.to ( ptt[0] ).add( u.mult( -sillHeight + 0.01f ) ).add( o.mult( -sillDepth) ),
				Jme3z.to(out), Jme3z.to(along), Jme3z.to(up),
				(float)depth + sillDepth, (float)winPanel.width,(float) sillHeight  );
		
		if (corniceHeight > 0) 
			moulding( to3d, new DRectangle(winPanel.x, winPanel.getMaxY(), winPanel.width, corniceHeight), wall );
	}
	
	protected void createDormerWindow( 
			QuadF l,
			MeshBuilder window, 
			MeshBuilder glass, 
			float sillDepth, 
			float sillHeight,
			float corniceHeight,
			double panelWidth, 
			double panelHeight ) {
		
		Vector3d along = new Vector3d(l.corners[3]);
		along.sub(l.corners[0]);
		along.normalize();
		
		Vector3d up = new Vector3d(0,1,0);
		
		Vector3d out = new Vector3d();
		out.cross( along, up );
		out.scale( 1 / out.length());
		
		Line3d lout;
		{
			Point3d away = new Point3d( l.corners[ 0 ] );
			away.add( out );
			lout = new Line3d( new Point3d( l.corners[ 0 ] ), away );
		}
		
		Vector3d loc = new Vector3d(l.found[0]);

		if ( lout.findPPram( l.found[ 0 ] ) < lout.findPPram( l.found[ 1 ] ) ) { // outwards going wall...
			loc = new Vector3d( up );
			loc.scale( -l.original.height );
			loc.add( l.found[ 1 ] );
		}
		
		{
			Vector3d avoidRoof = new Vector3d(out);
			avoidRoof.scale( 0.09 );;
			loc.add( avoidRoof );
		}
		
		Point3d deepest = Arrays.stream( l.found )
		.map ( p -> new Pair<Point3d, Double> (p,  lout.findPPram( p )) )
		.max( (a,b ) -> b.second().compareTo( a.second() ) ).get().first();
		
		double depth = lout.closestPointOn( deepest, false ).distance( lout.closestPointOn( new Point3d( loc ), false ) ); 
				
//				MUtils.max( 
//				Math.abs (l.corners[0].distance( l.found[0] )), 
//				Math.abs (l.corners[1].distance( l.found[1] )), 
//				Math.abs (l.corners[2].distance( l.found[2] )), 
//				Math.abs (l.corners[3].distance( l.found[3] )) 
//				) ;
		
		WindowGen.createWindow( window, glass, new Window( Jme3z.to ( loc ), Jme3z.to(along), Jme3z.to(up), 
				l.original.width, l.original.height, depth, panelWidth, panelHeight ) ); 
		
//		Vector3f u = Jme3z.to(up), o = Jme3z.to( out );
		
//		if (sillDepth > 0)
//			window.addCube( Jme3z.to ( ptt[0] ).add( u.mult( -sillHeight + 0.01f ) ).add( o.mult( -sillDepth) ),
//					Jme3z.to(out), Jme3z.to(along), Jme3z.to(up),
//					(float)depth + sillDepth, (float)winPanel.width,(float) sillHeight  );
//		
//		if (corniceHeight > 0) 
//			moulding( to3d, new DRectangle(winPanel.x, winPanel.getMaxY(), winPanel.width, corniceHeight), wall );
	}

	protected LoopL<Point2d> findRectagle( Loop<Point2d> flat, Point2d s, Point2d e ) {

//		new Plot(flat);
		
		List<Point2d> left = new ArrayList<>(), right = new ArrayList<>();
		
		for (Loopable<Point2d> pt : flat.loopableIterator()) {
			if (pt.get().equals (s) && left.isEmpty()) {
				double h = pt.get().y;
				do {
					left.add(pt.get());
					h = pt.get().y;
					pt = pt.getNext();
				}
				while (pt.get().y >= h);
			}
		
			if (pt.get().equals (e) && right.isEmpty()) {
				
				double h = pt.get().y;
				do {
					right.add(pt.get());
					h = pt.get().y;
					pt = pt.getPrev();
				}
				while (pt.get().y >= h);
			}
		}

		for (List<Point2d> l : new List[] {left, right}) 
			while ( l.size() > 2 && l.get( l.size()-2 ).y == l.get(l.size() -1).y)
				l.remove (l.size()-1);

		if (left.size() < 2 || right.size() < 2 )
			return null;
		
		double tween = (
					left .stream().mapToDouble( p -> p.x ).max().getAsDouble() + 
					right.stream().mapToDouble( p -> p.x ).min().getAsDouble() ) /2; 
		
		PtInChain
				xyL = findValid(left , tween, true),
				xyR = findValid(right, tween, false);
		
		double height = Math.min (xyL.y, xyR.y);
		
		if (xyL.y > xyR.y)
			xyL = setToHeight (left, true, xyL.x, height);
		else
			xyR = setToHeight (right, false, xyR.x, height);
		
		LoopL<Point2d> out = new LoopL<>();
		
		{
			Loop<Point2d> rect = new Loop<>();
			out.add( rect );
			rect.append( new Point2d( xyL.x, 0 ) );
			rect.append( new Point2d( xyL.x, xyL.y ) );
			rect.append( new Point2d( xyR.x, xyR.y ) );
			rect.append( new Point2d( xyR.x, 0 ) );
		}
		
		Loop<Point2d> lef = toPoly( left, xyL );
		if ( Loopz.area( lef ) > 0.001 )
			out.add( lef );
		
		Loop<Point2d> rig = toPoly( right, xyR );
		rig.reverse();
		if ( Loopz.area( rig ) > 0.001 )
			out.add( rig );
		
		{
			Loop<Point2d> top = new Loop<>();

			top.append( new Point2d( xyL.x, xyL.y ) );

			if ( xyL.prevPt < left.size() - 1 )
				top.append( new Line( left.get( xyL.prevPt ), left.get( xyL.prevPt + 1 ) ).fromPPram( xyL.frac ) );

			for ( int i = xyL.prevPt + 1; i < left.size(); i++ )
				top.append( left.get( i ) );

			for ( int i = right.size() - 1; i > xyR.prevPt; i-- )
				top.append( right.get( i ) );

			if ( xyR.prevPt < right.size() - 1 )
				top.append( new Line( right.get( xyR.prevPt ), right.get( xyR.prevPt + 1 ) ).fromPPram( xyR.frac ) );

			top.append( new Point2d( xyR.x, xyR.y ) );

			if ( Loopz.area( top ) > 0.001 )
				out.add( top );
		}
	
		return out;
	}

	private static Loop<Point2d> toPoly( List<Point2d> left, PtInChain xyL ) {
		
		Loop<Point2d> lef = new Loop<>();
		for (int i = 0; i <= xyL.prevPt; i++)
			lef.append (left.get(i));
		
		if (xyL.frac > 0 && xyL.prevPt < left.size()-1) 
			lef.append (new Line ( left.get ( xyL.prevPt ), left.get(xyL.prevPt + 1)).fromPPram( xyL.frac ) );
		
		lef.append( new Point2d(xyL.x, xyL.y) );
		lef.append( new Point2d(xyL.x, 0) );
		return lef;
	}
	
	private static PtInChain setToHeight( List<Point2d> chain, boolean left, double x2, double y2 ) {
		
		double bestX = chain.get(0).x;
		
		for (int i = 1; i < chain.size(); i++) {
			Point2d 
				p = chain.get(i-1),
				n = chain.get(i);
			
			bestX = left ? Math.max (p.x, bestX) : Math.min (p.x, bestX);
			
			PtInChain first = new PtInChain( new Point2d ( n ), i, 0 );
			first.x = bestX;
			
			if (Math.abs(n.y-y2) < 0.001) {
				first.x = left ? Math.max (n.x, first.x) : Math.min (n.x, first.x);
				return first;
			}
			
			Line pn = new Line(p, n);
			
			if (n.y > y2) {
				Point2d sec = new LinearForm(pn).intersect( new LinearForm( 0, 1, y2 ) );
				if (sec == null)
					return first;
				
				sec.x = left ? Math.max (bestX, sec.x) : Math.min (bestX, sec.x);
				
				return new PtInChain( sec, i-1, pn.findPPram( sec ) );
			}
		}
		return null;
	}

	private static PtInChain findValid( List<Point2d> chain, double startingX, boolean left ) {
		
		double bestArea = -Double.MAX_VALUE;
		PtInChain bestPt = new PtInChain ( chain.get(0), 0, 0 );
		
		double bestX = chain.get(0).x;
		
		Point2d pt;
		for (int i = 0; i < chain.size(); i++) {
			
			pt    = chain.get(i);
			bestX = left ? Math.max (pt.x, bestX) : Math.min (pt.x, bestX); 
			
			double area = Math.abs(startingX-bestX) * pt.y;

			if (area > bestArea || area == 0 && bestArea == 0 && pt.y > bestPt.y) {
				bestPt = new PtInChain( new Point2d ( bestX, pt.y), i, 0);
				bestArea = area;
			}
		}
		
		return bestPt;
	}
	
	private static class PtInChain extends Point2d {
		
		public PtInChain( Point2d point2d, int pos, double frac ) {
			super (point2d);
			this.prevPt = pos;
			this.frac = frac;
		}
		
		int prevPt;
		double frac;
	}

	public static Tag getTag( Set<Tag> tags, Class<? extends Tag> class1 ) {
		
		for (Tag t : tags)
			if (t.getClass() == class1)
				return t;
		
		return null;
	}

	private void buildGrid( DRectangle all, Matrix4d to3d, MiniFacade mf, 
			MeshBuilder wallColorMat, WallTag wallTag ) {

		Grid g = new Grid( .10, all.x, all.getMaxX(), all.y, all.getMaxY() );

		if ( mf != null ) {

			//			MiniFacade mf = wallTag.miniFacade;

			for ( FRect w : mf.rects.get( Feature.WINDOW ) ) {

				if ( all.contains( w ) )
					g.insert( w, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createWindow( rect, to3d, 
									wallColorMat, mbs.get( "wood", wood ), mbs.get( "glass", glass ), 
									wallTag.windowDepth, 
									(float) wallTag.sillDepth, 
									(float) w.attachedHeight.get(Feature.SILL).d, 
									(float) w.attachedHeight.get(Feature.CORNICE).d, 0.6, 0.9 );
						}
					} );
				
				double bHeight = w.attachedHeight.get(Feature.BALCONY).d;
				if (bHeight > 0) {
					
					DRectangle balcon = new DRectangle();
					balcon.setFrom (w);
					balcon.grow (0.2);
					balcon.height = bHeight;
					
					createBalcony( balcon, to3d, mbs.get( "metal", balcony ), wallTag.balconyDepth );
				}
				
			}

			for ( FRect s_ : mf.rects.get( Feature.SHOP ) ) {
				
				FRect s = new FRect(s_);
				
				DRectangle rect = all.intersect( s );
				
				if (rect != null) {
				s.setFrom(  rect );
				
					g.insert( s, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {

							createWindow( rect, to3d, wallColorMat, mbs.get( "wood", wood ), mbs.get( "glass", glass ), 
									wallTag.windowDepth, 
									(float) wallTag.sillDepth, 
									(float) s.attachedHeight.get(Feature.SILL).d, 
									(float) s.attachedHeight.get(Feature.CORNICE).d,
									1.5, 2 );
						}
					} );
				}
			}
			for ( DRectangle d : mf.rects.get( Feature.DOOR ) ) {
				if ( all.contains( d ) )
					g.insert( d, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createDoor( rect, to3d, wallColorMat, mbs.get( "wood", new float[] {0,0,0.3f, 1} ), wallTag.doorDepth );
						}
					} );
			}

			for ( DRectangle b : mf.rects.get( Feature.BALCONY ) ) {
				if ( all.contains( b ) )
					g.insert( b, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createBalcony( rect, to3d, mbs.get( "metal", balcony ), wallTag.balconyDepth );
						}

						@Override
						public boolean noneBehind() {
							return true;
						}
					} );
			}

			for ( DRectangle b : mf.rects.get( Feature.MOULDING ) ) {
				if ( all.contains( b ) )
					g.insert( b, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							moulding( to3d, rect, mbs.get( "brick", moudling ) );
						}
					} );
			}
		}

		g.instance( new Griddable() {
			@Override
			public void instance( DRectangle rect ) {
				wallColorMat.add( rect, to3d );
			}
		} );
	}
}
