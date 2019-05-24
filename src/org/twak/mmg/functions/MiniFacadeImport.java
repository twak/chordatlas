package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.mmg.Command;
import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.MO;
import org.twak.mmg.MOgram;
import org.twak.mmg.Node;
import org.twak.mmg.Refs;
import org.twak.mmg.media.GreebleMMG;
import org.twak.mmg.prim.Edge;
import org.twak.mmg.prim.ScreenSpace;
import org.twak.mmg.ui.Cowput;
import org.twak.utils.Cache;
import org.twak.utils.Pair;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class MiniFacadeImport extends Function {

	private static final String TOP_RECT = "⇧ ", BOTTOM_RECT = "⇩ ", SIDES_RECT = "⇦⇨ ";
	public MiniFacade mf;
	private Map<String, MO> fixedLabels;
	
	public MiniFacadeImport( MiniFacade mf, Map<String, MO> fixedLabels ) {
		color = fixed;
		
		this.mf = mf;
		this.fixedLabels = fixedLabels;
	}

	/**
	 * We have a set of MOs every facade uses that are identified via unique MOs
	 */
	public static Map<String, MO> createLabels() {

		Map<String, MO> out = new LinkedHashMap<>();

		for ( String s : new String[] { GreebleHelper.FLOOR_EDGE, GreebleHelper.WALL_EDGE, GreebleHelper.ROOF_EDGE } ) {
			FixedLabel fl = new FixedLabel( s );
			out.put( s, new MO( fl ) );
		}

		for ( Feature f : new Feature[] { Feature.DOOR, Feature.WINDOW } ) {
			for ( String s : new String[] { TOP_RECT, BOTTOM_RECT, SIDES_RECT } ) {
				String name = s + f.name().toLowerCase();
				FixedLabel fl = new FixedLabel( name );
				out.put( name, new MO( fl ) );
			}
		}

		return out;
	}
	
	public Node getLabel( String s, MMG mmg ) {
		
		try {
			return mmg.getNodes( fixedLabels.get( s ) ).get( 0 );
		}
		catch ( NullPointerException e ) {
			System.err.println( "not all node names declared in fixedlabels :(" );
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public List<Node> createSN( InputSet is, List<Object> vals, MMG mmg, Command mo, MOgram mogram ) {

		ScreenSpace.lastPosition = 0;

		List<Node> out = new ArrayList<>();
		Node root = new Node( this, null );
		out.add( root );



		for ( MiniFacade.Feature f : mf.featureGen.keySet() )
			createRectangles( mo, out, f.name().toLowerCase(), mf.featureGen.get( f ), root, mmg );

		createWall( mo, out, root, mmg );

		return out;
	}

	private void createWall( Command mo, List<Node> out, Node root, MMG mmg ) {


		for ( Loop<? extends Point2d> loop : mf.facadeTexApp.postState.wallFaces ) {
			
			Node pointRoot = new Node( this, null );
			out.add( pointRoot );
			Nodez.connect( root, "wall_faces", pointRoot, Refs.Structure.Unordered );
			
			Cache<Point2d, Node> nodeCache = new Cache<Point2d, Node>() {
				@Override public Node create( Point2d point2d ) {
					Node n = new Node( MiniFacadeImport.this, point2d);
					out.add(n);
					Nodez.connect( pointRoot, "points", n, Refs.Structure.Loop );
					return n;
				}
			};
			
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

				FixedLabel.label( getLabel( label, mmg ), en );
			}
		}

	}
	
	
	
	private void createRectangles( Command mo, List<Node> out, String name, List<FRect> rects,
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
		mf = GreebleMMG.createTemplateMF( Math.random() * 5 + 5, Math.random() * 5 + 3 );
		mogram.somethingChanged();
	}
	
	public void showOptions( JPanel panel, MOgram mogram ) {
	
		panel.setLayout( new ListDownLayout() );
		JButton rr = new JButton("randomize");
		panel.add( rr );
		rr.addActionListener( e -> randomMF(mogram) );
	}

	

}
