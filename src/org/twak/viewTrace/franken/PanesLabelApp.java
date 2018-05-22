package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.Tweed;
import org.twak.utils.Imagez;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class PanesLabelApp extends App {

	PanesTexApp child = new PanesTexApp( this );
	public String label;
	public boolean regularize = true;
	public List<DRectangle> panes = null;
	
	public PanesLabelApp(HasApp ha) {
		super(ha );
	}
	
	public PanesLabelApp(PanesLabelApp t) {
		super (t);
	}
	
	@Override
	public App getUp() {
		return ((FRect)hasA).mf.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		MultiMap<String, App>  out = new MultiMap<>();
		
		out.put( "texture", child );
		
		return out;
	}

	@Override
	public App copy() {
			return new PanesLabelApp( this );
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		return new AutoCheckbox( this, "regularize", "regularize" ) {
			@Override
			public void updated( boolean selected ) {
				
				for (App a : apps)
					((PanesLabelApp)a).regularize = selected;
				
				apps.computeAll(globalUpdate);
			}
		};
		
	}


	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {

		NetInfo ni = NetInfo.get(this); 
		Pix2Pix p2 = new Pix2Pix( ni );

		DRectangle bounds = new DRectangle( 0, 0, 256, 256 );
		int count = 0;
		
		panes = null;
		
		Map<FRect, Meta> names = new HashMap<>();
		
//		for ( MiniFacade mf : subfeatures ) {
		for ( App a : batch ) {
			try {
				MiniFacade mf = ( (FRect) a.hasA ).mf;

				//			if (mf.featureGen instanceof CGAMini)
				//				mf.featureGen = new FeatureGenerator( mf, mf.featureGen );

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( mf.app.coarse ) );
				DRectangle mini = Pix2Pix.findBounds( mf );

				FRect r = (FRect) a.hasA;

				if ( !mini.contains( r ) )
					return;//continue;

				DRectangle w = bounds.transform( mini.normalize( r ) );
				w.y = bounds.getMaxY() - w.y - w.height;

				BufferedImage dow =
							src.getSubimage(  
								(int) w.x, 
								(int) w.y,
								(int) w.width , 
								(int) w.height );

				DRectangle mask = new DRectangle();

				BufferedImage scaled = Imagez.padTo ( Imagez.scaleSquare( dow, 120, mask, Double.MAX_VALUE, Color.black ), 
						mask, ni.resolution, ni.resolution, Color.black );
				
//				scaled = new FastBlur().processImage( scaled, 5 );
				
				BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );

				Graphics2D g = toProcess.createGraphics();
				g.drawImage( scaled, 256, 0, null );
				g.dispose();

				Meta meta = new Meta( (PanesLabelApp) a, r, mask );
				p2.addInput( toProcess, meta, a.styleZ );

				//					String name = System.nanoTime() + "_" + count;
				//					ImageIO.write( toProcess, "png", new File( "/home/twak/code/pix2pix-interactive/input/"+WINDOW+"/test/" + name + ".png" ) );					

				count++;

			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		}
		
		p2.submit ( new Job ( new JobResult() {
			@Override
			public void finished( Map<Object, File> results ) {

				try {
					
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
//						File labelFile = new File( e.getValue(), meta.name+ ".png" ) ;
						BufferedImage labels = ImageIO.read( e.getValue() );
						
						if (regularize) {
							regularize ( meta.app, labels, meta.mask, 0.006 );
							ImageIO.write( labels, "png", e.getValue() );
						}
						
						String dest = Pix2Pix.importTexture( e.getValue(), 255, null, meta.mask );
						
						if ( dest != null )   
							 meta.r.app.texture = ((PanesLabelApp ) meta.r.app).label = dest;
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
		
		 BufferedImage crop = Imagez.clone ( Imagez.cropShared (labels, mask) );
		
		 regularize (a, crop, d);
		 Graphics2D g  = labels.createGraphics();
			
		 g.setColor( Color.red );
		 g.fillRect( 0, 0, labels.getWidth(), labels.getHeight() );
		 g.drawImage( crop, (int) mask.x, (int) mask.y, null );
		 
		 g.dispose();
		 
	}

	private static void regularize( PanesLabelApp a, BufferedImage crop, double threshold ) {
		
		
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
			frameX[x] = redX[x] > full * threshold;
			
		for (int y = 0; y < frameY.length; y++) 
			frameY[y] = redY[y] > full * threshold;
			
		for (int i = 0; i < 10; i++) {
			frameX[frameX.length -1 -i] = frameX[i] = true;
			frameY[frameY.length -1 -i] = frameY[i] = true;
		}
		
		int fLen = 3;
		
//		for (boolean[] a : new boolean[][] {frameX, frameY}) // denoise
//			for (int i = 0; i < a.length - fLen; i++) 
//				if (!a[i] && ! a[i + fLen])
//					for (int j = 0; j < fLen; j++)
//						a[j] = false;
		
		a.panes = new ArrayList<>();
		
		Graphics2D g = crop.createGraphics();
		
		g.setColor( Color.red );
		g.fillRect( 0, 0, crop.getWidth(), crop.getHeight() );
		g.setColor( Color.blue );
			
		DRectangle bounds = new DRectangle(crop.getWidth(), crop.getHeight() );
		
		int x = 0, y = 0;
		x:
		do {
			
			while (frameX[x]) {
				x++;
				
				if  ( x >= frameX.length )
				break x;
			}
			int startX = x;
			
			while (x < frameX.length-1 && !frameX[x] )
				x++;
			
			y=0;
			y:
			do {
			
				while (frameY[y] ) {
					y++;
					if (y >= frameY.length)
						break y;
				}
				int startY = y;
				
				while (y < frameY.length-1 && !frameY[y])
					y++;
				
				if ( isBlue ( startX, x, startY, y, crop, 0.5 ) ) {
					double height = y - startY - 1;
					a.panes.add( bounds.normalize( new DRectangle (startX, bounds.height - startY - height, x - startX -1, height) ) );
					g.fillRect( startX, startY, x - startX -1, y - startY - 1 );
				}
			}
			while (y < frameY.length);
		}
		while (x < frameX.length);
		
		g.dispose();
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
		return new Enum[] { AppMode.Off, AppMode.Net };
	}
}
