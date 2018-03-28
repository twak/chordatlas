package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.utils.Imagez;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;

/**
 * Utilties to synthesize facade using Pix2Pix network
 */

public class Pix2Pix {

	private static final String LABEL2PHOTO = "facades_label2photo_pretrained";
	private static final String WINDOW = "window_super_res";

	public enum CMPLabel { //sequence is  z-order
		Background (1, 0,0, 170 ),
		Facade     (2, 0,0, 255 ),
		Molding    (10, 255, 85, 0 ),
		Cornice    (5, 0, 255, 255 ),
		Pillar     (11, 255,0, 0 ),
		Window     (3, 0, 85, 255 ),
		Door       (4, 0,170, 255 ),
		Sill       (6, 85,255, 170 ),
		Blind      (8, 255,255, 0 ),
		Balcony    (7, 170,255, 85 ),
		Shop       (12, 170,0, 0 ),
		Deco       (9, 255,170, 0 );
		
		final int index;
		final Color rgb;
		
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
		
		public Job (String network, JobResult finished) {
			this.network = network;
			this.finished = finished;
		}
	}
	
	public void submit( Job job ) {
		synchronized (job.network.intern()) {
			submitSafe(job);
		}
	}
	
	public void submitSafe( Job job ) {
		
		File go = new File( "/home/twak/code/pix2pix-interactive/input/"+ job.network + "/test/go" );
		String outputName = System.nanoTime() + "_" + Math.random();
		File outDir = new File( "/home/twak/code/pix2pix-interactive/output/" + job.network +"/" + outputName );
		
		try {
			FileWriter  fos = new FileWriter( go );
			fos.write( outputName );
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
		} while ( go.exists() && startTime - System.currentTimeMillis() < 2e4 );

		startTime = System.currentTimeMillis();

		do {

			try {
				Thread.sleep( 50 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}

			if ( outDir.exists() ) {
				
				System.out.println( "processing" );
				job.finished.finished( outDir );
				
				try {
					FileUtils.deleteDirectory( outDir );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
				
				break;
			}
			
		} while ( System.currentTimeMillis() - startTime < 3000 );
	}

	
	public void facade( List<MiniFacade> minis, Runnable update ) {
		
		BufferedImage bi = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D ) bi.getGraphics();
		
		Map<String, MiniFacade> index = new HashMap<>();
		
		for ( MiniFacade toEdit : minis ) {

			DRectangle bounds = new DRectangle( 256, 0, 256, 256 );
			DRectangle mini = findBounds (toEdit);
			
			g.setColor( CMPLabel.Background.rgb );
			g.fillRect( 256, 0, 256, 255 );
			
			g.setColor( CMPLabel.Facade.rgb );
			
			if ( toEdit.postState == null ) {
				
				g.fillRect( 256, 0, 256, 255 );
				mini = toEdit.getAsRect();
			} else {
				
				mini = toEdit.postState.outerFacadeRect;

				for ( Loop<? extends Point2d> l : toEdit.postState.skelFaces)
					g.fill( toPoly( toEdit, bounds, mini, l ) );
				
				g.setColor( CMPLabel.Background.rgb );
				for ( LoopL<Point2d> ll : toEdit.postState.occluders )
					for ( Loop<Point2d> l : ll )
						g.fill( toPoly( toEdit, bounds, mini, l ) );
			}

			cmpRects( toEdit, g, bounds, mini, CMPLabel.Door.rgb, Feature.DOOR );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Window.rgb, Feature.WINDOW );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Molding.rgb, Feature.MOULDING );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Cornice.rgb, Feature.CORNICE );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Sill.rgb, Feature.SILL );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Shop.rgb, Feature.SHOP );

			String name = System.nanoTime() + "_" + index.size();

			index.put ( name, toEdit );
			
			try {

				ImageIO.write( bi, "png", new File( "/home/twak/code/pix2pix-interactive/input/"+LABEL2PHOTO+"/test/" + name + ".png" ) );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		
		submit ( new Job (LABEL2PHOTO, new JobResult() {

			@Override
			public void finished( File f ) {

				boolean found = false;

				String dest;
				try {

					List<MiniFacade> subfeatures = new ArrayList();

					new File( Tweed.SCRATCH ).mkdirs();

					for ( Map.Entry<String, MiniFacade> e : index.entrySet() ) {

						String name = e.getKey();
						dest = importTexture( f, name, -1 );

						if ( dest != null ) {
							e.getValue().texture = dest;
							subfeatures.add( e.getValue() );
							found = true;
						}
					}

					if ( found ) {
						new Thread() {
							public synchronized void start() {
								windows( subfeatures, update );
							};
						}.start();

						return;
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				update.run();
			}

		} ) );
	}

	private String importTexture( File f, String name, int specular ) throws IOException {
		
		File texture = new File( f, "images/" + name + "_fake_B.png" );
		String dest = null;
		if ( texture.exists() && texture.length() > 0 ) {

			byte[] image = Files.readAllBytes( texture.toPath() );

			Files.write( new File( Tweed.DATA + "/" + ( dest = "scratch/" + name + ".png" ) ).toPath(), image );

			BufferedImage labels = ImageIO.read( new File( f, "images/" + name + "_real_A.png" ) ), 
					rgb = ImageIO.read( new ByteArrayInputStream( image ) );

			NormSpecGen ns = new NormSpecGen( rgb, labels );
			
			if (specular >= 0) {
				Graphics2D g = ns.spec.createGraphics();
				g.setColor( new Color (specular, specular, specular) );
				g.fillRect( 0, 0, ns.spec.getWidth(), ns.spec.getHeight() );
				g.dispose();
			}

			ImageIO.write( ns.norm, "png", new File( Tweed.DATA + "/" + ( "scratch/" + name + "_norm.png" ) ) );
			ImageIO.write( ns.spec, "png", new File( Tweed.DATA + "/" + ( "scratch/" + name + "_spec.png" ) ) );
											ImageIO.write ( labels, 
													"png", new File(Tweed.DATA + "/" + ( "scratch/" + name + "_src.png" ) ) );
			texture.delete();
		}
		return dest;
	}
	
	private DRectangle findBounds( MiniFacade toEdit ) {
		
		if ( toEdit.postState == null ) 
			return toEdit.getAsRect();
		else 
			return toEdit.postState.outerFacadeRect;
	}

	private void windows( List<MiniFacade> subfeatures, Runnable update ) {
		
		DRectangle bounds = new DRectangle( 0, 0, 256, 256 );
		int count = 0;
		
		Map<FRect, String> names = new HashMap<>();
		
		for ( MiniFacade mf : subfeatures ) {
			try {
			
				BufferedImage src = ImageIO.read( Tweed.toWorkspace( mf.texture )  );
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
					
					BufferedImage scaled = Imagez.scaleSquare( Imagez.scaleLongest( dow, 256 ), 256 );
					BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
					
					Graphics2D g = toProcess.createGraphics();
					g.drawImage( scaled, 256, 0, null );
					g.dispose();
					
					String name = System.nanoTime() + "_" + count;
					ImageIO.write( toProcess, "png", new File( "/home/twak/code/pix2pix-interactive/input/"+WINDOW+"/test/" + name + ".png" ) );					
					names.put( r, name );
					
					count++;
				}

			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
			
		}
		
		submit ( new Job ( WINDOW, new JobResult() {
			@Override
			public void finished( File f ) {

				try {

					for ( Map.Entry<FRect, String> e : names.entrySet() ) {

						String name = e.getValue();
						String dest = importTexture( f, name, 255 );

						if ( dest != null ) 
							e.getKey().texture = dest;
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
	

	private static Polygon toPoly( MiniFacade toEdit, DRectangle bounds, DRectangle mini, Loop<? extends Point2d> loop ) {
		Polygon p = new Polygon();

			for ( Point2d pt : loop ) {
				Point2d p2 = bounds.scale( mini.normalize( pt ) );
				p.addPoint( (int) p2.x, (int) -p2.y+256 );
			}
		return p;
	}

	public static void cmpRects( MiniFacade toEdit, Graphics2D g, DRectangle bounds, DRectangle mini, Color col, Feature...features ) {
		for (FRect r : toEdit.featureGen.getRects(features)) {
			
			DRectangle w = bounds.scale ( mini.normalize( r ) );
			
			w.y = bounds.getMaxY() - w.y - w.height;
			
			g.setColor( col);
			g.fillRect( (int) w.x, (int)w.y, (int)w.width, (int)w.height );
		}
	}
	
}
