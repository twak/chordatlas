package org.twak.tweed.gen;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.twak.footprints.SatUtils;
import org.twak.tweed.GenHandlesSelect;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.tools.FacadeTool;
import org.twak.tweed.tools.SelectTool;
import org.twak.utils.Graph2D;
import org.twak.utils.Line;
import org.twak.utils.ListDownLayout;
import org.twak.utils.Loop;
import org.twak.utils.LoopL;
import org.twak.utils.Loopz;
import org.twak.utils.Pair;
import org.twak.utils.SuperLoop;
import org.twak.utils.UnionWalker;
import org.twak.utils.geom.Line3d;
import org.twak.viewTrace.Closer;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.GMLReader;

public class GMLGen extends LineGen3d {

	List<Line3d> lines = new ArrayList();

	Map<Integer,LoopL<Point3d>> blocks = new HashMap<>();
	
	FacadeFinder ff;
	
	Map<Integer, BlockGen> lastMesh = new HashMap<>();
	
	public GMLGen( String filename, Matrix4d toOrigin, Tweed tweed ) {

		super( "G2D " + new File( filename ).getName(), tweed );
		this.filename = filename;

		Closer<Point3d> closer = new Closer<>();
	
		LoopL<Point3d> polies = null;
		try {
			polies = GMLReader.readGML3d( new File( filename ), 
					DefaultGeocentricCRS.CARTESIAN,
					CRS.decode( TweedSettings.settings.gmlCoordSystem ) );
		} catch ( NoSuchAuthorityCodeException e ) {
			e.printStackTrace();
			return;
		} catch ( FactoryException e ) {
			e.printStackTrace();
			return;
		}
		
		if ( tweed.heights != null )
			for ( Loop<Point3d> poly : polies ) {
				if ( poly instanceof SuperLoop ) {
					SuperLoop sl = ( (SuperLoop) poly );
					sl.properties.putAll( tweed.heights.getProperties( (String) sl.properties.get( "name" ) ) );
				}
			}

		for ( Loop<Point3d> poly : polies) {

			List<Point3d> points = new ArrayList();
			
			for ( Pair<Point3d, Point3d> pair : poly.pairs() ) {

				toOrigin.transform( pair.first() );
				
				points.add( pair.first() );

				lines.add( new Line3d(pair.first(), pair.second()) );
			}
			
			if (TweedSettings.settings.flipFootprints)
				poly.reverse();
//				polies.reverseEachLoop();
			
			closer.add( points.toArray( new Point3d[points.size()]) );
		}
		
		Map<Point3d, Integer> bMap = closer.findMap();
		
		if (TweedSettings.settings.snapFootprintVert > 0) {
			Loopz.dirtySnap(polies, TweedSettings.settings.snapFootprintVert);
		}
		
		for ( Loop<Point3d> poly : polies) 
			if (poly.count() > 0) {
				int key = bMap.get ( poly.start.get() );
				LoopL<Point3d> loopl = blocks.get(key);
				if (loopl == null)
					blocks.put(key, loopl = new LoopL<>() );
				

				
				if (TweedSettings.settings.calculateFootprintNormals) {
						if (Loopz.area( Loopz.to2dLoop( poly, 1, null ) ) < 0)
							poly.reverse();
						
						
						
					}
				
				loopl.add(poly);
			}
	}

	@Override
	public Map<Loop<Point3d>, Integer> getFaces() {
		
		Map<Loop<Point3d>, Integer> out = new HashMap<>();
		
		for ( int i : blocks.keySet() )
			for ( Loop<Point3d> p : blocks.get( i ) )
				out.put( p, i );
		
		return out;
	}
	
	@Override
	public Iterable<Line3d> getLines() {
		return lines;
	}
	
	public enum Mode {
		RENDER_ALL_FACADES, RENDER_SELECTED_FACADE, RENDER_SAT
	}
	public static Mode mode = Mode.RENDER_SELECTED_FACADE;
	
	@Override
	protected void polyClicked( int callbackI ) {

		if (tweed.frame.selectedGen instanceof GenHandlesSelect) {
			
			((GenHandlesSelect)tweed.frame.selectedGen).blockSelected ( blocks.get( callbackI ), lastMesh.get( callbackI ) );
		}
		else if ( tweed.tool.getClass() == SelectTool.class )
			importMesh( callbackI );
		else if ( tweed.tool.getClass() == FacadeTool.class ) {

			if ( mode == Mode.RENDER_SELECTED_FACADE ) {
				( (FacadeTool) tweed.tool ).facadeSelected( blocks.get( callbackI ), lastMesh.get( callbackI ) );
				
			} else if ( mode == Mode.RENDER_ALL_FACADES ) {
				Set<LoopL<Point3d>> togo = new HashSet( blocks.values() );

				for ( int i = 0; i < 4; i++ )
					new Thread() {

						private synchronized LoopL<Point3d> getNext() {
							
							try {
								LoopL<Point3d> out = togo.iterator().next();

								togo.remove( out );

								System.out.println( "********************************* remaining:" + togo.size() );

								return out;
							} catch ( NoSuchElementException e ) {
								return new LoopL<>();
							}
						}

						public void run() {

							LoopL<Point3d> ll = null;
							while ( null != ( ll = getNext() ) )
								( (FacadeTool) tweed.tool ).facadeSelected( ll, null );
						};
					}.start();
					
			} else if ( mode == Mode.RENDER_SAT ) {
				SatUtils.render( tweed, blocks.get( callbackI ) );
			}
		}
			
	}
	
	@Override
	public JComponent getUI() {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		return out;
	}
	
	public static final double EXPAND_MESH = 5;
	private void importMesh( int index ) {
		
		LoopL<Point3d> polies = blocks.get( index );

		List<Vector2D> verts = polies.stream().flatMap( ll -> ll.streamAble() ).map( x -> {
			Line3d l = new Line3d( x.get(), x.getNext().get() );
			l.move( perp( l.dir(), EXPAND_MESH ) );
			return new Vector2D( l.start.x, l.start.z );
		} ).collect( Collectors.toList() );

		ConvexHull2D chull = new MonotoneChain( false, 0.0001 ).generate( verts );

		Loop<Point3d> hull = new Loop<Point3d>( ( Arrays.stream( chull.getLineSegments() ).map( x -> new Point3d( x.getStart().getX(), 0, x.getStart().getY() ) ).collect( Collectors.toList() ) ) );

		File root = new File( Tweed.JME + "Desktop/meshes/" );

		int i = 0;
		File l;

		while ( ( l = new File( root, "" + i ) ).exists() )
			i++;

		l.mkdirs();

		File objFile = new File( l, "cropped.obj" );
		tweed.miniGen.clip( hull, objFile );

		Graph2D g2 = new Graph2D();

		polies.stream().flatMap( ll -> ll.streamAble() ).forEach( 
				x -> g2.add( new Point2d( x.get().x, x.get().z ), new Point2d( x.getNext().get().x, x.getNext().get().z ) ) );

		g2.removeInnerEdges();

		//	new Plot (true, g2 );

		UnionWalker uw = new UnionWalker();
		for ( Point2d p : g2.map.keySet() )
			for ( Line line : g2.map.get( p ) )
				uw.addEdge(  line.end, line.start );
		//	new Plot (true, new ArrayList( uw.map.keySet()) );

		Loopz.writeXZObj( uw.findAll(), new File( l, "gis.obj" ), true );
		Loopz.writeXZObj( Loopz.to2dLoop(polies, 1, null), new File( l, "gis_footprints.obj" ), false );

		BlockGen bg = new BlockGen( l, tweed, polies ) ;
		
		lastMesh.put( index, bg );
		
		tweed.frame.addGen( bg, true );
		
	}

	public static Vector3d perp( Vector3d v, double scale ) {
		Vector3d out = new Vector3d( -v.z, 0, v.x );
		double l = out.length();
		if ( l < 0.001 )
			return new Vector3d();
		out.scale( scale / l );
		return out;
	}

}
