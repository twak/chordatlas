package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Pix2Pix.Job;
import org.twak.viewTrace.facades.Pix2Pix.JobResult;

public class PanesLabelApp extends App {

	PanesTexApp child = new PanesTexApp( this );
	public String label;
	public boolean regularize = false;
	
	public PanesLabelApp(HasApp ha) {
		super(ha, "label windows", "dows2", 8, 256);
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
		
		out.put( "facade texture", child );
		
		return out;
	}

	@Override
	public App copy() {
			return new PanesLabelApp( this );
	}
	
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
		
		DRectangle bounds = new DRectangle( 0, 0, 256, 256 );
		int count = 0;
		
		Map<FRect, Meta> names = new HashMap<>();
		
//		for ( MiniFacade mf : subfeatures ) {
		for ( App a : batch ) {
			try {
				MiniFacade mf = ( (FRect) a.hasA ).mf;

				//			if (mf.featureGen instanceof CGAMini)
				//				mf.featureGen = new FeatureGenerator( mf, mf.featureGen );

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( mf.app.texture ) );
				DRectangle mini = Pix2Pix.findBounds( mf );

				FRect r = (FRect) a.hasA;

				if ( !mini.contains( r ) )
					return;//continue;

				DRectangle w = bounds.scale( mini.normalize( r ) );
				w.y = bounds.getMaxY() - w.y - w.height;

				BufferedImage dow =
							src.getSubimage(  
								(int) w.x, 
								(int) w.y,
								(int) w.width , 
								(int) w.height );

				DRectangle mask = new DRectangle();

				BufferedImage scaled = Imagez.padTo ( Imagez.scaleSquare( dow, 120, mask, Double.MAX_VALUE, Color.black ), mask, resolution, resolution, Color.black );
				
				scaled = new FastBlur().processImage( scaled, 5 );
				
				BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );

				Graphics2D g = toProcess.createGraphics();
				g.drawImage( scaled, 256, 0, null );
				g.dispose();

				String wName = name + "@" + count + "@" + System.nanoTime();
				Pix2Pix.addInput( toProcess, wName, netName, a.styleZ );

				//					String name = System.nanoTime() + "_" + count;
				//					ImageIO.write( toProcess, "png", new File( "/home/twak/code/pix2pix-interactive/input/"+WINDOW+"/test/" + name + ".png" ) );					

				names.put( r, new Meta( wName, mask ) );
				count++;

			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		}
		
		Pix2Pix.submit ( new Job ( netName, new JobResult() {
			@Override
			public void finished( File f ) {

				try {

					
					for ( Map.Entry<FRect, Meta> e : names.entrySet() ) {

						Meta meta = e.getValue();
						
						File labelFile = new File( f, meta.name+ ".png" ) ;
						BufferedImage labels = ImageIO.read( labelFile );
						
						if (regularize) {
							regularize ( labels, meta.mask, 0.006 );
							ImageIO.write( labels, "png", labelFile );
						}
						
						String dest = Pix2Pix.importTexture( f, meta.name, 255, null, meta.mask );
						
						if ( dest != null ) {  
							e.getKey().app.texture = dest;
							((PanesLabelApp ) e.getKey().app).label = dest;
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

	protected void regularize( BufferedImage labels, DRectangle mask, double d ) {
		
		 BufferedImage crop = Imagez.clone ( Imagez.cropShared (labels, mask) );
		
		 regularize (crop, d);
		 Graphics2D g  = labels.createGraphics();
			
		 g.setColor( Color.red );
		 g.fillRect( 0, 0, labels.getWidth(), labels.getHeight() );
		 g.drawImage( crop, (int) mask.x, (int) mask.y, null );
		 
		 g.dispose();
		 
	}

	private void regularize( BufferedImage crop, double threshold ) {
		
		
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
		
		Graphics2D g = crop.createGraphics();
		
		g.setColor( Color.blue );
		g.fillRect( 0, 0, crop.getWidth(), crop.getHeight() );
			
		g.dispose();
		
		for (int x = 0; x < crop.getWidth(); x++) 
			for (int y = 0; y < crop.getHeight(); y++) {
				if (frameX[x] || frameY[y])
					crop.setRGB( x, y, frame );
			}
	}

	private static class Meta {
		String name;
		DRectangle mask;

		private Meta( String name, DRectangle mask ) {
			this.name = name;
			this.mask = mask;
		}
	}

	public Enum[] getValidAppModes() {
		return new Enum[] { AppMode.Color, AppMode.Net };
	}
}
