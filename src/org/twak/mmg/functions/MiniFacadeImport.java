package org.twak.mmg.functions;

import org.twak.mmg.*;
import org.twak.mmg.prim.Edge;
import org.twak.mmg.prim.Label;
import org.twak.mmg.prim.ScreenSpace;
import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.Pair;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.MiniFacade;

import javax.vecmath.Point2d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MiniFacadeImport extends Function {

	public MiniFacade mf;

	public MiniFacadeImport( MiniFacade mf ) {
		color = fixed;
		this.mf = mf;
	}

//
//	private static Cache<String, Lable>
//	"⇧ " + name.toLowerCase() ),
//	bot = labelCache.get( "⇩ " + name.toLowerCase() ),
//	sid = labelCache.get( "⇦⇨ "
//
//	static String doorTop = "door"
//	static Set<String> labelNames = new HashSet<>();
//	static{
//
//		for (String s : new String[] {"win", "door"})
//		{
//			labelNames.add("⇧ "+s );
//			labelNames.add("⇦ ⇨ "+s );
//			labelNames.add("⇩ "+s );
//		}
//
//	}

	@Override
	public List<Node> createSN( InputSet is, List<Object> vals, MMG mmg, Command mo, MOgram mogram ) {

		ScreenSpace.lastPosition = 0;

		List<Node> out = new ArrayList<>();
		Node root = new Node( this, null );
		out.add( root );

		Cache<String, Node> labelCache = new Cach<String, Node>(s -> {
			Label l = new Label (s);
			Node n = new Node (this, l);
			out.add(n);
//			Nodez.connect( root, "labels", n, Refs.Structure.Sequential );
			return n;
		} );


		for ( MiniFacade.Feature f : mf.featureGen.keySet() )
			createRectangles( mo, out, f.name().toLowerCase(), mf.featureGen.get( f ), labelCache, root );

		createWall( mo, out, labelCache, root );

		return out;
	}

	private void createWall( Command mo, List<Node> out, Cache<String, Node> labelCache, Node root ) {

		Node pointRoot = new Node( this, null );
		Nodez.connect( root, "points", pointRoot, Refs.Structure.Singleton );

		Cache<Point2d, Node> nodeCache = new Cache<Point2d, Node>() {
			@Override public Node create( Point2d point2d ) {
				Node n = new Node( MiniFacadeImport.this, point2d);
				out.add(n);
				Nodez.connect( pointRoot, "points", n, Refs.Structure.Loop );
				return n;
			}
		};

		for ( Loop<? extends Point2d> loop : mf.facadeTexApp.postState.wallFaces )
			for ( Loopable<? extends Point2d> lp : loop.loopableIterator() ) {

				Edge e = new Edge( lp.get(), lp.getNext().get() );
				Node en = new Node( this, e );
				out.add(en);

				TwoPointEdge.connectEdge( mo, nodeCache.get( lp.get() ), nodeCache.get( lp.getNext().get() ), en );

				String label;

				if ( e.start.y == 0 && e.end.y == 0 )
					label = GreebleHelper.FLOOR_EDGE;
				else if ( Math.abs( e.start.x - e.end.x ) < 0.1 )
					label = GreebleHelper.WALL_EDGE;
				else
					label = GreebleHelper.ROOF_EDGE;

				FixedLabel.label( labelCache.get( label ), en );
			}

	}
	private void createRectangles( Command mo, List<Node> out, String name, List<FRect> rects,
			Cache<String, Node> labelCache, Node root ) {

		Node 	top = labelCache.get( "⇧ " + name.toLowerCase() ),
				bot = labelCache.get( "⇩ " + name.toLowerCase() ),
				sid = labelCache.get( "⇦⇨ " + name.toLowerCase() );


		for ( Node n : new Node[] { top, bot, sid } ) {
			out.add( n );
			Nodez.connect( root, "labels", n, Refs.Structure.Sequential );
		}

		for ( DRectangle r : rects ) {

			Node rroot = new Node( this, null );
			out.add( rroot );
			Nodez.connect( root, name, rroot, Refs.Structure.Loop );

			List<Node> corners = new ArrayList<>();

			for ( Point2d p : r.points() ) {

				Node c = new Node( this, p );
				corners.add( c );
				out.add( c );

				Nodez.connect( rroot, name, c, Refs.Structure.Loop );
			}

			Loop<Node> edgeNodes = new Loop<>();

			int i = 0;
			for ( Pair<Node, Node> line : new ConsecutivePairs<>( corners, true ) ) {

				Edge e = new Edge( (Point2d) line.first().result, (Point2d) line.second().result );
				Node en = new Node( this, e );

				TwoPointEdge.connectEdge( mo, line.first(), line.second(), en );

				edgeNodes.append( en );
				out.add( en );

				switch ( i++ ) {
				case 1:
					FixedLabel.label( bot, en );
					break;
				case 3:
					FixedLabel.label( top, en );
					break;
				default:
					FixedLabel.label( sid, en );
				}
			}

			//			out.add ( EdgesToFace.createFace( this,  edgeNodes.singleton(),  mo ) );

		}
	}

}
