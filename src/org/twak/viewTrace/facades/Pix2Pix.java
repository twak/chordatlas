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
	

	
	
	public static void pix2pix( List<MiniFacade> minis, Runnable update ) {
		
		BufferedImage bi = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D ) bi.getGraphics();
		
		Map<String, MiniFacade> index = new HashMap<>();
		
		for ( MiniFacade toEdit : minis ) {

			DRectangle bounds = new DRectangle( 256, 0, 256, 256 );

			//		g.setColor( new Color (0, 0, 255 ) );
			//		g.fillRect( 255, 0, 255, 255 );

			g.setColor( new Color( 0, 48, 255 ) );
			g.fillRect( 256, 0, 256, 255 );

			DRectangle mini = toEdit.getAsRect();

			cmpRects( toEdit, g, bounds, mini, new Color( 0, 207, 255 ), Feature.DOOR );
			cmpRects( toEdit, g, bounds, mini, new Color( 0, 129, 250 ), Feature.WINDOW );
			cmpRects( toEdit, g, bounds, mini, new Color( 255, 80, 0 ), Feature.MOULDING );
			cmpRects( toEdit, g, bounds, mini, new Color( 32, 255, 224 ), Feature.CORNICE );
			cmpRects( toEdit, g, bounds, mini, new Color( 109, 254, 149 ), Feature.SILL );
			cmpRects( toEdit, g, bounds, mini, new Color( 175, 0, 0 ), Feature.SHOP );

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
								
//								FileOutputStream fos = new FileOutputStream(  );

								Files.write(  new File(Tweed.DATA + "/" + ( dest = "scratch/" + name + ".png" )).toPath(), image );

								ImageIO.write ( 
										new NormSpecGen( ImageIO.read(new ByteArrayInputStream( image ) ) ).norm(), 
										"jpg", 
										new File(Tweed.DATA + "/" + ( "scratch/" + name + "_norm" + ".jpg" ) ) );
								
//								fos.flush();
//								fos.close();

								e.getValue().texture = dest;

								texture.delete();
//								index.remove( name );
								
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
		for (FRect r : toEdit.getRects(features)) {
			
			DRectangle w = bounds.scale ( mini.normalize( r ) );
			
			w.y = bounds.getMaxY() - w.y - w.height;
			
			g.setColor( col);
			g.fillRect( (int) w.x, (int)w.y, (int)w.width, (int)w.height );
		}
	}
	
}
