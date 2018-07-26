package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.utils.Imagez;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class PanesLabelApp extends App {

//	PanesTexApp child = new PanesTexApp( this );
	
	public String label;
	public double frameScale = 0.1;
	
	public boolean regularize = true;
	public List<DRectangle> panes = null;
	public final static double frameWidth = 0.07 /*cm */;

	public Loop<Point2d> coveringRoof;
	public boolean renderedOnFacade = true;
	
	FRect fr;
	public String texture;

	public TextureUVs textureUVs = TextureUVs.Square;
	public DRectangle textureRect;
	
	public double scale = 1;
	
	public PanesLabelApp(FRect fr ) {
		
		super();
		
		this.fr = fr;
		
		if (TweedSettings.settings.siteplanInteractiveTextures)
			appMode = AppMode.Net;
		
		getUp( ).install(this);
	}
	
	public PanesLabelApp(PanesLabelApp t) {
		super (t);
		this.label = t.label;
		
		this.fr = t.fr;
		
		this.frameScale = t.frameScale;
		this.regularize = t.regularize;
		this.panes = t.panes;
		this.coveringRoof = t.coveringRoof;
		
		this.texture = t.texture;
		this.textureUVs = t.textureUVs;
		if (t.textureRect != null)
			this.textureRect = new DRectangle(t.textureRect);
		
		if (TweedSettings.settings.siteplanInteractiveTextures)
			appMode = AppMode.Net;
	}
	
	@Override
	public App getUp() {
		return fr.mf.facadeTexApp;
	}

	@Override
	public MultiMap<String, App> getDown() {
		MultiMap<String, App>  out = new MultiMap<>();
		
		out.put( "texture", fr.panesTexApp );
		
		return out;
	}

	@Override
	public App copy() {
			return new PanesLabelApp( this );
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {

		JPanel out = new JPanel(new ListDownLayout());

		if ( appMode == AppMode.Net ) {

			out.add( new AutoDoubleSlider( this, "scale", "scale", 0.01, 3 ) {
				public void updated( double value ) {
					for ( App a : apps )
						( (PanesLabelApp) a ).scale = scale;
					globalUpdate.run();
				};
			}.notWhileDragging() );

			out.add( new AutoCheckbox( this, "regularize", "regularize" ) {
				@Override
				public void updated( boolean selected ) {

					for ( App a : apps )
						( (PanesLabelApp) a ).regularize = selected;

					globalUpdate.run();
				}
			} );
		}
		return out;
	}

	public final static int pad = 20;
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		NetInfo ni = NetInfo.get(this); 
		Pix2Pix p2 = new Pix2Pix( ni );

		BufferedImage bi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();
		
		Map<Object, File> doors = new HashMap<>();
		
		for ( App a_ : batch ) {
			try {
				
				PanesLabelApp a = (PanesLabelApp)a_;
				
				if (a_.appMode != AppMode.Net) {
					
					a.texture = null;
					a.textureRect = null;
					a.textureUVs = TextureUVs.Square;
					a.panes = null;
					
					continue;
				}
				
				
				a.panes = null;
				
				FRect r = a.fr;
				
//				if ( !Pix2Pix.findBounds( a.fr.mf, true ).contains( r.getCenter() ) )
//					continue;
				
				double scale = ( ni.resolution - 2 * pad ) / ( (r.width + r.height ) * 0.5 );
				
				DRectangle imBounds = new DRectangle(r);
				
				imBounds = r.scale( scale );
				imBounds.x = (ni.resolution - imBounds.width) / 2;
				imBounds.y = (ni.resolution - imBounds.height) / 2;

				g.setColor( Color.black );
				g.fillRect( 0, 0, ni.resolution, ni.resolution );
				
				g.setColor( Color.red );
				g.fillRect ( (int) imBounds.x, (int)imBounds.y, (int)imBounds.width, (int) imBounds.height);
				
				imBounds = imBounds.intersect( ni.rect() );
				
				Meta meta = new Meta( a, r, imBounds );
				
				if (a.fr.getFeat() == Feature.DOOR) { // no labels
					
					String name = Tweed.SCRATCH + UUID.randomUUID();
					
					File labelAbsFile = new File (name + ".png"); 
					ImageIO.write( bi, "png", labelAbsFile );
					ImageIO.write( bi, "png", new File (name + ".png_label") );
					
					doors.put( meta, labelAbsFile );
					
					continue;
				}
				
				a.frameScale = this.scale * 0.5 * PanesLabelApp.frameWidth * scale / ni.resolution;
				
				if (r.width > 3) // small frame sizes start to look strange 
					a.frameScale *= 8;
				
				p2.addInput( bi, bi, null, meta, a.styleZ, a.frameScale );

			} catch ( Throwable e1 ) {
				e1.printStackTrace();
			}
		}
		
		g.dispose();
		
		p2.submit ( new Job ( new JobResult() {
			@Override
			public void finished( Map<Object, File> results ) {

				try {
					
					results.putAll(doors);
					
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						BufferedImage labels = ImageIO.read( e.getValue() );
						
						if (regularize) {
							regularize ( meta.app, labels, meta.mask, 0.5 );
							ImageIO.write( labels, "png", e.getValue() );
						}
						else
							meta.app.panes = null;
						
						String dest = Pix2Pix.importTexture( e.getValue(), 255, null, meta.mask, null, new BufferedImage[3] );
						
						if ( dest != null ) {
							meta.app.textureUVs = TextureUVs.Zero_One;
							meta.app.texture = meta.app.label = dest;
						}
					}
					
				} catch (Throwable th) {
					th.printStackTrace();
				}
				finally {
					whenDone.run();
				}
			}
		} ) );
	}

	protected static void regularize( PanesLabelApp a, BufferedImage labels, DRectangle mask, double d ) {
		
		try {
		 BufferedImage crop = Imagez.clone ( Imagez.cropShared (labels, mask) );
		
		 crop = regularize (a, crop, d);
		 
		 Graphics2D g  = labels.createGraphics();
			
		 g.setColor( Color.red );
		 g.fillRect( 0, 0, labels.getWidth(), labels.getHeight() );
		 g.drawImage( crop, (int) mask.x, (int) mask.y, null );
		 
		 g.dispose();
		}
		catch (RasterFormatException fre) {
			fre.printStackTrace();
		}
	}

	private static BufferedImage regularize( PanesLabelApp a, BufferedImage crop, double threshold ) {
		
		
		int frame= 0xff0000;

		boolean[] frameY = new boolean[crop.getHeight()], frameX = new boolean[crop.getWidth()];
		int[] 
				redY = new int[crop.getHeight()], 
				redX = new int[crop.getWidth()];
		
		for (int x = 0; x < crop.getWidth(); x++) 
			for (int y = 0; y < crop.getHeight(); y++) {
				int f = (crop.getRGB( x, y ) & frame) >> 16;
				redX[x] += f;
				redY[y] += f;
			}
		
		int full  = 256 * crop.getWidth() * crop.getHeight();
		
		for (int x = 0; x < frameX.length; x++) 
			frameX[x] = redX[x] / crop.getHeight() > 256 * threshold;
			
		for (int y = 0; y < frameY.length; y++) 
			frameY[y] = redY[y] / crop.getWidth() > 256 * threshold;
			
		for (int i = 0; i < 2; i++) { // ensure at least 1 pixel of frame....
			frameX[frameX.length -1 -i] = frameX[i] = true;
			frameY[frameY.length -1 -i] = frameY[i] = true;
		}
		
		int fLen = 5;
		for (boolean[] aa : new boolean[][] {frameX, frameY}) // denoise...
			for (int i = 0; i < aa.length - fLen; i++) 
				if (!aa[i] && ! aa[i + fLen])
					for (int j = 0; j < fLen; j++)
						aa[i+j] = false;
		
		a.panes = new ArrayList<>();
		
		BufferedImage out = new BufferedImage( crop.getWidth(), crop.getHeight(), BufferedImage.TYPE_3BYTE_BGR );
		
		Graphics2D g = out.createGraphics();
		
		g.setColor( Color.red );
		g.fillRect( 0, 0, crop.getWidth(), crop.getHeight() );
		g.setColor( Color.blue );
			
		DRectangle bounds = new DRectangle( crop.getWidth(), crop.getHeight() );
		{
			int x = 0, y = 0;
			x: do {

				while ( frameX[ x ] ) {
					x++;

					if ( x >= frameX.length )
						break x;
				}
				int startX = x;

				while ( x < frameX.length - 1 && !frameX[ x ] )
					x++;

				y = 0;
				y: do {

					while ( frameY[ y ] ) {
						y++;
						if ( y >= frameY.length )
							break y;
					}
					int startY = y;

					while ( y < frameY.length && !frameY[ y ] )
						y++;

					if ( isBlue( startX, x, startY, y, crop, 0.5 ) ) {
						double height = y - startY - 1;
						a.panes.add( bounds.normalize( new DRectangle( startX, bounds.height - startY - height, x - startX - 1, height ) ) );
						g.fillRect( startX, startY, x - startX - 1, y - startY - 1 );
					}
				} while ( y <= frameY.length );
			} while ( x <= frameX.length );
		}

		for ( int x = 0; x < crop.getWidth(); x++ )
			for ( int y = 0; y < crop.getHeight(); y++ )
				if ( ( ( crop.getRGB( x, y ) & 0xff00 ) >> 8 ) > 128 ) // every one likes pot (plants)
					out.setRGB( x, y, 0xff00 );

		g.dispose();
		return out;
	}

	private static boolean isBlue( int x1, int x2, int y1, int y2, BufferedImage crop, double frac ) {
		
		if (x2 - x1 < 2 || y2 - y1 < 2)
			return false;
		
		int totalBlue = 0;
		
		for (int x = x1; x < x2; x++) 
			for (int y = y1; y < y2; y++) 
				totalBlue += (crop.getRGB( x, y ) & 0xff);
		
		return true; //totalBlue / (255 * (x2-x1) * (y2-y1) ) > frac;
	}

	private static class Meta {
		
		FRect r; // the HasApp
		DRectangle mask;
		PanesLabelApp app;
		
		private Meta( PanesLabelApp a, FRect r, DRectangle mask ) {
			this.r = r;
			this.mask = mask;
			this.app = a;
		}
	}

	public Enum[] getValidAppModes() {
		return new Enum[] { AppMode.Manual, AppMode.Net };
	}
}
