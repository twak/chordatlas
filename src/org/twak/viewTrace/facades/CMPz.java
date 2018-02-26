package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.twak.camp.Output;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;

/**
 * Utilties to synthesize facade using Pix2Pix network
 */
public class CMPz {
	

	public static void cmpRender( MiniFacade toEdit, PlanSkeleton skel, Output output, SuperFace sf, Runnable update ) {
		
		BufferedImage bi = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D ) bi.getGraphics();
		
		DRectangle bounds = new DRectangle (256,0,256, 256);
		
//		g.setColor( new Color (0, 0, 255 ) );
//		g.fillRect( 255, 0, 255, 255 );
		
		g.setColor( new Color (0, 48, 255 ) );
		g.fillRect( 256, 0, 256, 255 );
		
		DRectangle mini = toEdit.getAsRect();
		
		cmpRects( toEdit, g, bounds, mini,new Color (0,207,255) , Feature.DOOR );
		cmpRects( toEdit, g, bounds, mini,new Color (0,129,250) , Feature.WINDOW );
		cmpRects( toEdit, g, bounds, mini,new Color (255,80,0) , Feature.MOULDING );
		cmpRects( toEdit, g, bounds, mini,new Color (32, 255, 224) , Feature.CORNICE );
		cmpRects( toEdit, g, bounds, mini,new Color (109, 254, 149) , Feature.SILL );
		cmpRects( toEdit, g, bounds, mini,new Color (175, 0, 0) , Feature.SHOP );
		
		String name = System.nanoTime()+"";
		
		try {
			
			
			ImageIO.write (bi, "png", new File ("/home/twak/code/pytorch-CycleGAN-and-pix2pix/input/test/"+name+".png"));
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
		long startTime = System.currentTimeMillis();
		
		do {
			
			try {
				Thread.sleep( 50 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			
			
			File[] fz = new File ("/home/twak/code/pytorch-CycleGAN-and-pix2pix/output/").listFiles();
			
			if (fz.length > 0) {
				
				for (File f : fz) {
				
					String dest;
					try {
						
						new File (Tweed.SCRATCH).mkdirs();
						
						
//						File texture = new File (f, "images/"+name+"_real_A.png");
						File texture = new File (f, "images/"+name+"_fake_B.png");
						
						if (texture.exists() && texture.length() > 0) {
							
							FileOutputStream fos = new FileOutputStream( Tweed.DATA + 
									"/"+(dest = "scratch/" + name+".png") );
							
							Files.copy( texture.toPath(), fos );
							
							fos.flush();
							fos.close();
						
							if (dest != null)
								toEdit.texture = dest;
							
							texture.delete();
							break;
						}
					} catch ( Throwable e ) {
						e.printStackTrace();
					}
					
				}
			}
			
		}
		while ( System.currentTimeMillis() - startTime < 1000 );
		
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
