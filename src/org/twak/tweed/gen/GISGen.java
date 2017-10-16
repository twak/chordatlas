package org.twak.tweed.gen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
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
import org.twak.utils.Line;
import org.twak.utils.Pair;
import org.twak.utils.Parallel;
import org.twak.utils.Parallel.Work;
import org.twak.utils.Parallel.Complete;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.SuperLoop;
import org.twak.utils.geom.Graph2D;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.ObjRead;
import org.twak.utils.geom.UnionWalker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.Closer;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.GMLReader;

import com.google.common.io.Files;

public class GISGen  extends LineGen3d implements ICanSave {

	public static final String CROPPED_OBJ = "cropped.obj";

	transient List<Line3d> lines = new ArrayList();
	transient Map<Integer,LoopL<Point3d>> blocks = new HashMap<>();
	transient Map<Integer, BlockGen> lastMesh = new HashMap<>();

	File objFile;
	String gmlFile;
	@Deprecated transient Matrix4d toOrigin;
	String crs;
	
	public GISGen() {}
	
	public GISGen( File objFile, Tweed tweed ) {
		
		super( "gis(o) " + objFile.getName(), tweed );
		this.objFile = objFile ;
		initObj();
	}
	
	public GISGen( String gmlFile, Matrix4d toOrigin, String crs, Tweed tweed ) {

		super( "gis(g) " + new File( gmlFile ).getName(), tweed );
		this.filename = gmlFile;
		this.gmlFile = gmlFile;
		this.crs = crs;

		initGML();
	}
	
	@Override
	
	public void onLoad( Tweed tweed ) {
		super.onLoad( tweed );
		if (objFile != null) // fixme: subclass pls
			initObj();
		else if (gmlFile != null)
			initGML();
	}
	
	public void initObj() {
		
		ObjRead gObj = new ObjRead( tweed.toWorkspace( objFile ) );
		
		LoopL<Point3d> fromOBJ = new LoopL<>();
		Closer<Point3d> closer = new Closer<>();
		
		for (int[] face : gObj.faces) {
			
			Loop<Point3d> loop = fromOBJ.newLoop();
			
			List<Point3d> points = new ArrayList<>();
			
			for (int i = 0; i < face.length; i++) {
				
				Point3d p = new Point3d ( gObj.pts[face[i]] ), 
						n = new Point3d ( gObj.pts[ face[ ( i + 1 ) % face.length ] ] );
				
				n.y = p.y = 0;//!
				loop.append( p );
				points.add( p );
				
				lines.add( new Line3d( p, n ) );
			}
			closer.add( points.toArray( new Point3d[points.size()]) );
		}
		
		createBlocks( closer, fromOBJ );
	}

	public void initGML() {
		Closer<Point3d> closer = new Closer<>();
	
		LoopL<Point3d> polies = null;
		try {
			polies = GMLReader.readGML3d( tweed.toWorkspace( new File( gmlFile ) ), 
					DefaultGeocentricCRS.CARTESIAN,
					CRS.decode( crs ) );
		} catch ( NoSuchAuthorityCodeException e ) {
			e.printStackTrace();
			return;
		} catch ( FactoryException e ) {
			e.printStackTrace();
			return;
		}
		
		Optional<Gen> hg = tweed.frame.gens( HeightGen.class ).stream().findAny();
		
		if ( hg.isPresent() )
			for ( Loop<Point3d> poly : polies ) {
				if ( poly instanceof SuperLoop ) {
					SuperLoop sl = ( (SuperLoop) poly );
					sl.properties.putAll( ((HeightGen)hg.get()).getProperties( (String) sl.properties.get( "name" ) ) );
				}
			}

		for ( Loop<Point3d> poly : polies) {

			List<Point3d> points = new ArrayList();
			
			for ( Pair<Point3d, Point3d> pair : poly.pairs() ) {

				TweedSettings.settings.toOrigin.transform( pair.first() );
				
				System.out.println( " >>> " + pair.first() );
				
				pair.first().y = 0;
				points.add( pair.first() );

				lines.add( new Line3d(pair.first(), pair.second()) );
			}
			
			if (TweedSettings.settings.flipFootprints)
				poly.reverse();
			
			closer.add( points.toArray( new Point3d[points.size()]) );
		}
		
		createBlocks( closer, polies );
	}

	private void createBlocks( Closer<Point3d> closer, LoopL<Point3d> polies ) {
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
		RENDER_ALL_FACADES, RENDER_SELECTED_FACADE, RENDER_SAT;
		
		@Override
		public String toString() {
			return super.toString().toLowerCase().replaceAll( "_", " " );
		}
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
				
				FacadeFinder.count = 0;
				
				new Parallel<LoopL<Point3d>, Integer>( new ArrayList(blocks.values()), new Work<LoopL<Point3d>, Integer>() {
					public Integer work(LoopL<Point3d> out) {
						System.out.println("rendering... ("+ FacadeFinder.count + " images written)");
						( (FacadeTool) tweed.tool ).facadeSelected( out, null );
						return 1;
					}
				}, new Complete<Integer>() {

					@Override
					public void complete( Set<Integer> dones ) {
						System.out.println("finished rendering "+ FacadeFinder.count + " images");
					}
				}, false );

//				Set<LoopL<Point3d>> togo = new HashSet( blocks.values() );
//
//				for ( int i = 0; i < 4; i++ )
//					new Thread() {
//
//						private synchronized LoopL<Point3d> getNext() {
//							
//							try {
//								LoopL<Point3d> out = togo.iterator().next();
//
//								togo.remove( out );
//
//								System.out.println( "********************************* remaining:" + togo.size() );
//
//								return out;
//							} catch ( NoSuchElementException e ) {
//								return new LoopL<>();
//							}
//						}
//
//						public void run() {
//
//							LoopL<Point3d> ll = null;
//							while ( null != ( ll = getNext() ) )
//								( (FacadeTool) tweed.tool ).facadeSelected( ll, null );
//						};
//					}.start();
					
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

		File root = new File( Tweed.SCRATCH + "meshes" + File.separator );

		int i = 0;
		File l;

		while ( ( l = new File( root, "" + i ) ).exists() )
			i++;

		l.mkdirs();

		File croppedFile = new File( l, CROPPED_OBJ );
		
		boolean found = false;
		
		for ( Gen gen : tweed.frame.gens( MiniGen.class ) ) { // minigen == optimised obj
			
			( (MiniGen) gen ).clip( hull, croppedFile );

			
			found = true;
		}
		
		if (!found) 
			
			for ( Gen gen : tweed.frame.gens( MeshGen.class ) ) { // obj == just import whole obj
			
				ObjGen objg = (ObjGen) gen;
				
				try {
					Files.asByteSource( objg.getFile() ).copyTo (Files.asByteSink( croppedFile ));
					objg.setVisible( false );
					found = true;
				} catch ( IOException e ) {
					e.printStackTrace();
				}
			}
		
		if ( found ) {
			Graph2D g2 = new Graph2D();

			polies.stream().flatMap( ll -> ll.streamAble() ).forEach( x -> g2.add( new Point2d( x.get().x, x.get().z ), new Point2d( x.getNext().get().x, x.getNext().get().z ) ) );

			g2.removeInnerEdges();

			//	new Plot (true, g2 );

			UnionWalker uw = new UnionWalker();
			for ( Point2d p : g2.map.keySet() )
				for ( Line line : g2.map.get( p ) )
					uw.addEdge( line.end, line.start );
			//	new Plot (true, new ArrayList( uw.map.keySet()) );

			Loopz.writeXZObj( uw.findAll(), new File( l, "gis.obj" ), true );
			Loopz.writeXZObj( Loopz.to2dLoop( polies, 1, null ), new File( l, "gis_footprints.obj" ), false );

			BlockGen bg = new BlockGen( l, tweed, polies );

			lastMesh.put( index, bg );

			tweed.frame.addGen( bg, true );
		} else
			JOptionPane.showMessageDialog( tweed.frame(), "Failed to find mesh from minimesh or gml layers" );
		
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
