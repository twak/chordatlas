package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;

/**
 * Utilties to synthesize facade using Pix2Pix network
 */

public class Pix2Pix {

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
	
	public static void pix2pix( List<MiniFacade> minis, Runnable update ) {
		
		BufferedImage bi = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D ) bi.getGraphics();
		
		Map<String, MiniFacade> index = new HashMap<>();
		
		for ( MiniFacade toEdit : minis ) {

			DRectangle bounds = new DRectangle( 256, 0, 256, 256 );

			//		g.setColor( new Color (0, 0, 255 ) );
			//		g.fillRect( 255, 0, 255, 255 );

//			get resultion right
//			stretch and fill mf.skelFaces in dark blue
			
			
			g.setColor( new Color( 0, 48, 255 ) );
			g.fillRect( 256, 0, 256, 255 );

			DRectangle mini = toEdit.postState == null ? toEdit.getAsRect() : toEdit.postState.outerFacadeRect;

			cmpRects( toEdit, g, bounds, mini, CMPLabel.Door.rgb, Feature.DOOR );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Window.rgb, Feature.WINDOW );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Molding.rgb, Feature.MOULDING );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Cornice.rgb, Feature.CORNICE );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Sill.rgb, Feature.SILL );
			cmpRects( toEdit, g, bounds, mini, CMPLabel.Shop.rgb, Feature.SHOP );

			String name = System.nanoTime() + "_" + index.size();

			index.put ( name, toEdit );
			
			try {

				ImageIO.write( bi, "png", new File( "/home/twak/code/pix2pix-interactive/input/test/" + name + ".png" ) );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		
		File go =  new File( "/home/twak/code/pix2pix-interactive/input/test/go" );
		
		try {
			new FileOutputStream( go  ).close();
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
			
			File[] fz = new File ("/home/twak/code/pix2pix-interactive/output/").listFiles();
			
			if (fz.length > 0) {
				
				boolean found = false;
				
				for (File f : fz) {
				
					String dest;
					try {
						
						new File (Tweed.SCRATCH).mkdirs();
						
						for ( Map.Entry<String, MiniFacade> e : index.entrySet() ) {

							String name = e.getKey();
							//						File texture = new File (f, "images/"+name+"_real_A.png");
							File texture = new File( f, "images/" + name + "_fake_B.png" );

							if ( texture.exists() && texture.length() > 0 ) {

								byte[] image = Files.readAllBytes( texture.toPath() );
								
								Files.write(  new File(Tweed.DATA + "/" + ( dest = "scratch/" + name + ".png" )).toPath(), image );

								NormSpecGen ns = new NormSpecGen(
										ImageIO.read(new ByteArrayInputStream( image ) ),
										ImageIO.read( new File( f, "images/" + name + "_real_A.png" ) )
								);
								
								ImageIO.write ( ns.norm, 
										"png", new File(Tweed.DATA + "/" + ( "scratch/" + name + "_norm.png" ) ) );
								ImageIO.write ( ns.spec, 
										"png", new File(Tweed.DATA + "/" + ( "scratch/" + name + "_spec.png" ) ) );
								
								e.getValue().texture = dest;

								texture.delete();
								
								found = true;
							}
						}
						
						if (found) {
							FileUtils.deleteDirectory(f);
							update.run();
							return;
						}
						
					} catch ( Throwable e ) {
						e.printStackTrace();
					}
					
				}
			}
			
		}
		while ( System.currentTimeMillis() - startTime < 3000 );
		
		update.run();
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
