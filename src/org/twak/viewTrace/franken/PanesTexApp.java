package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.twak.tweed.Tweed;
import org.twak.utils.Imagez;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Pix2Pix.Job;
import org.twak.viewTrace.facades.Pix2Pix.JobResult;

public class PanesTexApp extends App implements HasApp {

	private PanesLabelApp parent;

	public PanesTexApp(PanesLabelApp parent) {
		super(null, "texture windows", "dows1", 8, 256);
		super.hasA = this;
		this.parent = parent;
	}
	
	public PanesTexApp(PanesTexApp t) {
		super (t);
	}
	
	@Override
	public App getUp() {
		return parent;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
			return new PanesTexApp( this );
	}
	
	final static Map<Color, Color> specLookup = new HashMap<>();
	static {
		specLookup.put( Color.blue, Color.white );
		specLookup.put( Color.red, Color.gray );
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {

		DRectangle bounds = new DRectangle( 0, 0, 256, 256 );
		int count = 0;

		Map<PanesTexApp, Meta> names = new HashMap<>();

		for ( App a : batch ) {

			try {
				
				PanesTexApp pta = (PanesTexApp)a;

				BufferedImage labels = ImageIO.read( Tweed.toWorkspace( pta.parent.label ) );

//				FRect r = (FRect) a.hasA;
//				DRectangle w = bounds.scale( mini.normalize( r ) );
//				w.y = bounds.getMaxY() - w.y - w.height;
//
//				BufferedImage dow =
//							src.getSubimage(  
//								(int) w.x, 
//								(int) w.y,
//								(int) w.width , 
//								(int) w.height );

				DRectangle mask = new DRectangle();

				BufferedImage scaled = Imagez.scaleSquare( labels, 256 );
				BufferedImage toProcess = Imagez.join( scaled, scaled );

//				Graphics2D g = toProcess.createGraphics();
//				g.drawImage( scaled, 256, 0, null );
//				g.dispose();

				String wName = name + "_" + count + "@" + System.nanoTime();
				Pix2Pix.addInput( toProcess, wName, netName );

				names.put( pta, new Meta( wName, mask, labels ) );
				count++;

			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		}
		
		Pix2Pix.submit( new Job( netName, System.nanoTime() + "_" + zAsString(), new JobResult() {
			@Override
			public void finished( File f ) {

				try {

					for ( Map.Entry<PanesTexApp, Meta> e : names.entrySet() ) {

						Meta meta = e.getValue();
						
						String dest = Pix2Pix.importTexture( f, meta.name, 1, specLookup, null );

						if ( dest != null ) {
							e.getKey().texture = dest;
							e.getKey().parent.texture = dest;
						}
					}

				} catch ( Throwable th ) {
					th.printStackTrace();
				} finally {
					whenDone.run();
				}
			}
		} ) );
	}


private static class Meta {
	String name;
	DRectangle mask;
	BufferedImage labels;
	
	private Meta(String name, DRectangle mask, BufferedImage labels) {
		this.name = name;
		this.mask = mask;
		this.labels = labels;
	}
}
}
