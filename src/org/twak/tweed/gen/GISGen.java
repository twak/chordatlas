package org.twak.tweed.gen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.GenHandlesSelect;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.tools.FacadeTool;
import org.twak.tweed.tools.SelectTool;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.Pair;
import org.twak.utils.Parallel;
import org.twak.utils.Parallel.Complete;
import org.twak.utils.Parallel.Work;
import org.twak.utils.Parallel.WorkFactory;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.SuperLoop;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.Graph2D;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.ObjRead;
import org.twak.utils.geom.UnionWalker;
import org.twak.utils.streams.InaxPoint2dCollector;
import org.twak.utils.streams.InaxPoint3dCollector;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.Closer;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.FacadeFinder.FacadeMode;
import org.twak.viewTrace.GMLReader;
import org.twak.viewTrace.facades.GreebleSkel;

import com.google.common.io.Files;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainPatch;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class GISGen  extends LineGen3d implements ICanSave {

	public static final String CROPPED_OBJ = "cropped.obj";

	transient List<Line3d> lines = new ArrayList();
	transient Map<Integer,LoopL<Point3d>> blocks = new HashMap<>();
	transient Map<Loop<Point3d>,LoopL<Point3d>> lot2block = new HashMap<>();
	
	transient List<Loop<Point3d>> lots = new ArrayList<>();
	transient Map<Integer, BlockGen> lastMesh = new HashMap<>();

	File objFile;
	String gmlFile;
//	@Deprecated transient Matrix4d toOrigin;
	String crs;
	
	public boolean showTerrain = false;
	
	public GISGen() {}
	
	public GISGen( File objFile, Tweed tweed ) {
		
		super( "gis(o) " + objFile.getName(), tweed );
		this.objFile = objFile ;
		initObj();
	}
	
	@Override
	public void calculate() {
		// TODO Auto-generated method stub
		super.calculate();

		if ( showTerrain ) {
			// https://wiki.jmonkeyengine.org/jme3/advanced/terrain.html
			DRectangle bounds = rect();
			int size = 1 + Mathz.nextPower2( (int) Math.max( bounds.width, bounds.height ) );
			Texture heightMapImage = tweed.getAssetManager().loadTexture( GreebleSkel.TILE_JPG );
			AbstractHeightMap heightmap = null;
			heightmap = new ImageBasedHeightMap( heightMapImage.getImage(), 1f );
			heightmap.load();

			TerrainQuad terrain = new TQ( "terrain", 65, size, heightmap.getHeightMap() );

			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
			ColorRGBA c = Jme3z.toJme( color );
			mat.setColor( "Diffuse", c );
			mat.setColor( "Ambient", c.mult( 0.1f ) );
			mat.setBoolean( "UseMaterialColors", true );

			//		mat.setBoolean( "UseMaterialColors", true );
			terrain.setMaterial( mat );
			Point2d gc = bounds.getCenter();
			terrain.setLocalTranslation( (float) gc.x, -10f, (float) gc.y );
			terrain.setLocalScale( 1f, 0.05f, 1f );

			TerrainLodControl control = new TerrainLodControl( terrain, Collections.singletonList( tweed.getCamera() ) );
			terrain.addControl( control );

			gNode.attachChild( terrain );
		}
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
			polies = GMLReader.readGML3d( Tweed.toWorkspace( new File( gmlFile ) ), 
					DefaultGeocentricCRS.CARTESIAN,
					CRS.decode( crs ) );
		} catch ( NoSuchAuthorityCodeException e ) {
			e.printStackTrace();
			return;
		} catch ( FactoryException e ) {
			e.printStackTrace();
			return;
		}
		
//		Optional<Gen> hg = tweed.frame.gens( LotInfoGen.class ).stream().findAny();
//		
//		if ( hg.isPresent() )
//			for ( Loop<Point3d> poly : polies ) {
//				if ( poly instanceof SuperLoop ) {
//					SuperLoop sl = ( (SuperLoop) poly );
//					sl.properties.putAll( ((LotInfoGen)hg.get()).getProperties( (String) sl.properties.get( "name" ) ) );
//				}
//			}

		for ( Loop<Point3d> poly : polies) {

			List<Point3d> points = new ArrayList();
			
			for ( Pair<Point3d, Point3d> pair : poly.pairs() ) {

				TweedSettings.settings.toOrigin.transform( pair.first() );
				
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
				
				lots.add( poly );
				lot2block.put( poly, loopl );
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
	
//	public enum Mode {
//		RENDER_ALL_BLOCKS, RENDER_SELECTED_BLOCK, RENDER_SAT, RANDOM_FACADE_SAMPLER;
//		
//		@Override
//		public String toString() {
//			return super.toString().toLowerCase().replaceAll( "_", " " );
//		}
//	}
//	public static Mode mode = Mode.RENDER_SELECTED_BLOCK;
	
	private transient boolean doneStreetWidth = false;
	
	@Override
	protected void polyClicked( int callbackI ) {

		if (tweed.frame.selectedGen instanceof GenHandlesSelect) {
			
			((GenHandlesSelect)tweed.frame.selectedGen).blockSelected ( blocks.get( callbackI ), lastMesh.get( callbackI ) );
		}
		else if ( tweed.tool.getClass() == SelectTool.class )
			importMesh( callbackI );
		else if ( tweed.tool.getClass() == FacadeTool.class ) {
//			if ( mode == Mode.RENDER_SAT ) {
//				SatUtils.render( tweed, blocks.get( callbackI ) );
//			} else {
				startRender( callbackI );
//			}
		}
			
	}

	public void startRender( int callbackI /* -1 for all */ ) {
		
		LotInfoGen li = tweed.frame.getGenOf( LotInfoGen.class );
		GISGen gis = tweed.frame.getGenOf( GISGen.class );

//		ensureStreetWidths( callbackI, li, gis );
		
		
		AtomicInteger count = new AtomicInteger( 0 );
		Random randy = new Random(System.nanoTime());
		
		WorkFactory<LoopL<Point3d>> b = findBlocks( callbackI, count, randy );

		File description = new File ( Tweed.DATA + File.separator + FeatureCache.SINGLE_RENDERED_FOLDER + File.separator +"params.txt" );
		
		try {
			description.getParentFile().mkdirs();
			BufferedWriter descBW = new BufferedWriter( new FileWriter( description ) );

			PanoGen feedback = null;
//			tweed.frame.addGen( new PanoGen( tweed ) {
//					protected void createPanoGens() {};
//			}, true);
			
			new Parallel<LoopL<Point3d>, Integer>( b, new Work<LoopL<Point3d>, Integer>() {
				public Integer work( LoopL<Point3d> in ) {
					
					double area = Loopz.area( Loopz.toXZLoop( in ) );
					
					if (area < 10)
						return 0;
					
					BlockGen.findWidths( in, gis );
					
					if (li != null)
					for ( Loop loop : in )
						li.fetchOSProperties( (SuperLoop<?>) loop );
					
					System.out.println( "rendering... (" + count + " images written)" );
					
					( (FacadeTool) tweed.tool ).renderFacade( in, count, descBW, feedback );
					
					try {
						descBW.flush();
					} catch ( IOException e ) {
						e.printStackTrace();
					}
					
					return 1;
				}
			}, new Complete<Integer>() {

				@Override
				public void complete( Set<Integer> dones ) {

					System.out.print( "finished rendering " + count + " images\nwriting description..." );

					try {
						descBW.close();
					} catch ( IOException e ) {
						e.printStackTrace();
					}

					System.out.print( "done" );

				}
			}, false, 16 );
			
		} catch ( IOException e1 ) {
			e1.printStackTrace();
		}
	}

	private WorkFactory<LoopL<Point3d>> findBlocks( int callbackI, AtomicInteger count, Random randy ) {
		WorkFactory<LoopL<Point3d>> b;
		
		int TOGET = Integer.MAX_VALUE;
		
		if ( callbackI >= 0 )
			b = new Parallel.ListWF<LoopL<Point3d>>( Collections.singletonList( blocks.get( callbackI ) ) );
		else {
			
			if (FacadeFinder.facadeMode == FacadeMode.KANGAROO )
				b = new WorkFactory<LoopL<Point3d>>() {
					@Override
					public LoopL<Point3d> generateWork() {
						int i = count.get();
						if (i > TOGET)
							return null;
						else
						{
							LoopL<Point3d> ll = lot2block.get( lots.get(randy.nextInt( lots.size() )) );
							return ll;
						}
					}

					@Override
					public boolean shouldAbort() {
						return false;
					}
				};
			else
				b = new Parallel.ListWF<LoopL<Point3d>>( new ArrayList( blocks.values() ) );
			
		}
		return b;
	}

	private void ensureStreetWidths( int callbackI, LotInfoGen li, GISGen gis ) {
		if ( li != null && gis != null && ( (FacadeTool) tweed.tool ).singleFolder ) {

			if ( callbackI >= 0 ) {

				BlockGen.findWidths( blocks.get( callbackI ), gis );
				for ( Loop loop : blocks.get( callbackI ) )
					li.fetchOSProperties( (SuperLoop<?>) loop );
			} else if ( !doneStreetWidth ) {

				int count = 0;
				
				for ( LoopL<Point3d> ll : blocks.values() ) {
					System.out.println( "pre-processing block " + (count++) +"/"+ blocks.size() );
					BlockGen.findWidths( ll, gis );

					for ( Loop loop : ll )
						li.fetchOSProperties( (SuperLoop) loop );

				}
				doneStreetWidth = true;
			}
		}
	}
	
	@Override
	public JComponent getUI() {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add(new AutoCheckbox( this, "showTerrain", "terrain" ) {
			public void updated(boolean selected) {
				calculateOnJmeThread();
			};
		} );
		
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

		double tol = 0.0001;
		ConvexHull2D chull = null;
		
		while ( tol < 10 ) {
			try {
				chull = new MonotoneChain( false, tol ).generate( verts );
				tol = 1000;
			} catch ( ConvergenceException e ) {
				tol *= 10;
			}
		}
		
		if (chull == null) {
			System.out.println( "unable to find hull" );
			return;
		}

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
			
			tweed.frame.setSelected( bg );
			
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

	public transient Quadtree quadtree = null;
	
	public synchronized void ensureQuad() {
		if (quadtree == null) 
		{
			System.out.print( "building quadtree..." );
			
			quadtree = new Quadtree();
			
			for (LoopL<Point3d> ll : blocks.values()) {

				if ( Loopz.area( Loopz.toXZLoop( ll ) ) < 10 )
					continue; // filter OS' kiosks
			
				for (Loop<Point3d> footprint : ll) {
					Envelope e = envelope( footprint );
					quadtree.insert( e, footprint  );
				}
			}
			
			System.out.println( "...done" );
		}
	}

	public static Envelope envelope( Loop<Point3d> footprint ) {
		double[] mm = footprint.stream().map( e -> Pointz.to2XZ( e )).collect( new InaxPoint2dCollector() );
		Envelope e = new Envelope( mm[0], mm[1], mm[2], mm[3] );
		return e;
	}

	public DRectangle rect() {
		
		double[] mm = lots.stream().flatMap( s -> s.stream() ).collect( new InaxPoint3dCollector() );
		
		return new DRectangle(mm[0], mm[4], mm[1]-mm[0], mm[5]-mm[4]);
	}
}
