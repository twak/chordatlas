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
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

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
		specLookup.put( Color.red, Color.black );
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {

		Pix2Pix p2 = new Pix2Pix( batch.get( 0 ) );
		
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

				String wName = name + "@" + count + "@" + System.nanoTime();
				Meta meta = new Meta( pta, wName, mask, labels ); 
				p2.addInput( toProcess, meta, a.styleZ );

//				names.put( pta, );
				count++;

			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		}
		
		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {

				try {
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta) e.getKey();
						
						String dest = Pix2Pix.importTexture( e.getValue(), -1, specLookup, null );

						if ( dest != null ) {
							meta.pta.texture = dest;
							meta.pta.parent.texture = dest;
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
		PanesTexApp pta;
		
		private Meta( PanesTexApp pta, String name, DRectangle mask, BufferedImage labels ) {
			this.pta = pta;
			this.name = name;
			this.mask = mask;
			this.labels = labels;
		}
	}

	public Enum[] getValidAppModes() {
		return new Enum[] { AppMode.Color, AppMode.Net };
	}
}
