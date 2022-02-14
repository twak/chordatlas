package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.mmg.*;
import org.twak.mmg.functions.ui.TwoPointEdge;
import org.twak.mmg.media.GreebleMMG;
import org.twak.mmg.prim.*;
import org.twak.mmg.ui.Cowput;
import org.twak.utils.Cache;
import org.twak.utils.Pair;
import org.twak.utils.collections.*;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.PostProcessState;

public class MiniFacadeImport extends Function implements Fixed, MultiNodeFn {


	private static final String TOP_RECT = "⇧ ", BOTTOM_RECT = "⇩ ", SIDES_RECT = "⇦⇨ ";
	private Map<String, MO> fixedLabels;

	// input comes from mmg if it contains the magic key
	public static final String MINI_FACADE_KEY = "mini-facade";
	public MiniFacade mf2;
	
	public MiniFacadeImport( MiniFacade mf, Map<String, MO> fixedLabels ) {
		color = fixed;
		this.mf2 = mf;
		this.fixedLabels = fixedLabels;
	}

	/**
	 * We have a set of MOs every facade uses that are identified via unique MOs
	 */
	public static Map<String, MO> createLabels() {

		Map<String, MO> out = new LinkedHashMap<>();

		int i = 0;
		for ( String s : new String[] { GreebleHelper.FLOOR_EDGE, GreebleHelper.WALL_EDGE, GreebleHelper.ROOF_EDGE } ) {
			FixedLabel fl = new FixedLabel( s );
			fl.label.location.y += Label.HEIGHT * 1.3 * i++ + Label.HEIGHT * 0.3;
			fl.label.location.x = Label.HEIGHT * 0.3;
			out.put( s, new MO( fl ) );
		}

//		for ( Feature f : new Feature[] { Feature.DOOR, Feature.WINDOW } ) {
//			for ( String s : new String[] { TOP_RECT, BOTTOM_RECT, SIDES_RECT } ) {
//				String name = s + f.name().toLowerCase();
//				FixedLabel fl = new FixedLabel( name );
//				fl.label.location.y += Label.HEIGHT * 1.3 * i++ + Label.HEIGHT * 0.3;
//				fl.label.location.x = Label.HEIGHT * 0.3;
//				out.put( name, new MO( fl ) );
//			}
//		}

		return out;
	}
	
	public Node getLabel( String s, MMG mmg ) {
		
		try {
			return mmg.getNodes( fixedLabels.get( s ) ).get( 0 );
		} catch ( NullPointerException e ) {
			System.err.println( "not all node names declared in fixedlabels :(" );
			e.printStackTrace();
		}
		
		return null;
	}


	@Override
	public List<Node> createSN( InputSet is, List<Object> vals, MMG mmg, MO mo, MOgram mogram ) {

		List<Node> out = new ArrayList<>();
		Node root = new Node( this, new IndexNodeResult() );
		out.add( root );

		MiniFacade mf = mf2;

		for ( MiniFacade.Feature f : mf.featureGen.keySet() )
			createRectangles( mo, out, f.name().toLowerCase(), mf.featureGen.get( f ), root, mmg );

		createWall( mo, out, root, mmg );

		return out;
	}

	public static Point2d toMMGSpace(Point2d pt) {
		return new Point2d(pt.x, -pt.y);
	}

	public static Point2d fromMMGSpace(Point2d pt) {
		return new Point2d(pt.x, -pt.y);
	}

	private void createWall( MO mo, List<Node> out, Node root, MMG mmg ) {


		for ( Loop<? extends Point2d> loop : mf2.facadeTexApp.postState.wallFaces ) {
			
			Node pointRoot = new Node( this, null );
			out.add( pointRoot );
			Nodez.connect( root, "wall_faces", pointRoot, Refs.Structure.Loop );
			
			Cache<Point2d, Node> nodeCache = new Cache<Point2d, Node>() {
				@Override public Node create( Point2d point2d ) {
					Node n = new Node( MiniFacadeImport.this, point2d);
					out.add(n);
					Nodez.connect( pointRoot, "points", n, Refs.Structure.Loop );
					return n;
				}
			};

			LoopL<Node> edgesLL = new LoopL<>();
			Loop<Node>edgesL = edgesLL.loop();

			Loopable<? extends Point2d> start = findNearestOrigin(loop), current = start;

			do {

				Point2d a = toMMGSpace(current.get()), b = toMMGSpace(current.getNext().get());

				Edge e = new Edge( a, b );
				Node en = new Node( this, e );
				edgesL.append(en);
				out.add(en);

				TwoPointEdge.connectEdge( mo, nodeCache.get( a ), nodeCache.get( b ), en );

				String label;

				if ( e.start.y == 0 && e.end.y == 0 )
					label = GreebleHelper.FLOOR_EDGE;
				else if ( Math.abs( e.start.x - e.end.x ) < 0.1 )
					label = GreebleHelper.WALL_EDGE;
				else
					label = GreebleHelper.ROOF_EDGE;

				FixedLabel.label( getLabel( label, mmg ), en );
				current = current.next;

			} while (current != start);

			Node face = EdgesToFace.createFace(this, edgesLL, mo);
			out.add(face);
		}

		PointEdgeBuilder.sortGeometryNodes(out);
	}

	private Loopable<? extends Point2d> findNearestOrigin(Loop<? extends Point2d> loop) {
		final Point2d zero = new Point2d();
		return loop.streamAble().min( (a, b) -> Double.compare(a.get().distance(zero), b.get().distance(zero))).get();

	}


	private void createRectangles( MO mo, List<Node> out, String name, List<FRect> rects,
			Node root, MMG mmg ) {

		Node 	top = getLabel( TOP_RECT    + name.toLowerCase(), mmg ),
				bot = getLabel( BOTTOM_RECT + name.toLowerCase(), mmg ),
				sid = getLabel( SIDES_RECT  + name.toLowerCase(), mmg );

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
	
	private void randomMF(MOgram mogram) {
		mf2 = GreebleMMG.createTemplateMF( Math.random() * 5 + 5, Math.random() * 5 + 3 );
		mogram.somethingChanged();
	}
	
	public void showOptions( JPanel panel, MOgram mogram ) {
	
		panel.setLayout( new ListDownLayout() );
		JButton rr = new JButton("randomize");
		panel.add( rr );
		rr.addActionListener( e -> randomMF(mogram) );
	}


	@Override
	public Class<?> outputType() {
		return Face.class;
	}

	@Override
	public Object save() {

		List<Loop<? extends  Point2d>> out = new ArrayList<>();

		for (Loop<? extends Point2d> lp : mf2.facadeTexApp.postState.wallFaces ) {

			Loop<Point2d> copy = new Loop<>();
			for (Point2d p : lp)
				copy.append(new Point2d(p));

			out.add(copy);
		}

		return out;
	}

	@Override
	public void load(Object o) {
		this.mf2 = new MiniFacade();
		this.mf2.facadeTexApp.postState = new PostProcessState();
		this.mf2.facadeTexApp.postState.wallFaces = (List<Loop<? extends Point2d>>) o;
	}
}
