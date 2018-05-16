package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
import org.twak.siteplan.campskeleton.PlanSkeleton.ColumnProperties;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.RoofTag;
import org.twak.tweed.gen.skel.SETag;
import org.twak.tweed.gen.skel.WallTag;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.LinearForm;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.ui.Colourz;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.App.TextureUVs;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import smile.math.Math;

public class GreebleSkel {

	private static final double TILE_UV_SCALE = 0.4;
	private static final String TILE_TEXTURED = "tile_textured";
	private static final String TILE = "tile";
	private static final String BRICK = "brick";
	private static final String TILE_JPG = "tile.jpg";
	
	public static final String Appearance = "appearance";
	Tweed tweed;
	Node node = new Node();
	
	OnClick onClick;
	
	GreebleGrid greebleGrid;
	
//	boolean isTextured = false;
//	DRectangle roofBounds;
	
	private HasApp roofApp;
	
	public static float[] 
			BLANK_ROOF = new float[] {0.5f, 0.5f, 0.5f, 1 },
			BLANK_WALL = new float[] {228/255f, 223/255f, 206/255f, 1.0f };
	
	public GreebleSkel( Tweed tweed ) {
		this.tweed = tweed;
	}

	public Node showSkeleton( Output output, OnClick onClick, java.util.Map<Object, Face> occluderLookup, HasApp roof ) {
		
		this.onClick = onClick;
		this.roofApp = roof;
		createMesh( output, occluderLookup );
		return node;
	}

	public void createMesh( Output output, java.util.Map<Object, Face> occluderLookup ) {
		
		float[] roofColor = BLANK_ROOF;
		
		if ( output.faces == null )
			return;
		
		double bestRoofArea = 0;
		
		
//		roofBounds = new DRectangle.Enveloper();
		
		
		roofColor = Colourz.toF4( HasApp.get( roofApp ).color );
		
		for ( Face f : output.faces.values() )  {
			
			Tag t = GreebleHelper.getTag( f.profile, WallTag.class );
			
			WallTag wt = ((WallTag)t);
			
			if (t != null ) {
				
				wt.miniFacade.postState = null;
				
//				for ( Loop<SharedEdge> lc : f.edges )
//					for ( SharedEdge se : lc ) {
//						roofBounds.envelop( Pointz.to2( se.start ) );
//						roofBounds.envelop( Pointz.to2( se.end ) );
//					}
				wt.miniFacade.postState = new PostProcessState();
			}
		}
		
//		roofBounds.grow( 2 );
//		roofBounds.height = roofBounds.width = Math.max( roofBounds.width, roofBounds.height );
		
		output.addNonSkeletonSharedEdges(new RoofTag( roofColor ));
		
		List<List<Face>> chains = Campz.findChains( output );

		Set<MiniFacade> seen = new HashSet<>();
		
		// give each minifacade a chance to update its features based on the skeleton result
		for (List<Face> chain : chains) {
			
			Set<WallTag> opt = chain.stream().flatMap( f -> f.profile.stream() )
					.filter( tag -> tag instanceof WallTag ).map( t -> (WallTag)t ).collect( Collectors.toSet() );
			
			for (WallTag wt : opt) {
				
				MiniFacade mf = wt.miniFacade;
				
				Edge e = chain.get( 0 ).edge;
				Line megafacade = new Line ( e.end.x, e.end.y, e.start.x, e.start.y );
				double mfl = megafacade.length();
				Vector2d dir =megafacade.dir();
				LinearForm3D lf = new LinearForm3D( new Vector3d(-dir.y, dir.x, 0), e.start );
				
				for (Face f : chain) 
					if (GreebleHelper.getTag( f.profile, WallTag.class ) != null)
						for (Loop<Point2d> face : projectTo( megafacade, mfl, lf, f ) )
							mf.postState.skelFaces.add( face );
				
				if ( occluderLookup != null )
					for ( Object o : wt.occlusions ) {
						Face f = occluderLookup.get( o );
						if ( f != null ) 
							mf.postState.occluders.add( projectTo( megafacade, mfl, lf, f ) );
				}
				
				
				if ( seen.add( mf ) ) {
					mf.postState.outerFacadeRect = GreebleHelper.findRect(mf.postState.skelFaces);
					mf.width = chain.get( 0 ).edge.length();
					mf.featureGen.update();
				}
			}
		}
		
		greebleGrid = new GreebleGrid(tweed, new MMeshBuilderCache());
		
		// generate geometry for each face
		for (List<Face> chain : chains) {
			
			Optional<Tag> opt = chain.stream().flatMap( f -> f.profile.stream() ).filter( tag -> tag instanceof WallTag ).findAny();
			
			WallTag wt = null;
			
			
			MiniFacade mf2 = null;
			
			
			Set<QuadF> processedFeatures = new HashSet<>();
			
			Line megafacade = new Line();
			if (opt.isPresent() && (wt = (WallTag) opt.get() ).miniFacade != null ) {
				
				mf2 = wt.miniFacade;
				
				{
					Edge e = chain.get( 0 ).edge;
					megafacade.set( e.end.x, e.end.y, e.start.x, e.start.y ); // we might rotate the facade to apply a set of features to a different side of the building.
				}
				
				if (TweedSettings.settings.snapFacadeWidth) {
					
					mf2 = new MiniFacade ( wt.miniFacade );
					mf2.postState = wt.miniFacade.postState;
					
					// move/scale mf horizontally from mean-image-location to mesh-facade-location
					double[] meshSE = findSE ( wt.miniFacade, megafacade, chain );
					mf2.scaleX( meshSE[0], meshSE[1] );
				}
				
				mf2.featureGen.values().stream()
					.flatMap ( k -> k.stream() )
					.map     ( r -> new QuadF (r, megafacade) )
					.forEach ( q -> processedFeatures.add(q) );
			}

			Set<QuadF> allFeatures = new LinkedHashSet<>();
			allFeatures.addAll( processedFeatures );
			
			for ( Face f : chain ) 
				face( f, mf2, processedFeatures, megafacade );

			allFeatures.removeAll( processedFeatures );
			for (QuadF q1 : allFeatures)
				mf2.postState.generatedWindows.add(q1.original);
			
			if ( TweedSettings.settings.createDormers ) {
				Iterator<QuadF> quit = processedFeatures.iterator();
				while ( quit.hasNext() ) {
					QuadF w = quit.next();
					if ( ( w.original.f == Feature.WINDOW || w.original.f == Feature.SHOP ) && w.foundAll() ) {
						greebleGrid.createDormerWindow( w, greebleGrid.mbs.WOOD, greebleGrid.mbs.GLASS, (float) wt.sillDepth, (float) wt.sillHeight, (float) wt.corniceHeight, 0.6, 0.9 );
						quit.remove();
					}
				}
			}
			
			edges( output, roofColor );
			
			// output per-material objects
			greebleGrid.attachAll(node, chain, output, new ClickMe() {
				@Override
				public void clicked( Object data ) {

					try {
						SwingUtilities.invokeAndWait( new Runnable() {

							@Override
							public void run() {
								selected( output, node, findSuperEdge( output, chain ),
										data instanceof Spatial ? ((HasApp)((Object[])((Spatial) data).getUserData( Appearance ) ) [0])  
												: null);
							}
						} );
					} catch ( Throwable th ) {
						th.printStackTrace();
					}
				}
			});
		}
	}

	private LoopL<Point2d> projectTo( Line megafacade, double mfl, LinearForm3D lf, Face f ) {
		
		if (f == null)
			return new LoopL<>();
		
		return f.points.new Map<Point2d>() {
			@Override
				public Point2d map( Loopable<Point3d> input ) {
					Point3d i = input.get();
					Point3d inSpace = lf.project( i );
					Point2d onGround = new Point2d(inSpace.x, inSpace.y);
					return new Point2d (megafacade.findPPram( onGround ) * mfl, inSpace.z); 
				} }
			.run();
	}

	

	public interface OnClick {
		void selected( Output output, Node node, SuperEdge superEdge, HasApp ha );
	}

	private void selected( Output output, Node out, SuperEdge superEdge, HasApp ha ) {
		if (onClick != null)
			onClick.selected (output, out, superEdge, ha );
	}
	
	private SuperEdge findSuperEdge( Output output, List<Face> chain ) {
		
		ColumnProperties col = ((PlanSkeleton)output.skeleton).getDefiningColumn( chain.get( 0 ).edge );
		if (col == null)
			return null;
		Bar bar = col.defBar;
		SETag set = ((SETag) GreebleHelper.getTag( bar.tags, SETag.class ));
		
		return set == null ? null : set.se;
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
		
	private void face (Face f, MiniFacade mf, Set<QuadF> features, Line megafacade ) {

		MatMeshBuilder faceColor = greebleGrid.mbs.ERROR;
		
		WallTag wt = null;

		for ( Tag t : f.profile ) {

			if ( t instanceof WallTag ) {
				
				wt = ( (WallTag) t );
				
				switch ( mf.app.appMode ) {
				
				case Color:
					// hashcode to force unique for selection.
					faceColor = greebleGrid.mbs.get( BRICK+mf.app.hashCode(), mf.app.color, mf );
//					faceColor = greebleGrid.mbs.get( BRICK, mf.app.color != null ? Colourz mf.app.color : wallColor, mf );
					break;
				case Bitmap:
					faceColor = greebleGrid.mbs.getTexture( TILE_TEXTURED+mf.app.hashCode(), TILE_JPG, mf );
					break;
				case Net:
					faceColor = greebleGrid.mbs.getTexture( "texture_"+mf.app.texture+mf.app.hashCode() , mf.app.texture, mf );
					break;
				}

			} else if ( t instanceof RoofTag ) {
				
//				RoofTag rt = (RoofTag)t;
				
				App ra = HasApp.get ( roofApp );
				
				switch ( ra.appMode ) {

				case Color:
					faceColor = greebleGrid.mbs.get( TILE, ra.color, roofApp );
					break;
				case Bitmap:
					faceColor = greebleGrid.mbs.getTexture( TILE_TEXTURED, TILE_JPG, roofApp );
					break;
				case Net:
					faceColor = greebleGrid.mbs.getTexture( "texture_" + ra.texture, ra.texture, roofApp );
					break;
				}
			}
		}

		if ( f.edge.getPlaneNormal().angle( new Vector3d( 0, 0,1 ) ) < 0.1 )
			wt = null;
		
		for ( Loop<Point3d> ll : f.getLoopL() ) {
			for ( Loopable<Point3d> lll : ll.loopableIterator() )
				if ( lll.get().distance( lll.getNext().get() ) > 200 )
					return;
		}

		for ( Loop<LPoint3d> ll : GreebleHelper.findPerimeter( f ) ) {
				
			if (wt != null) 
				wt.isGroundFloor = f.definingCorners.iterator().next().z < 1;
				
			mapTo2d( f, ll, mf, wt, features, faceColor, megafacade );
		}
	}
	
	protected static class QuadF {
		
		// potential window - either recessed, dormer (if a corner is between faces) , or deleted (if a corner outside all faces in chain)
		
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

		public boolean project (Matrix4d to2d, Matrix4d to3d, Loop<? extends Point2d> facade, LinearForm3D facePlane, Vector3d perp ) {

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
				envelop.add( Pointz.to2XZ( tmp ) );
			}
			
			bounds.setFrom( new DRectangle( envelop ) );
		}
	}
	
	protected void mapTo2d( 
			Face f, 
			Loop<LPoint3d> ll, 
			MiniFacade mf,
			WallTag wallTag, 
			Set<QuadF> features, 
			MatMeshBuilder faceMaterial, 
			Line megafacade ) {
		
		Matrix4d to2dXY = new Matrix4d();
		
		Vector3d up    = f.edge.uphill,
				 along = f.edge.direction(),
				 out   = f.edge.getPlaneNormal();
		
		along.normalize();
		
		to2dXY.setRow( 2, up.x, up.y, up.z, 0);
		to2dXY.setRow( 1, out.x, out.y, out.z, 0);
		to2dXY.setRow( 0, -along.x, -along.y, -along.z, 0);
		
		Point3d bottomS = f.definingSE.iterator().next().getStart( f ), bottomE = f.definingSE.iterator().next().getEnd( f );
		
		if (bottomS == null || bottomE== null)
			return;
		
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
		
		Loop<LPoint2d> flat = GreebleHelper.to2dLoop( GreebleHelper.transform (ll, to2dXY), 1 );

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
		
		MiniFacade toRecess = null;
		
		
		if (mf != null) {
			toRecess = new MiniFacade(mf);
			toRecess.featureGen = new FeatureGenerator( toRecess );
		}
		
		LinearForm3D facePlane = new LinearForm3D( new Vector3d( out.x, out.z, out.y ), new Point3d( bottomS.x, bottomS.z, bottomS.y ) );
		
		LoopL<Point2d> sides = null;
		DRectangle facadeRect = null;
		
		if ( wallTag != null ) {
			
			sides = GreebleHelper.findRectagle( flat, Pointz.to2XZ( start ), Pointz.to2XZ( end ) );

			if (mf != null) {
			if ( sides != null )
				facadeRect = GreebleHelper.findRect( sides.remove( 0 ) );

//			if (isBottom) {
//				mf.postState.outerFacadeRect = GreebleHelper.findRect(flat);
//				mf.featureGen.update(); // computes window positions
//			}
			}
		}

		// find window locations in 3 space
		
		List<DRectangle> floors = new ArrayList();
		List<MatMeshBuilder> materials = new ArrayList();
		
		if (wallTag != null && facadeRect != null && mf != null && 
			wallTag.isGroundFloor && mf.groundFloorHeight > 0 && wallTag.groundFloorColor != null &&
			facadeRect.x < mf.groundFloorHeight && facadeRect.getMaxX() > mf.groundFloorHeight) 
		{
			
			floors.addAll ( facadeRect.splitY( mf.groundFloorHeight ) );
			
			MatMeshBuilder gfm = greebleGrid.mbs.get( BRICK, wallTag.groundFloorColor ); 
			
			for ( Loop<Point2d> loop : sides ) {
				
				Loop<Point2d>[] cut = Loopz.cutConvex( loop, new LinearForm( 0, 1, mf.groundFloorHeight ) );
				faceMaterial.add( cut[ 1 ].singleton(), to3d );
				LoopL<Point2d> pts = cut[ 0 ].singleton();
				gfm.add( pts, GreebleHelper.wallUVs(pts, mf.postState.outerFacadeRect), to3d );
			}
			
			materials.add( gfm );
			materials.add( faceMaterial );
		} else {
			
			floors.add( facadeRect );
			materials.add( faceMaterial );
			if (sides != null)
				faceMaterial.add( sides, GreebleHelper.wallUVs(sides, mf.postState.outerFacadeRect), to3d );
		}

		for ( int j = 0; j < floors.size(); j++ ) {
			
			DRectangle floorRect = floors.get( j );
			MatMeshBuilder m = materials.get( j );
			
			Iterator<QuadF> quit = features.iterator();
			while ( quit.hasNext() ) {

				QuadF n = quit.next();

				if ( n.project( to2d, to3d, flat, facePlane, new Vector3d( along.y, 0, -along.x ) ) && 
						wallTag != null && floorRect != null && toRecess != null ) {

					// set the vertical bounds, so we can just render in 2d
					FRect bounds = new FRect( n.original );
					n.setBounds( to2d, bounds );

					if ( floorRect.contains( bounds ) ) 
					{
						toRecess.featureGen.put( n.original.f, bounds );
						quit.remove();
					}
				}
			}

			if ( wallTag == null || toRecess == null || floorRect == null ) {
				LoopL<LPoint2d> loop = flat.singleton();
				
				LoopL<Point2d> roofUVs;
				
				App ra = HasApp.get( roofApp );
				
				switch ( ra.appMode ) {
				default:
					roofUVs = null;
				case Net:
					if ( ra.textureUVs != TextureUVs.Rectangle )
						roofUVs = GreebleHelper.roofPitchUVs( loop, Pointz.to2XZ( start ), Pointz.to2XZ( end ), TILE_UV_SCALE );
					else 
						roofUVs = GreebleHelper.wholeRoofUVs( ll.singleton(), ra.textureRect );
				}
						
				m.add( loop, roofUVs, to3d );
				return;
			}

			if ( mf.app.appMode == AppMode.Color )
				
				greebleGrid.buildGrid (
					floorRect,
					to3d,
					toRecess,
					m,
					wallTag );
			else {
				
				DRectangle uvs;
				
				if (mf.app.textureUVs == TextureUVs.SQUARE) {
					uvs = new DRectangle(mf.postState.outerFacadeRect);
					uvs.y -= bottomS.z;
				} else 
					uvs = GreebleGrid.ZERO_ONE_UVS;
				
				greebleGrid.textureGrid (
					floorRect,
					uvs,
					to3d,
					toRecess );
			}
		}
	}
	
	public void edges( Output output, float[] roofColor ) {

		MatMeshBuilder mmb;
		
		App a = HasApp.get( roofApp );
		
		switch ( a.appMode ) {
			case Color: 
			default:
				mmb = greebleGrid.mbs.get( TILE, roofColor, roofApp );
				break;
			case Bitmap:
				mmb = greebleGrid.mbs.getTexture(TILE_TEXTURED, TILE_JPG, roofApp );
				break;
			case Net:
//				mmb = greebleGrid.mbs.getTexture( TILE_TEXTURED+"_" + a.texture, a.texture, roofApp );
				return;
//				break;
		}
		
		GreebleEdge.roowWallGreeble( output, 
				greebleGrid.mbs.get( TILE, roofColor, roofApp ),
				mmb, 
				greebleGrid.mbs.get( BRICK, new float[] { 1, 0, 0, 1 } ), TILE_UV_SCALE );

		for ( Face f : output.faces.values() )
			GreebleEdge.roofGreeble( f, greebleGrid.mbs.get( TILE, roofColor ) );
	}
}
