package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;

import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.Streamz;
import org.twak.utils.collections.SuperLoop;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjRead;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.FacadeFinder.FacadeMode;
import org.twak.viewTrace.Slice;
import org.twak.viewTrace.SliceParameters;
import org.twak.viewTrace.SliceSolver;
import org.twak.viewTrace.facades.AlignStandalone2d;

import com.jme3.asset.ModelKey;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class BlockGen extends ObjGen {

	File root;
	String selectedSelectedName = "";
	public LoopL<Point3d> polies;
	public ProfileGen profileGen;
	
	private static SliceParameters P = new SliceParameters(10); // when set by Slice UI, used for all future blocks!

	public Point2d center;
	
	public BlockGen( File l, Tweed tweed, LoopL<Point3d> polies ) {
		
		super ( new File(l, GISGen.CROPPED_OBJ ).getPath().substring( Tweed.JME.length() ), tweed);

		this.polies = polies;
		this.root = l;
		this.name = "block";
		this.transparency = 0;
		
		this.center = Loopz.average( Loopz.to2dLoop( polies, 1, null ) );
		System.out.println("creating block with name: " + nameCoords() );
	}
	
	@Override
	public void calculate() {
		
		super.calculate();
		doClicked(gNode);
	}

	private void doClicked( Spatial s ) {
		s.setUserData( ClickMe.class.getSimpleName(), new Object[] { new ClickMe() {
			@Override
			public void clicked( Object data ) {
				tweed.frame.setSelected( BlockGen.this );
			}
		} } );
	}

	private void show (String file) {
		String full = new File (root, file).getPath();
		String neuFilename = full.substring( Tweed.JME.length() );
		
		if (!neuFilename.equals( filename )) {
			filename = neuFilename;
			tweed.getAssetManager().deleteFromCache( new ModelKey( filename ) );
			calculateOnJmeThread();
		}
	}
	
	@Override
	public JComponent getUI() {
		
		JPanel panel = (JPanel) super.getUI();

		JButton profiles = new JButton ("find profiles");
		profiles.addActionListener( e -> doProfile() );
		
		JButton panos = new JButton ("render panoramas");
		panos.addActionListener( e -> renderPanos() );
		
		JButton features = new JButton ("find image features");
		features.addActionListener( e -> segnetFacade() );
		
		JButton viewFeatures = new JButton ("features viewer");
		viewFeatures.addActionListener( e -> viewFeatures() );
		
		JButton slice = new JButton ("slice");
		slice.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				new Thread() {
					public void run() {

						File fs = getSlicedFile();

						if ( !fs.exists() ) 
						{
							new SliceSolver( fs, 
									new Slice( 
											getCroppedFile(), 
											getGISFile(), P, false ), P );
						}
						
						tweed.frame.addGen( new ObjGen( 
								tweed.makeWorkspaceRelative( fs ).toString(),
								tweed ), true); 
					}

				}.start();
			}
		} );
		
		JButton tooD = new JButton( "slice UI" );
		tooD.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				new Slice( root, ProfileGen.SLICE_SCALE );
			}
		} );
		
		JButton loadSln = new JButton( "load last solution" );
		loadSln.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				File f = getSolutionFile();
				if ( f.exists() ) {

					SolverState SS = (SolverState) new XStream().fromXML( f );
					SkelFootprint.postProcesss( SS );
					
//					PaintThing.debug.clear();
//					new Plot(SS.mesh).add( new ICanPaint() {
//
//						@Override
//						public void paint( Graphics2D g, PanMouseAdaptor ma ) {
//
//							g.setColor( Color.black );
//							g.setStroke( new BasicStroke( 2 ) );
//							
//							if (SS.footprint != null)
//							for (Line l : SS.footprint) {
//								g.drawLine( 
//										ma.toX( l.start.x ), 
//										ma.toY( l.start.y ), 
//										ma.toX( l.end.x ), 
//										ma.toY( l.end.y ) ); 
//							}
//							
//						}
//					});
					
					tweed.frame.addGen( new SkelGen( SS.mesh, tweed, BlockGen.this ), true );
				} else {
					JOptionPane.showMessageDialog( tweed.frame(), "Unable to find pre-computed solution.\n" + f );
				}
			}
		} );
		
		StringBuilder sb = new StringBuilder();
		sb.append( "name:" +nameCoords()+"\nlot info:\n" );
		Optional<Gen> hg = tweed.frame.gens( LotInfoGen.class ).stream().findAny();
		
		if ( hg.isPresent() )
			for ( Loop<Point3d> loop : polies )
				try {
					SuperLoop<Point3d> sl = (SuperLoop) loop;
					for ( Map.Entry<String, Object> e : sl.properties.entrySet() )
						sb.append( " >" + e.getKey() + " : " + e.getValue() + "\n" );
				}
				catch (Throwable th) {th.printStackTrace(  ); }
		

		
		JButton b = new JButton("street widths");
		b.addActionListener( e -> findWidths(polies, tweed.frame.getGenOf( GISGen.class )) );
		
		JTextArea name = new JTextArea( sb.toString() );
		name.setEditable( false );
		JScrollPane nameScroller = new JScrollPane( name );
		nameScroller.setPreferredSize( new Dimension( 100, 150 ) );
		
		panel.add( b );
		panel.add(profiles, 0 );
		panel.add(panos, 1 );
		panel.add(features, 2 );
		panel.add(new JLabel("other:"), 4 );
		panel.add( slice );
		panel.add( viewFeatures );
//		panel.add( tooD );
		if (getSolutionFile().exists())
			panel.add( loadSln );
		panel.add(new JLabel("metadata:") );
		panel.add( nameScroller );
		
		
		return panel;
	}

	public final static String STREET_WIDTH = "streetwidth";
	
	public static void findWidths(LoopL<Point3d> polies, GISGen gisGen ) {

		LoopL<Point2d> polies2d = toXZLoopSameProperties ( polies );

		Map<Point2d, Point2d> onBoundary = new HashMap<>(); 
		
		{
			LoopL<Point2d> boundary = Loopz.removeInnerEdges( polies2d );
			boundary.stream().filter( x -> Loopz.area( x ) > 10).
				flatMap( x -> x.streamAble() ).
				forEach( p -> onBoundary.put( p.get(), p.getNext().get() ) );
		}

//		PaintThing.debug.clear();
		
		gisGen.ensureQuad();
		
		for ( Loop<Point2d> footprint : polies2d ) {
			
			Map<Line, Double> widths = new HashMap<>();
			((SuperLoop)footprint).properties.put( STREET_WIDTH, widths );
			
			for ( Loopable<Point2d> ll : footprint.loopableIterator() ) {
				Point2d a = onBoundary.get( ll.get() );

				if ( a != null && a.equals( ll.getNext().get() ) ) {

					Line l = new Line( ll.get(), ll.getNext().get() );

//					PaintThing.debug( Color.black, 2f, l );
				
					double sw = findStreetWidth ( polies, l, gisGen.quadtree, 30, gisGen );
					
					widths.put( l, sw );
					
//					if (sw < 1e3) {
//						
//						Vector2d dir = l.dir();
//						dir.set( new double[] { -dir.y, dir.x } );
//						dir.normalize();
//						
//						Point2d mid = l.fromPPram( 0.5 );
//						dir.scale( sw );
//						dir.add( mid );
//						PaintThing.debug( Color.black, 1f, new Line( mid, new Point2d( dir ) ) );
//					}
				}

			}
		}
		
//		new Plot ( polies2d );
	}

	private static LoopL<Point2d> toXZLoopSameProperties(LoopL<Point3d> list) {
		
		LoopL<Point2d> out = new LoopL<>();
		
		for (Loop<Point3d> ll : list)
			out.add( toXZLoopSameProperties( ll) );
		
		return out;
	}
	
	private static Loop<Point2d>  toXZLoopSameProperties( Loop<Point3d> ll) {
		
		Loop<Point2d> o;
		
		if (ll instanceof SuperLoop ) {
			o = new SuperLoop( (String) ( (SuperLoop)ll).properties.get( "name" ) );
			((SuperLoop)o).properties = ( (SuperLoop) ll ).properties;
		}
		else {
			o = new Loop<>();
		}
		
		for (Point3d p : ll) 
			o.append(new Point2d(p.x, p.z));
		
		for (Loop<Point3d> hole : ll.holes)
			o.holes.add(toXZLoopSameProperties(hole));
		
		return o;
	}
	
	private static final int MIN_SW = 15;
	private synchronized static double findStreetWidth( LoopL<Point3d> ignore, Line l, Quadtree quadtree, double max, GISGen gisGen ) {
		
		if (l.length() < MIN_SW) {
			Point2d cen = l.fromPPram( 0.5 );
			Vector2d up = l.dir();
			up.scale( MIN_SW  / (2*up.length() ) );
			l = new Line(new Point2d ( cen ), new Point2d ( cen ) );
			l.end.add( up );
			l.start.sub( up);
		}
		
		Vector2d dir = l.dir();
		dir.set( new double[] { -dir.y, dir.x } );
		dir.scale( max / l.length() );
		
		DRectangle dr =  new DRectangle(l.start );
		dr.envelop( l.end );
		
		Point2d a = new Point2d(l.start), b = new Point2d( l.end );
		a.add( dir ); b.add( dir );
		dr.envelop( a ); dr.envelop( b );
		
		double dist = Double.MAX_VALUE;

		
		Loop<Point2d> queryBounds = new Loop<>(l.end, l.start, a, b);
		Envelope queryEnvelope = new Envelope( dr.x, dr.getMaxX(), dr.y, dr.getMaxY()  );
		
		for (Object o : quadtree.query( queryEnvelope ) ) {
			
			Loop<Point3d> block = (Loop)o;
			
//			why is block sometime null?
			if ( block == null || ignore.contains( block ) || ! queryEnvelope.intersects( GISGen.envelope( block ) ) )
				continue;
			
			for (Loopable<Point3d> pt : block.loopableIterator()) {
				Line query = new Line ( Pointz.to2( pt.get() ) , Pointz.to2( pt.getNext().get()));
				
				if ( !Loopz.inside( query, queryBounds ) )
					continue;
				
				dist = Math.min( dist, l.distance( query ) );
			}
		}
		
		return dist;
	}

	private void viewFeatures() {
		AlignStandalone2d.show( getInputFolder( FeatureCache.FEATURE_FOLDER ).toString() );
	}

	private void segnetFacade() {
		
		File r = getInputFolder( FeatureCache.FEATURE_FOLDER );
		
		if (!r.exists()) {
			JOptionPane.showMessageDialog( tweed.frame(), "no facade images found - have they been rendered?" );
			return;
		}
			
		
		File toProcess = new File (r, "files.txt");
		
		if (toProcess.exists())
			toProcess.delete();
		
		boolean[] seenResult = new boolean[] {false};
		
		StringBuffer sb = new StringBuffer();
		
		FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
				File f = file.toFile();
				File result = new File ( f.getParentFile(), FeatureCache.PARAMETERS_YML );
				
				if (f.getName().equals( FeatureCache.RENDERED_IMAGE_PNG ) && !result.exists() ) {
					
					Path fileR = r.toPath().relativize( file ),
							resultR = r.toPath().relativize( result.toPath() );
					
					
					seenResult[0] |= resultR.toFile().exists();
					
					sb.append ( "/output/" + fileR +"\t"+
							"/output/" +resultR +"\n" );
				}
				
				return FileVisitResult.CONTINUE;
			}
		};

		
		try {
			Files.walkFileTree( r.toPath(), fv );
			FileWriter fw = new FileWriter( toProcess );
			fw.append( sb );
			fw.close();
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
		if (sb.length() == 0) {
			JOptionPane.showMessageDialog( tweed.frame(), "all features already computed. nothing to do here!" );
			return;
		}
		
		System.out.println( "running CNN to find features..." );
		
		ProcessBuilder pb = new ProcessBuilder( 
				"nvidia-docker", "run", "-v", r+":/output",
				"twak/segnet-facade", "bash", "-c", "source inference /output/files.txt" );
		
		Process p;
		
		try {
			p = pb.start();
			Streamz.inheritIO(p.getInputStream(), System.out);
			Streamz.inheritIO(p.getErrorStream(), System.err);
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
	}

	private void renderPanos() {
		
		if (getInputFolder( FeatureCache.FEATURE_FOLDER ).exists()) {
			int result = JOptionPane.showConfirmDialog(tweed.frame(), "feature folder already exists. really re-render?",
			        "alert", JOptionPane.OK_CANCEL_OPTION);
			if (result == JOptionPane.CANCEL_OPTION)
				return;
		}
		
		FacadeFinder.facadeMode = FacadeMode.PER_CAMERA;
		
		try {
			new FacadeTool(tweed).renderFacade( polies, null, new BufferedWriter(new FileWriter( Tweed.SCRATCH +"/params.txt" )), null );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	public String nameCoords() {
		return center.x+"_"+center.y;
	}
	
	private void doProfile() {
		new Thread() {
			@Override
			public void run() {
				profileGen = new ProfileGen(BlockGen.this, Loopz.toXZLoop( polies ), tweed);
			}
		}.start();
	}
	
	public File getGISFile() {
		return new File( root, "gis.obj" );
	}

	public File getCroppedFile() {
		return new File( root, "cropped.obj" );
	}
	
	public File getSlicedFile() {
		return new File( root, "sliced.obj" );
	};
	
	private static abstract class Selected {
		
		String name;
		
		public Selected(String name) {
			this.name = name;
		}
		
		public abstract void onSelect();
		
		@Override
		public String toString() {
			return name;
		}
	}

	ObjRead croppedMesh = null;
	public ObjRead getCroppedMesh() {
		
		if (croppedMesh == null)
			croppedMesh = new ObjRead( getCroppedFile() );

		return croppedMesh;
	}
	
	double[] croppedExtent = null;
	public double[] getCroppedExtent() {
		if (croppedExtent == null)
			croppedExtent = getCroppedMesh().findExtent();
		
		return croppedExtent;
	}
	
	@Override
	public void dumpObj( ObjDump dump ) {
		dump.setCurrentMaterial( Color.blue, 0.5);
		dump.addAll (getCroppedMesh());
	}

	public File getInputFolder( String dir ) {
		return new File (Tweed.DATA, dir+File.separator+nameCoords() );
	}
	
	public File getSolutionFile() {
		return new File (getInputFolder(ResultsGen.SOLUTIONS),ResultsGen.SOLVER_FILE);
	}
}
