package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.twak.tweed.gen.skel.FCircle;
import org.twak.tweed.gen.skel.MiniRoof;
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
import org.twak.viewTrace.franken.App.TextureMode;
import org.twak.viewTrace.franken.App.TextureUVs;
import org.twak.viewTrace.franken.BuildingApp;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.RoofSuperApp;
import org.twak.viewTrace.franken.RoofTexApp;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class GreebleSkel {

	private static final double TILE_UV_SCALE = 0.4;
	private static final String TILE_TEXTURED = "tile_textured";
	private static final String TILE = "tile";
	private static final String BRICK = "brick";
	public static final String TILE_JPG = "tile.jpg";
	public static final String BRICK_JPG = "brick.jpg";
	
	public static final String Appearance = "appearance";
	Tweed tweed;
	Node node = new Node();
	
	OnClick onClick;
	
	GreebleGrid greebleGrid;
	
//	boolean isTextured = false;
//	DRectangle roofBounds;
	
	private MiniRoof miniroof;
	
	public static float[] 
			BLANK_ROOF = new float[] {0/255f, 170/255f, 255/255f, 1.0f },
			BLANK_WALL = new float[] {255/255f, 170/255f, 0/255f, 1.0f };
	
	SuperFace sf;
	
	public Map<SuperEdge, Face> occluderLookup;
	
	public GreebleSkel( Tweed tweed, SuperFace sf ) {
		this.tweed = tweed;
		this.sf = sf;
	}

	public Node showSkeleton( Output output, OnClick onClick, MiniRoof roof ) {
		
		this.onClick = onClick;
		this.miniroof = roof;
		createMesh( output );
		return node;
	}

	private void createMesh( Output output ) {
		
		float[] roofColor = BLANK_ROOF;
		
		if ( output.faces == null )
			return;
		
		
		roofColor = Colourz.toF4( miniroof.roofTexApp.color );
		Set<MiniFacade> allMFs = new HashSet<>();
		
		for ( Face f : output.faces.values() )  {
			WallTag wt = ((WallTag) GreebleHelper.getTag( f.profile, WallTag.class ));
			
			if (wt != null ) {
				if (wt.miniFacade != null)
					allMFs.add( wt.miniFacade );
			}
			
		}
		
		if (tweed != null)
		for (MiniFacade mf : allMFs) {
			mf.facadeTexApp.resetPostProcessState();
			mf.featureGen.valueList().stream().forEach( r -> r.panesLabelApp.renderedOnFacade = false );
		}
		
		
		List<List<Face>> chains = Campz.findChains( output );

		// give each minifacade a chance to update its features based on the skeleton result
		if (tweed != null)
		for (List<Face> chain : chains) {
			
			Set<WallTag> opt = chain.stream().flatMap( f -> f.profile.stream() )
					.filter( tag -> tag instanceof WallTag ).map( t -> (WallTag)t ).collect( Collectors.toSet() );
			
			for (WallTag wt : opt) {
				
				MiniFacade mf = wt.miniFacade;
				PostProcessState pps = mf.facadeTexApp.postState;
				
				Edge e = chain.get( 0 ).edge;
				Line megafacade = new Line ( e.end.x, e.end.y, e.start.x, e.start.y );
				double mfl = megafacade.length();
				Vector2d dir =megafacade.dir();
				LinearForm3D lf = new LinearForm3D( new Vector3d(-dir.y, dir.x, 0), e.start );
				
				for (Face f : chain) 
						for (Loop<Point2d> face : projectTo( megafacade, mfl, lf, f ) )
							if (GreebleHelper.getTag( f.profile, WallTag.class ) != null)
								pps.wallFaces.add( face );
							else
								pps.roofFaces.add( face );
				
				if ( occluderLookup != null )
					for ( Object o : wt.occlusions ) {
						Face f = occluderLookup.get( o );
						if ( f != null ) 
							pps.occluders.addAll( projectTo( megafacade, mfl, lf, f ) );
				}
				
				mf.width = chain.get( 0 ).edge.length();
			}
		}
		
		for ( MiniFacade mf : allMFs ) {
			PostProcessState pps = mf.facadeTexApp.postState;
			pps.outerWallRect = GreebleHelper.findRect( pps.wallFaces );
			mf.featureGen.update( );
		}
		
		if (tweed != null) // just calculating dormer locations
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
				
				if (false && TweedSettings.settings.snapFacadeWidth) {
					
					mf2 = new MiniFacade ( wt.miniFacade );
					
					mf2.facadeTexApp   = wt.miniFacade.facadeTexApp;
					mf2.facadeLabelApp = wt.miniFacade.facadeLabelApp;
					mf2.facadeSuperApp = wt.miniFacade.facadeSuperApp;
					
					// move/scale mf horizontally from mean-image-location to mesh-facade-location
					double[] meshSE = findSE ( wt.miniFacade, megafacade, chain );
					mf2.scaleX( meshSE[0], meshSE[1] );
				}
				
				mf2.featureGen.values().stream()
					.flatMap ( k -> k.stream() )
					.map     ( r -> new QuadF (r, megafacade) )
					.forEach ( q -> processedFeatures.add(q) );
			}

			for ( QuadF q1 : processedFeatures ) 
				q1.original.panesLabelApp.coveringRoof = new Loop<>();
			
			if ( tweed != null ) {
				Set<QuadF> allFeatures = new LinkedHashSet<>();
				allFeatures.addAll( processedFeatures );
			
				for ( Face f : chain )
					face( f, mf2, processedFeatures, megafacade );

				allFeatures.removeAll( processedFeatures );
				for ( QuadF q1 : allFeatures ) {
					
					if (q1.original.getFeat() == Feature.WINDOW || q1.original.getFeat() == Feature.SHOP) {
						q1.original.panesLabelApp.renderedOnFacade = true;
					}
				}
			}
			
			if ( ( wt != null && sf.buildingApp.createDormers && !wt.miniFacade.facadeLabelApp.disableDormers ) ||  
					TweedSettings.settings.createDormers ) {
				
				Iterator<QuadF> quit = processedFeatures.iterator();
				while ( quit.hasNext() ) {
					QuadF w = quit.next();
					if ( ( w.original.getFeat() == Feature.WINDOW || w.original.getFeat() == Feature.SHOP ) && w.foundAll() ) {
						
						w.original.panesLabelApp.renderedOnFacade = true;
						
						w.original.panesLabelApp.coveringRoof = new Loop<Point2d>();
						
						if (greebleGrid != null)
							greebleGrid.createDormerWindow( miniroof, w, greebleGrid.mbs.WOOD, greebleGrid.mbs.GLASS, 
								(float)  w.original.attachedHeight.get(Feature.SILL).depth, (float) w.original.attachedHeight.get(Feature.SILL).d,
								(float) w.original.attachedHeight.get(Feature.CORNICE).d,
								0.6, 0.9 );
						
						quit.remove();
					}
				}
			}
			
			
			if ( greebleGrid != null ) {
				edges( output, roofColor );

				// output per-material objects
				greebleGrid.attachAll( node, chain, output, new ClickMe() {
					@Override
					public void clicked( Object data ) {

						try {
							SwingUtilities.invokeAndWait( new Runnable() {

								@Override
								public void run() {
									selected( output, node, findSuperEdge( output, chain ), data instanceof Spatial ? ( ( (Object[]) ( (Spatial) data ).getUserData( Appearance ) )[ 0 ] ) : null );
								}
							} );
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
				} );
			}
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
		void selected( Output output, Node node, SuperEdge superEdge, Object ha );
	}

	private void selected( Output output, Node out, SuperEdge superEdge, Object ha ) {
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
				
				FacadeTexApp mfa = mf.facadeTexApp;
				
				if (mfa.texture == null)
					faceColor = greebleGrid.mbs.get( BRICK+mfa.hashCode(), mfa.color, mf );
				
				else switch ( mfa.appMode ) {
					case Off:
						faceColor = greebleGrid.mbs.get( BRICK + mfa.hashCode(), mfa.color, mf );
						break;
					case Bitmap:
						faceColor = greebleGrid.mbs.getTexture( "brick_" + mfa.hashCode(), BRICK_JPG, mf );
						break;
					case Net:
						faceColor = greebleGrid.mbs.getTexture( "wall_" + mfa.texture + mfa.hashCode(), mfa.texture, mf );
						break;
					}

			} else if ( t instanceof RoofTag ) {
				
				RoofTag rt = (RoofTag)t;
				RoofTexApp ra = miniroof.roofTexApp;
				
				switch ( ra.appMode ) {

				case Off:
					faceColor = greebleGrid.mbs.get( TILE, ra.color, miniroof );
					break;
				case Bitmap:
					faceColor = greebleGrid.mbs.getTexture( TILE_TEXTURED, TILE_JPG, miniroof );
					break;
				case Net:
				case Parent:
					if ( ra.texture == null )
						faceColor = greebleGrid.mbs.get( TILE, ra.color, miniroof );
					
					else {
						String texture = ra.getTexture (rt);
						faceColor = greebleGrid.mbs.getTexture( "roof_" + texture, texture, miniroof );
					}
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
		
		FacadeTexApp fta = null;
		if (mf != null)
			fta = mf.facadeTexApp;
		
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
		
		FeatureGenerator toRecess = new FeatureGenerator( (MiniFacade) null );
		
		
//		if (mf != null) {
//			toRecess = new FeatureGenerator();
//			toRecess.mf = mf;
//			toRecess.add new FeatureGenerator( toRecess );
//		}
		
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
		
		DRectangle uvs = GreebleGrid.ZERO_ONE_UVS;
		
		if ( mf != null ) {
			if ( fta.textureUVs == TextureUVs.Square ) {
				
				uvs = new DRectangle( fta.postState.outerWallRect );

				{ // for faces not at the bottom, move to the bottom's uv space
					Face f2 = f;
					while ( f2.parent != null )
						f2 = f2.parent;
					Point3d bottomS2 = new Point3d( f2.definingSE.iterator().next().getStart( f2 ) );
					to2dXY.transform( bottomS2 );
					uvs.x += bottomS2.x;
					uvs.y -= bottomS.z;
				}

			} else if ( fta.textureUVs == TextureUVs.Rectangle ) {
				uvs = fta.textureRect;
			}
			
			if ( fta.texture == null )
				uvs = null;
		}
			

		// find window locations in 3 space
		
		List<DRectangle> floors = new ArrayList();
		List<MatMeshBuilder> materials = new ArrayList();
		
		boolean isGroundFloor = wallTag != null && 
				facadeRect != null && 
				mf != null && 
				f.definingCorners.iterator().next().z < 1;
		
		if (wallTag != null && facadeRect != null && mf != null && 
			isGroundFloor && mf.groundFloorHeight > 0 &&
			facadeRect.x < mf.groundFloorHeight && facadeRect.getMaxX() > mf.groundFloorHeight) 
		{
			
			floors.addAll ( facadeRect.splitY( mf.groundFloorHeight ) );
			
			MatMeshBuilder gfm = greebleGrid.mbs.get( BRICK,Colourz.toF4( mf.facadeTexApp.groundFloorColor ) ); 
			
			for ( Loop<Point2d> loop : sides ) {
				
				Loop<Point2d>[] cut = Loopz.cutConvex( loop, new LinearForm( 0, 1, mf.groundFloorHeight ) );
				faceMaterial.add( cut[ 1 ].singleton(), to3d );
				LoopL<Point2d> pts = cut[ 0 ].singleton();
				gfm.add( pts, GreebleHelper.wallUVs(pts, fta.postState.outerWallRect), to3d );
			}
			
			materials.add( gfm );
			materials.add( faceMaterial );
		} else {
			
				floors.add( facadeRect );
				materials.add( faceMaterial );
				if ( sides != null && !sides.isEmpty() )
					faceMaterial.add( sides, uvs == null ? null : GreebleHelper.wallUVs( sides, uvs ), to3d );
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
					FRect bounds = new FRect( n.original, n.original.panesLabelApp, n.original.panesTexApp, null );
					
					n.setBounds( to2d, bounds );

					if ( floorRect.contains( bounds ) ) {
						toRecess.put( n.original.getFeat(), bounds );
						quit.remove();
					}
				}
			}
			
			if ( floorRect == null || wallTag == null || toRecess == null || mf == null ) {
				greebleRoof( f, ll, faceMaterial, start, end, flat, to3d, to2d );
			} else if ( fta.texture == null )
				
				greebleGrid.buildGrid (
					floorRect,
					to3d,
					toRecess,
					mf,
					m,
					wallTag );
			else {
				
				greebleGrid.textureGrid (
					floorRect,
					uvs,
					to3d,
					toRecess,
					mf );
			}
		}
	}

	private void greebleRoof( Face f, Loop<LPoint3d> ll, MatMeshBuilder faceMaterial, 
			Point3d start, Point3d end, Loop<LPoint2d> flat, Matrix4d to3d, Matrix4d to2d ) {
		LoopL<LPoint2d> loop = flat.singleton();
		
		LoopL<Point2d> roofUVs;
		
		RoofTexApp ra = miniroof.roofTexApp;
		RoofSuperApp rsa = miniroof.roofSuperApp;
		
		switch ( ra.appMode ) {
			default:
				roofUVs = null;
				break;
			case Net:
				if ( ra.texture != null && ra.textureUVs == TextureUVs.Square )
					roofUVs = GreebleHelper.roofPitchUVs( loop, Pointz.to2XZ( start ), Pointz.to2XZ( end ), TILE_UV_SCALE );
				else if ( rsa.appMode == TextureMode.Net && rsa.textures != null && ra.textureUVs == TextureUVs.Zero_One ) {
					roofUVs = GreebleHelper.zeroOneRoofUVs( loop, Pointz.to2XZ( end ), Pointz.to2XZ( start ) );
				}
				else
					roofUVs = GreebleHelper.wholeRoofUVs( ll.singleton(), ra.textureRect == null ? new DRectangle(0,0,1,1 ) : ra.textureRect );
				break;
			case Bitmap:
				roofUVs = GreebleHelper.roofPitchUVs( loop, Pointz.to2XZ( start ), Pointz.to2XZ( end ), TILE_UV_SCALE );
				break;
		}
		
		faceMaterial.add( loop, roofUVs, to3d );
		
		for (FCircle feature : miniroof.getGreebles(f) ) {
			
			Point3d onRoof = f.edge.linearForm.collide( new Point3d(feature.loc.x, feature.loc.y, 0 ), Mathz.Z_UP );
			onRoof.set(onRoof.x, onRoof.z, onRoof.y);
			to2d.transform( onRoof );
			
			DRectangle r = feature.toRect();
			
			r.x = onRoof.x - r.width / 2;
			r.y = onRoof.z - r.height / 2;
			
			switch ( feature.f ) {

				case Chimney:
					greebleGrid.createChimney ( onRoof, miniroof, feature, f.edge.projectDown().dir() , f.edge.linearForm, 
						sf.buildingApp.chimneyTexture );
					break;
				
				case Velux:
					if ( feature.veluxTextureApp.texture == null)
						greebleGrid.createWindow( r, to3d, null, greebleGrid.mbs.WOOD, greebleGrid.mbs.GLASS, 0.09, -1, -1, -1, 2, 2 );
					else {
						
						double zOff = -0.08;
						
						GreebleGrid.createWindowFromPanes (new ArrayList<DRectangle>(), r, r, to3d,
							greebleGrid.mbs.getTexture( "velux_"+feature.veluxTextureApp.texture+"_"+r.hashCode(),
									feature.veluxTextureApp.texture, miniroof ),
							zOff + 0.05, zOff, true );
					}
					break;
			}
		}
	}
	
	public void edges( Output output, float[] roofColor ) {

		MatMeshBuilder mmb;
		
		RoofTexApp a = miniroof.roofTexApp;
		
		switch ( a.appMode ) {
			case Off: 
			default:
				mmb = greebleGrid.mbs.get( TILE, roofColor, miniroof );
				break;
			case Bitmap:
				mmb = greebleGrid.mbs.getTexture(TILE_TEXTURED, TILE_JPG, miniroof );
				break;
			case Net:
				return;
		}
		
		GreebleEdge.roowWallGreeble( output, 
				greebleGrid.mbs.get( TILE, roofColor, miniroof ),
				mmb, 
				greebleGrid.mbs.get( BRICK, new float[] { 1, 0, 0, 1 } ), TILE_UV_SCALE );

		for ( Face f : output.faces.values() )
			GreebleEdge.roofGreeble( f, greebleGrid.mbs.get( TILE, roofColor ) );
	}
}
