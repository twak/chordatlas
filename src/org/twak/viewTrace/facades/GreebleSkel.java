package org.twak.viewTrace.facades;

import java.util.ArrayList;
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
import javax.vecmath.Vector3d;

import org.twak.camp.Edge;
import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Tag;
import org.twak.camp.ui.Bar;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.PlanSkeleton.ColumnProperties;
import org.twak.siteplan.jme.MeshBuilder;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.SETag;
import org.twak.tweed.gen.SuperEdge;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.LinearForm;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;
import org.twak.viewTrace.facades.GreebleHelper.LPoint3d;
import org.twak.viewTrace.facades.MiniFacade.Feature;

import com.jme3.scene.Node;

public class GreebleSkel {

	Tweed tweed;
	Node node = new Node();
	
	OnClick onClick;
	
	GreebleGrid greebleGrid;
	
	MMeshBuilderCache mbs;
	
	public GreebleSkel( Tweed tweed ) {
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
		
		greebleGrid = new GreebleGrid(tweed, mbs = new MMeshBuilderCache());
		
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
				
				if (TweedSettings.settings.snapFacadeWidth) {
					// move/scale mf horizontally from mean-image-location to mesh-facade-location
					double[] meshSE = findSE ( wt.miniFacade, facadeLine, chain );
					mf2.scaleX( meshSE[0], meshSE[1] );
				}
				
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
					greebleGrid.createDormerWindow( w, mbs.WOOD, mbs.GLASS, 
							(float) wt.sillDepth, (float) wt.sillHeight, (float) wt.corniceHeight, 0.6, 0.9 );
				}
			
//			for ( String mName : mbs.cache.keySet() )
//				for (float[] mCol : mbs.cache.get( mName ).keySet() )		
//					node.attachChild( mb2Geom( output, chain, mName, mCol ) );
			
			greebleGrid.attachAll(node, chain, output, new ClickMe() {
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
			});
		}
	}

	

	public interface OnClick {
		void selected( Output output, Node node, SuperEdge superEdge );
	}

	private void selected( Output output, Node out, SuperEdge superEdge ) {
		if (onClick != null)
			onClick.selected (output, out, superEdge );
	}
	
	private SuperEdge findSuperEdge( Output output, List<Face> chain ) {
		
		ColumnProperties col = ((PlanSkeleton)output.skeleton).getDefiningColumn( chain.get( 0 ).edge );
		if (col == null)
			return null;
		Bar bar = col.defBar;
		SETag set = ((SETag) GreebleSkel.getTag( bar.tags, SETag.class ));
		
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
		
	private void face (Face f, MiniFacade mf, Set<QuadF> features, float[] roofColor, float[] wallColor ) {

		MatMeshBuilder faceColor = mbs.ERROR;
		
		WallTag wallTag = null;

		for ( Tag t : f.profile ) {

			if ( t instanceof WallTag ) {
				
				wallTag = ( (WallTag) t );
				faceColor = greebleGrid.mbs.get( "brick", wallTag.color != null ? wallTag.color : wallColor );

			} else if ( t instanceof RoofTag ) {
				
				RoofTag rt = (RoofTag)t;
				faceColor = greebleGrid.mbs.get("tile", rt.color != null ? rt.color : roofColor );
			}
		}

		if ( f.edge.getPlaneNormal().angle( new Vector3d( 0, 0,1 ) ) < 0.1 )
			wallTag = null;
		
		for ( Loop<Point3d> ll : f.getLoopL() ) {
			for ( Loopable<Point3d> lll : ll.loopableIterator() )
				if ( lll.get().distance( lll.getNext().get() ) > 200 )
					return;
		}


		for ( Loop<LPoint3d> ll : GreebleHelper.findPerimeter( f ) ) {
				
			if (wallTag != null) 
				wallTag.isGroundFloor = f.definingCorners.iterator().next().z < 1;
				
			mapTo2d( f, ll, mf, wallTag, features, faceColor );
		}
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
				envelop.add( Pointz.to2( tmp ) );
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
			
			MatMeshBuilder gfm = greebleGrid.mbs.get( "brick", wallTag.groundFloorColor ); 
			
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

			if ( wallTag == null || forFace == null || floorRect == null ) {
				m.add( flat.singleton(), to3d );
				return;
			}

			List<DRectangle> occlusions = new ArrayList<>();
			for ( LineHeight lh : wallTag.occlusions ) {

				Point3d s = new Point3d( lh.start.x, lh.start.y, lh.min ), e = new Point3d( lh.end.x, lh.end.y, lh.max );

				to2dXY.transform( s );
				to2dXY.transform( e );

				occlusions.add( new DRectangle( Math.min( s.x, e.x ), s.z, Math.abs( e.x - s.x ), Math.abs( e.z - s.z ) ) );
			}

			if ( mf.texture == null )
				greebleGrid.buildGrid (
					floorRect,
					to3d,
					forFace,
					m,
					wallTag );
			else
				greebleGrid.textureGrid (
					floorRect,
					to3d,
					mf );
		
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
	
	
	
	protected boolean visible( DRectangle dRectangle, List<DRectangle> occlusions ) {
		
		for (Point2d p : dRectangle.points()) 
			for (DRectangle d : occlusions)
				if (d.contains( p ))
					return false;
		
		return true;
	}
	
	protected LoopL<Point2d> findRectagle( Loop<LPoint2d> flat, Point2d s, Point2d e ) {

//		new Plot(flat);
		
		List<Point2d> left = new ArrayList<>(), right = new ArrayList<>();
		
		for (Loopable<LPoint2d> pt : flat.loopableIterator()) {
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

	public void edges( Output output, float[] roofColor ) {

		GreebleEdge.roowWallGreeble( output, mbs.get( "tile", roofColor ), mbs.get( "brick", new float[] { 1, 0, 0, 1 } ) );

		for ( Face f : output.faces.values() )
			GreebleEdge.roofGreeble( f, mbs.get( "tile", roofColor ) );
	}
}
