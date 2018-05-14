package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.Filez;
import org.twak.utils.Imagez;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.App.TextureUVs;

/**
 * Utilties to synthesize facade using Pix2Pix network
 */

public class Pix2Pix {

	public static final int LATENT_SIZE = 8;

	public enum CMPLabel { //sequence is  z-order
		Background (1, 0,0, 170 ),
		Facade     (2, 0,0, 255 ),
		Molding    (10, 255, 85, 0 ),
		Cornice    (5, 0, 255, 255 ),
		Pillar     (11, 255,0, 0 ),
		Window     (3, 0, 255, 0 ),
//		Window     (3, 0, 85, 255 ),
		Door       (4, 0,170, 255 ),
		Sill       (6, 85,255, 170 ),
		Blind      (8, 255,255, 0 ),
		Balcony    (7, 170,255, 85 ),
		Shop       (12, 170,0, 0 ),
		Deco       (9, 255,170, 0 );
		
		final int index;
		public final Color rgb;
		
		CMPLabel (int index, int r, int g, int b) {
			this.rgb = new Color (r,g,b);
			this.index = index;
		}
	}
	
	public interface JobResult {
		public void finished(File folder);
	}
	
	public static class Job {
		String network;
		JobResult finished;
		public String name;
		
		public Job (String network, String name, JobResult finished) {
			
			this.network = network;
			this.finished = finished;
			this.name = name;
		}
	}
	
	public static void submit( Job job ) {
//		synchronized (job.network.intern()) {
			submitSafe(job);
//		}
	}
	
	public static void submitSafe( Job job ) {
		
		File go     = new File( "/home/twak/code/bikegan/input/"  + job.network + "/val/go" );
		File outDir = new File( "/home/twak/code/bikegan/output/" + job.network +"/" + job.name );
		
		try {
			FileWriter  fos = new FileWriter( go );
			fos.write( job.name );
			fos.close();
			
			
		} catch ( Throwable e1 ) {
			e1.printStackTrace();
		}

		long startTime = System.currentTimeMillis();

		do {
			try {
				Thread.sleep( 50 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		} while ( go.exists() && System.currentTimeMillis() - startTime < 1e5 );

		startTime = System.currentTimeMillis();

		if (go.exists()) {
			System.out.println( "failed to get a result "+ go.getAbsolutePath() );
			return;
		}
			
		do {

			try {
				Thread.sleep( 50 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}

			if ( outDir.exists() ) {
				
				System.out.println( "processing "+job.name );
				job.finished.finished( outDir );
				
				try {
					FileUtils.deleteDirectory( outDir );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
				return;
			}
			
		} while ( System.currentTimeMillis() - startTime < 3000 );
		
		System.out.println( "timeout trying to get result "+ job.name );
		
	}
	
	public void encode(File f, int resolution, String netName, double[] values, Runnable update ) {

		try {
			
			BufferedImage bi = ImageIO.read( f );
			bi = Imagez.scaleSquare( bi, resolution );
			bi = Imagez.join( bi, bi );

			File dir = new File( "/home/twak/code/bikegan/input/" + netName + "_e/val/" );
			dir.mkdirs();
			ImageIO.write( bi, "png", new File( dir, System.nanoTime() + ".png" ) );

			submit( new Job( netName+"_e", System.nanoTime() + "_" + Filez.stripExtn( f.getName() ), new JobResult() {

				@Override
				public void finished( File f ) {
					for ( File zf : f.listFiles() ) {
						String[] ss = zf.getName().split( "_" );
						for ( int i = 0; i < ss.length; i++ )
							values[ i ] = Double.parseDouble( ss[ i ] );
						update.run();
						return;
						
					}
				}
			} ) );

		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		
	}

	public static String importTexture( File f, String name, int specular, Map<Color, Color> specLookup, DRectangle crop ) throws IOException {
		
		File texture = new File( f, name + ".png" );
		String dest = "missing";
		if ( texture.exists() && texture.length() > 0 ) {

			BufferedImage labels = ImageIO.read( new File( f, name + ".png_label" ) ), 
					rgb = ImageIO.read( texture );

			if (crop != null) {
				
				rgb = scaleToFill ( rgb, crop );//   .getSubimage( (int) crop.x, (int) crop.y, (int) crop.width, (int) crop.height );
				labels = scaleToFill ( labels, crop );
			}
			
			NormSpecGen ns = new NormSpecGen( rgb, labels, specLookup );
			
			if (specular >= 0) {
				Graphics2D g = ns.spec.createGraphics();
				g.setColor( new Color (specular, specular, specular) );
				g.fillRect( 0, 0, ns.spec.getWidth(), ns.spec.getHeight() );
				g.dispose();
			}

			dest =  "scratch/" + name ;
			ImageIO.write( rgb    , "png", new File( Tweed.DATA + "/" + ( dest + ".png" ) ) );
			ImageIO.write( ns.norm, "png", new File( Tweed.DATA + "/" + ( dest + "_norm.png" ) ) );
			ImageIO.write( ns.spec, "png", new File( Tweed.DATA + "/" + ( dest + "_spec.png" ) ) );
			ImageIO.write ( labels, "png", new File( Tweed.DATA + "/" + ( dest + "_lab.png" ) ) );
			texture.delete();
		}
		return dest + ".png";
	}
	


	public static BufferedImage scaleToFill( BufferedImage rgb, DRectangle crop ) {
		
		BufferedImage out = new BufferedImage (rgb.getWidth(), rgb.getHeight(), rgb.getType() );
		
		Graphics2D g = out.createGraphics();
		g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR );
		g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
		
		int cropy = (int) (256 - crop.height - crop.y);
		g.drawImage( rgb, 0, 0, rgb.getWidth(), rgb.getHeight(), 
				(int) crop.x, cropy, (int) (crop.x + crop.width), (int) (cropy + crop.height ),
				null );
		g.dispose();
		
		return out;
	}

	public static DRectangle findBounds( MiniFacade toEdit ) {
		
		if ( toEdit.postState == null ) 
			return toEdit.getAsRect();
		else 
			return toEdit.postState.outerFacadeRect;
	}
	
	public static void addInput( BufferedImage bi, String name, String netName ) {
		try {
			File dir = new File( "/home/twak/code/bikegan/input/" + netName + "/val/" );
			dir.mkdirs();
			ImageIO.write( bi, "png", new File( dir, name + ".png" ) );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
}
