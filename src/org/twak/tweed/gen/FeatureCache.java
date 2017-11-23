package org.twak.tweed.gen;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.Line;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.PanMouseAdaptor;
import org.twak.viewTrace.facades.MiniFacade;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;

public class FeatureCache {

	public static final String PARAMETERS_YML = "parameters.yml";
	public static final String RENDERED_IMAGE = "orthographic";
	public static final String RENDERED_IMAGE_PNG = RENDERED_IMAGE + ".png";
	public static final String FEATURE_FOLDER = "features";
	
	public static class ImageFeatures implements ICanPaint {
		
		public File rectified, ortho;
		public List<Rectangle2D.Double> windows;
		public List<Double> classCuts;
		public double start, end;
		public List<MiniFacade> miniFacades = new ArrayList<>();
		public MegaFeatures mega;
		
		private transient BufferedImage
			loadedRectified = null,
			imageFile = null;

		public BufferedImage getRectified() {

			if (rectified == null)
				return null;
			
			if ( loadedRectified == null )
				try {
					loadedRectified = ImageIO.read( fixAbsPath(rectified) );
				} catch ( IOException e ) {
					e.printStackTrace();
					loadedRectified = new BufferedImage( 1, 1, BufferedImage.TYPE_3BYTE_BGR );
				}
			
			return loadedRectified;
		}

		public BufferedImage getOrtho() {
			
			if ( imageFile == null )
				try {
					imageFile = ImageIO.read( fixAbsPath( ortho ) );
				} catch ( IOException e ) {
					e.printStackTrace();
					imageFile = new BufferedImage( 1, 1, BufferedImage.TYPE_3BYTE_BGR );
				}
			return imageFile;
		}
		
		public static File fixAbsPath(File f) {
			
			if (f == null)
				return null;
			
			if (f.exists())
				return f;
			else
				return new File (Tweed.DATA, f.getPath() );
		}

		@Override
		public void paint( Graphics2D g, PanMouseAdaptor ma ) {

			{
				Composite old = g.getComposite();
				Composite comp = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, 0.5f );
				g.setComposite( comp );

				// draw rectified image at the width of the ortho image...
				
				double height = getRectified().getHeight() / 40.;

				g.drawImage( getRectified(), 
						ma.toX( Math.min( start, end ) ), 
						ma.toY( 0 - height ), 
						ma.toZoom( Math.abs(start - end) ), 
						ma.toZoom( height ), null );
				g.setComposite( old );
			}

//			for ( int i = 0; i < miniFacades.size(); i++ )
//				miniFacades.get( i ).paint( g, ma );

			//			for (double d : o.windowCuts)
			//				g.drawLine( ma.toX(d), 0, ma.toX(d), 10000 );
			//			
			g.setColor( Color.orange );
			//			
			//			for (double d : o.classCuts)
			//				g.drawLine( ma.toX(d), 0, ma.toX(d), 10000 );
			//		
			//			g.setColor( new Color(255, 100,100, 255) );
			//			if (o.windows != null)
			//			for (Rectangle2D.Double w : o.windows) {
			//				g.drawRect( ma.toX( w.x ), ma.toY (-w.y-w.height), ma.toZoom( w.width ), ma.toZoom( w.height ) );
			//			}
		}
	}
	
	public static class MegaFeatures {
		
		public List<ImageFeatures> features = new ArrayList();
		public Line megafacade;
		
		public MegaFeatures () {}
		
		public MegaFeatures (File folder) {
			
			File lineFile = new File (folder, FacadeTool.LINE_XML );
			File[] toProcess;
			
			
			if ( !lineFile.exists() )  { // a single image
				lineFile = new File ( folder.getParentFile(), FacadeTool.LINE_XML );
				toProcess = new File[] {folder};
			}
			else
				toProcess = folder.listFiles(); // an entire megfacade

			Arrays.sort( toProcess );
			
			if (lineFile.exists())
				megafacade = (Line) new XStream().fromXML( lineFile );
			else
				megafacade = new Line( 0, 0, 1, 0 );

			for ( File f : toProcess )
				if ( f.isDirectory() ) {
					try {
						ImageFeatures imf = readFeatures( f, this );
						if ( imf != null )
							features.add( imf );
					} catch ( Throwable th ) {
						System.out.println( "while reading features from " + f );
						th.printStackTrace();
					}
				}
		}
		
		public MegaFeatures( Line mega ) {
			this.megafacade = mega;
		}
	}
	
	public Map<Point2d,BlockFeatures> blockFeatures = new LinkedHashMap<>(); 
	
	public static class BlockFeatures  {
		
		Point2d pos;
		ArrayList<MegaFeatures> features = null;
		File blockDir;
		
		public BlockFeatures() {
			features = new ArrayList<>();
		}
		
		public BlockFeatures( File blockDir, Map<Point2d, BlockFeatures> blockFeatures ) {

			this.blockDir = blockDir;
			String name = blockDir.getName();
			
			try {
				pos = fileToCenter( name );
				blockFeatures.put(pos, this);
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}

		
		public List<MegaFeatures> getFeatures() {
			
			if (blockDir != null && features == null)
			{
				features = new ArrayList<>(); 
				for (File f : blockDir.listFiles() ) {

					if (f.isDirectory())
						try {
							features.add( new MegaFeatures( f ) );
						}
					catch (Throwable th) {
						th.printStackTrace();
					}
				}
			}
			
			
			return features;
		}
	}

	public static Point2d fileToCenter( String name ) {
		String[] ll = name.split( "_" );
		return new Point2d ( Double.parseDouble( ll[ 0 ] ), Double.parseDouble( ll[ 1 ] ) );
	}
	
	public BlockFeatures getBlock( Point2d center ) {
		
		Optional<Point2d> maybePt = blockFeatures.keySet().stream().min( 
				(a,b ) -> Double.compare( a.distance(center), b.distance(center) ) );
		
		if (maybePt.isPresent()) {
			if (center.distance( maybePt.get() ) < 1)
				return blockFeatures.get(maybePt.get());
			else {
				JOptionPane.showMessageDialog( tweed.frame.frame, "block features not found "+ center.x+"_" + center.y );
			}
		}
		
		return new BlockFeatures();
	}
	
	File folder;
	Tweed tweed;
	
	public FeatureCache( File folder, Tweed tweed ) {
		
		this.tweed = tweed;
		
		this.folder = folder;
		tweed.frame.removeGens( this.getClass() );
		tweed.features = this;
		
		if (!folder.exists()) {
			System.out.println("Feature not found " + folder);
			return;
		}
		
		init();
	}
	
	public void init() {
		File[] files = folder.listFiles();
		Arrays.sort( files );
		
		Arrays.stream( files ).filter( f -> f.isDirectory() ).forEach( f -> new BlockFeatures( f, blockFeatures ) );
	}
	
	public static ImageFeatures readFeatures( File fFolder, MegaFeatures megaFeatures ) {
		
		Line mega = new Line (megaFeatures.megafacade);
		
		ImageFeatures out = new ImageFeatures();
		out.mega = megaFeatures;

		Line imageL = null;
		
		out.miniFacades = new ArrayList();

		double ppm = 40;
		
		{			
			if (Tweed.DATA == null)
				out.ortho = new File( fFolder, RENDERED_IMAGE_PNG ) ;
			else
				out.ortho = Paths.get( Tweed.DATA ).relativize( new File( fFolder, RENDERED_IMAGE_PNG ).toPath() ).toFile();
			
			File rectFile = new File( fFolder, "rectified.png" );
			
			if ( rectFile.exists() ) {
				if ( Tweed.DATA == null )
					out.rectified = rectFile;
				else
					out.rectified = Paths.get( Tweed.DATA ).relativize( rectFile.toPath() ).toFile();
			}
			else
				out.rectified = out.ortho;
		}

		int imageWidth  = out.getRectified().getWidth(), 
			imageHeight = out.getRectified().getHeight();
		
		
		double rectifiedToOrtho = out.getRectified().getWidth() / (double) out.getOrtho().getWidth();
		
		
		{
			List<String> lines = null;
			try {
				lines = Files.readAllLines( new File( fFolder, "meta.txt" ).toPath() );
			} catch ( IOException e ) {
				System.err.println( "failed to read metafile" );
			}

			if ( lines == null ) {
				System.out.println( "warning, failed to read input files in " + fFolder );
				imageL = new Line (0,0, out.getRectified().getWidth() / ppm, 0 );
			} else {
				String plane = lines.get( 1 );
				String[] pVals = plane.split( " " );

				Point2d a = new Point2d( Float.parseFloat( pVals[ 0 ] ), Float.parseFloat( pVals[ 1 ] ) );
				Point2d b = new Point2d( Float.parseFloat( pVals[ 2 ] ), Float.parseFloat( pVals[ 3 ] ) );

				imageL = new Line( a, b );
			}
		}

		out.start = mega.findPPram( imageL.start ) * mega.length();
		out.end   = mega.findPPram( imageL.end   ) * mega.length();
		
//		out.classCuts  = toMega( readList( new File( fFolder,  "class_cuts.txt" ) ), imageWidth, imageL, mega );
//		out.windowCuts = toMega( readList( new File( fFolder, "window_cuts.txt" ) ), imageWidth, imageL, mega );
//		out.windows = readWindows( new File( fFolder, "window_boxes.txt" ), imageWidth, imageHeight, ppm, out.start );
		
		try {

			File yFile = new File( fFolder, PARAMETERS_YML );
			
			if ( yFile.exists() ) {

				YamlReader fromVision = new YamlReader( new FileReader( yFile ) );
				Map m = (Map) fromVision.read();

				List yamlFac = (List) m.get( "facades" );

				
				double maxW = 0;
				
				if ( yamlFac != null ) {
					for ( Object o : yamlFac )
						try {
							
							MiniFacade mf = new MiniFacade( 
									out, 
									(Map) o, 
									imageWidth  / (rectifiedToOrtho * ppm ), 
									imageHeight / ppm, 
									rectifiedToOrtho * ppm, 
									out.start ) ;
							
							if (!mf.invalid())
								out.miniFacades.add(mf );
							
							maxW = Math.max( maxW,  ( mf.left + mf.width - out.start ) );
							
						} catch (Throwable th ) {
							
							System.out.println("while reading " + yFile);
							th.printStackTrace();
						}
				}
			} else
				System.out.println( "no parameters in " + fFolder );


		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		
		
		return out;
	}
	
	public static List<java.awt.geom.Rectangle2D.Double> readWindows( File file, double imageWidth, 
			double imageHeight, double ppm, double imageXM ) {
		
		
		List<Rectangle2D.Double> out = new ArrayList();
		
		if (!file.exists())
			return out;
		
		try {
			for ( String w : Files.readAllLines( file.toPath() ) ) {

				String[] wp = w.trim().split( "[\\s]+" );
				
				double
				
					t = Double.parseDouble( wp[0] ),
					l = Double.parseDouble( wp[1] ),
					b = Double.parseDouble( wp[2] ),
					r = Double.parseDouble( wp[3] );
	
				l = l / ppm + imageXM;
				r = r / ppm + imageXM;
				
				t = ( imageHeight - t ) / ppm;
				b = ( imageHeight - b ) / ppm;
				
				out.add(new Rectangle2D.Double( Math.min (l,r), Math.min( t, b ), Math.abs(l-r), Math.abs(t-b) ));
				
			}
		} catch ( IOException e ) {
			System.out.println("error while reading windows " + file.getName() );
//			e.printStackTrace();
		}
		
		return out;
	}

	public static List<Double> toMega(List<Double> xVals, double imageWidth, Line image, Line mega) {
		return xVals.stream().map( x -> mega.findPPram( image.fromPPram ( x / imageWidth ) ) * mega.length() ).collect(Collectors.toList());
	}

	public static List<Double> readList( File file ) {
		
		if (!file.exists())
			return null;
		
		List<Double> out = new ArrayList();
		
		try {
			for (String s : Files.readAllLines( file.toPath() ) ) {
				
				String[] ds = s.split("\\s");
				
				out.addAll  ( Arrays.stream ( ds ).filter(x -> x.length() > 0).mapToDouble( x -> Double.parseDouble( x ) ).
						boxed().collect( Collectors.toList() ) );
				
			}
		} catch ( IOException e ) {
			System.out.println("error while reading file " + file.getName() );
//			e.printStackTrace();
		}

		return out;
	}

	static {
		
		PaintThing.lookup.put(Rectangle2D.class, new ICanPaintU() {
			
			@Override
			public void paint( Object o, Graphics2D g, PanMouseAdaptor ma ) {
				
					Rectangle2D r = (Rectangle2D) o;
					g.drawRect( ma.toX(r.getX()), ma.toY(-r.getY()-r.getHeight()), ma.toZoom( r.getWidth()), ma.toZoom( r.getHeight()) );
			}
		});
		
		PaintThing.lookup.put(ImageFeatures.class, new ICanPaintU() {
			@Override
			public void paint( Object o_, Graphics2D g, PanMouseAdaptor ma ) {
				

			}
		});
	}

	/**
	 * A MFPoint is part of the optimization process; we may/maynot select any given MFPoint.
	 * We have a minifacade (or not) on either side of us.
	 *
	 */
	public static class MFPoint extends Point2d {
		
		public MegaFeatures mega;
		public ImageFeatures image;
		public MiniFacade left, right;
		
		public HalfEdge selectedEdge; // after optimization, this storest the halfedge whose .end we map to (or null if none)
		public Set<MFPoint> sameCluster; // other points that might have better facade parameters
		public Set<ImageFeatures> covering;
		
		public MFPoint( Point2d pt, Set<ImageFeatures> covering, MegaFeatures mega, ImageFeatures image, MiniFacade left, MiniFacade right ) {
			super(pt);
			this.mega = mega;
			this.covering = covering;
			this.image = image;
			this.left = left;
			this.right = right;
		}
	}
	
	public static double MFWidthTol = TweedSettings.settings.miniWidthThreshold;
	public MultiMap<MegaFeatures, MFPoint> createMinis( BlockGen blockGen ) {

		MultiMap<MegaFeatures, MFPoint> out = new MultiMap();
		
		
		for (MegaFeatures m : getBlock (blockGen.center).getFeatures() ) {
			
			double mLen = m.megafacade.length();
			
			for ( ImageFeatures i : m.features ) { 
				
				for (int mi = 0; mi <= i.miniFacades.size(); mi++) {
				
					MiniFacade n = mi < i.miniFacades.size() ? i.miniFacades.get(mi) : null, 
							   p = (mi-1) >= 0 ? i.miniFacades.get(mi-1) : null;
					
		            if (n != null && ( n.width < MFWidthTol || (n.rects.countValue() == 0 && n.width < MFWidthTol* 3) ) ) // skinny mf filter
		            	n = null;
		            if (p != null && ( p.width < MFWidthTol || (p.rects.countValue() == 0 && p.width < MFWidthTol * 3) ) )
		            	p = null;
							   
				    if ( n== null && p == null || n == null && p.softRight || p == null && n.softLeft || 
				    		p != null && n != null && p.softRight && n.softLeft) 
				    	continue;

				    double ptDist = n == null ? ( p.left + p.width ) : n.left;
				    
					Point2d pt = m.megafacade.fromPPram( ptDist / mLen );
							
					double covTol = 2;
					
					Set<ImageFeatures> covering = m.features.stream()
							.filter( ii -> ii.start + covTol < ptDist && ii.end > ptDist + covTol )
							.collect( Collectors.toSet() );
					
					double pa = m.megafacade.findPPram( pt ) * mLen; // stuff beyond the end of the facade
					if ( pa < -5 || pa > mLen + 5)
						continue;
					
					out.put(m,new MFPoint (pt, covering, m, i, p, n) );
				}
			}
		}
		
		return out;
	}
}
