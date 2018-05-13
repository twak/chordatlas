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

	private static final String LABEL2PHOTO = "bike_2", 
//			private static final String LABEL2PHOTO = "facades3k", 
			WINDOW = "window_super_res", 
			FACADE = "facade_super_res";
	
	public static final int LATENT_SIZE = 8;

	public enum CMPLabel { //sequence is  z-order
		Background (1, 0,0, 170 ),
		Facade     (2, 0,0, 255 ),
		Molding    (10, 255, 85, 0 ),
		Cornice    (5, 0, 255, 255 ),
		Pillar     (11, 255,0, 0 ),
		Window     (3, 0, 85, 0 ),
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

			submit( new Job( LABEL2PHOTO+"_e", System.nanoTime() + "_" + Filez.stripExtn( f.getName() ), new JobResult() {

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

//	public void facade( List<MiniFacade> minis, double[] z, String netName, int resolution, Runnable update ) {
//		
//	}

	public static String importTexture( File f, String name, int specular, DRectangle crop ) throws IOException {
		
		File texture = new File( f, name + ".png" );
		String dest = "missing";
		if ( texture.exists() && texture.length() > 0 ) {

			BufferedImage labels = ImageIO.read( new File( f, name + ".png_label" ) ), 
					rgb = ImageIO.read( texture );

			if (crop != null) {
				
				rgb = scaleToFill ( rgb, crop );//   .getSubimage( (int) crop.x, (int) crop.y, (int) crop.width, (int) crop.height );
				labels = scaleToFill ( labels, crop );
			}
			
			NormSpecGen ns = new NormSpecGen( rgb, labels );
			
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
//			ImageIO.write ( labels, "png", new File( Tweed.DATA + "/" + ( dest + ".png" ) ) );
			texture.delete();
		}
		return dest + ".png";
	}
	

	private static class Meta {
		String name;
		DRectangle mask;
		private Meta(String name, DRectangle mask) {
			this.name = name;
			this.mask = mask;
		}
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
	
	private static class FacState {
		
		BufferedImage big;
		int nextX = 0, nextY = 0, maxX, maxY;
		public String nextTile;
		
		public FacState( BufferedImage big ) {
			this.big = big;

			this.maxX = ( big.getWidth()  / 200 );
			this.maxY = ( big.getHeight() / 200 );
		}
	}
	
	private void facadeSuper( List<MiniFacade> subfeatures, Runnable update ) {
		
		Map<MiniFacade, FacState> todo = new LinkedHashMap();
		
		for ( MiniFacade mf : subfeatures ) {
			try {

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( mf.app.texture ) );
//				src = Imagez.scaleLongest( src, 128 );
//				ImageIO.write( src, "png", new File( "/home/twak/Desktop/foo/" + System.nanoTime() + "_orig.png" ) );

				
				DRectangle mini = findBounds( mf );
				
				BufferedImage highRes = new BufferedImage ( 
						(int)( mini.width * FacadeTool.pixelsPerMeter),
						(int)( mini.height * FacadeTool.pixelsPerMeter), BufferedImage.TYPE_3BYTE_BGR);
				
				{
					
					Graphics2D g = highRes.createGraphics();
					g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
					g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
					g.drawImage( src, 0, 0, highRes.getWidth(), highRes.getHeight(), null );
					g.dispose();
				}
				
				todo.put( mf, new FacState(highRes) );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
		
		facadeContinue (todo, update);
	}
	
	private DRectangle findBounds( MiniFacade toEdit ) {
		
		if ( toEdit.postState == null ) 
			return toEdit.getAsRect();
		else 
			return toEdit.postState.outerFacadeRect;
	}
	
	private synchronized void facadeContinue( Map<MiniFacade, FacState> todo, Runnable update ) {

		if (todo.isEmpty()) {
			update.run();
			return;
		}
		
		int MAX_CONCURRENT = 32;
		
			int count = 0;
			for ( Map.Entry<MiniFacade, FacState> e : todo.entrySet() ) {
				try {

					FacState state = e.getValue();

					String name = System.nanoTime() + "_" + count;

					BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );

					state.nextTile = name;
					
					{
						Graphics2D g = toProcess.createGraphics();
						g.drawImage( state.big, 
								-state.big.getWidth () + 209 * (state.nextX+1) + 47 + 256, 
								-state.big.getHeight() + 209 * (state.nextY+1) + 47, 
								state.big.getWidth(),
								state.big.getHeight(),
								null );
						
						g.dispose();
					}
					
					ImageIO.write( toProcess, "png", new File( "/home/twak/code/pix2pix-interactive/input/" + FACADE + "/test/" + name + ".png" ) );

					System.out.println( "++" + state.nextX +", " + state.nextY );

					if ( count > MAX_CONCURRENT )
						break;
				} catch ( Throwable th ) {
					th.printStackTrace();
				}
			}
			
			submit ( new Job ( FACADE, ""+System.nanoTime() /* fixme */, new JobResult() {
				@Override
				public void finished( File f ) {
					
					
					// patch images with new tiles...
					for ( Map.Entry<MiniFacade, FacState> e : todo.entrySet() ) {
						try {

							FacState state = e.getValue();
							
							File texture = new File( f, "images/" + state.nextTile + "_fake_B.png" );
							if ( texture.exists() && texture.length() > 0 ) {

								BufferedImage rgb = ImageIO.read( texture );
								
								File orig = new File( f, "images/" + state.nextTile + "_real_A.png" );
								
//								String name = Math.random()+"";
//								ImageIO.write( rgb, "png", new File( "/home/twak/Desktop/foo/" + name + ".png" ) );
//								ImageIO.write( ImageIO.read( orig ), "png", new File( "/home/twak/Desktop/foo/" + name + "_orig.png" ) );
								
								Graphics2D g = state.big.createGraphics();
								g.drawImage( rgb, 
										state.big.getWidth () - 209 * (state.nextX+1) -47, 
										state.big.getHeight() - 209 * (state.nextY+1) -47, 
										rgb.getWidth(), 
										rgb.getHeight(), null );
								g.dispose();
							}
							
							if ( state.nextY == state.maxY && state.nextX == state.maxX ) {
								state.nextX = state.nextY = -1; // done
							} else if ( state.nextX == state.maxX ) {
								state.nextY++;
								state.nextX = 0;
							} else {
								state.nextX++;
							}
							
							if ( count > MAX_CONCURRENT )
								break;
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
					
					// finished - import texture!
					for (MiniFacade mf : new ArrayList<> (todo.keySet())) {
						FacState state = todo.get( mf );
						
						if (state.nextY == -1) { // done
							
							todo.remove( mf );
							
							File texture = new File( f, "images/" + state.nextTile + "_fake_B.png" );
							
							NormSpecGen ns = new NormSpecGen( state.big, null );

							String dest =  "scratch/facade_" + state.nextTile ;
							
							try {
								ImageIO.write( state.big, "png", new File( Tweed.DATA + "/" + ( dest + ".png" ) ) );
								ImageIO.write( ns.norm  , "png", new File( Tweed.DATA + "/" + ( dest + "_norm.png" ) ) );
//								ImageIO.write( ns.spec  , "png", new File( Tweed.DATA + "/" + ( dest + "_spec.png" ) ) );
								
								mf.app.textureUVs = TextureUVs.ZERO_ONE;
								mf.app.texture = dest + ".png";
										
							} catch ( IOException e1 ) {
								e1.printStackTrace();
							}
						}
					}
					
					try {
						FileUtils.deleteDirectory( f );
					} catch ( IOException e1 ) {
						e1.printStackTrace();
					}

					facadeContinue( todo, update );
				}
			} ) );
		
	}

	private void windowsSuper( List<MiniFacade> subfeatures, Runnable update ) {
		
		DRectangle bounds = new DRectangle( 0, 0, 256, 256 );
		int count = 0;
		
		Map<FRect, Meta> names = new HashMap<>();
		
		for ( MiniFacade mf : subfeatures ) {
			try {
			
				BufferedImage src = ImageIO.read( Tweed.toWorkspace( mf.app.texture )  );
				DRectangle mini = findBounds( mf );
				
				for ( FRect r : mf.featureGen.getRects( Feature.WINDOW, Feature.SHOP, Feature.DOOR ) ) {
					
					if (!mini.contains( r ))
						continue;
					
					DRectangle w = bounds.scale ( mini.normalize( r ) );
					w.y = bounds.getMaxY() - w.y - w.height;
					
					BufferedImage dow =
							src.getSubimage(  
								(int) w.x, 
								(int) w.y,
								(int) w.width , 
								(int) w.height );
					
					DRectangle mask = new DRectangle();
					
					BufferedImage scaled = Imagez.scaleSquare( dow, 256, mask, Double.MAX_VALUE );
					BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
					
					Graphics2D g = toProcess.createGraphics();
					g.drawImage( scaled, 256, 0, null );
					g.dispose();
					
					String name = System.nanoTime() + "_" + count;
					ImageIO.write( toProcess, "png", new File( "/home/twak/code/pix2pix-interactive/input/"+WINDOW+"/test/" + name + ".png" ) );					
					names.put( r, new Meta( name, mask ) );
					
					count++;
				}

			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
			
		}
		
		submit ( new Job ( WINDOW,  ""+System.nanoTime() /* fixme */, new JobResult() {
			@Override
			public void finished( File f ) {

				try {

					for ( Map.Entry<FRect, Meta> e : names.entrySet() ) {

						Meta meta = e.getValue();
						String dest = importTexture( f, meta.name, 255, meta.mask );
						
						
						if ( dest != null ) 
							e.getKey().app.texture = dest;
					}
				} catch (Throwable th) {
					th.printStackTrace();
				}
				finally {
					update.run();
				}
			}
		} ) );
	}
	
	public static void setInput( BufferedImage bi, String name, String netName ) {
		try {
			File dir = new File( "/home/twak/code/bikegan/input/" + netName + "/val/" );
			dir.mkdirs();
			ImageIO.write( bi, "png", new File( dir, name + ".png" ) );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
}
