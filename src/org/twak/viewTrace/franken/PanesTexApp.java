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

import org.twak.tweed.Tweed;
import org.twak.utils.Filez;
import org.twak.utils.Imagez;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class PanesTexApp extends App implements HasApp {

	private PanesLabelApp parent;

	public PanesTexApp(PanesLabelApp parent) {
		super( (HasApp) null );
		super.hasA = this;
		this.parent = parent;
	}
	
	public PanesTexApp(PanesTexApp t) {
		super ( (App ) t);
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

		NetInfo ni = NetInfo.get(this); 
		Pix2Pix p2 = new Pix2Pix( NetInfo.get(this) );

		BufferedImage lBi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D lg =  lBi.createGraphics();
		
		BufferedImage eBi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D eg =  eBi.createGraphics();
		
		for ( App a : batch ) {

			try {
				
				PanesTexApp pta = (PanesTexApp)a;
				PanesLabelApp pla = pta.parent;
				
				BufferedImage labelSrc = ImageIO.read( Tweed.toWorkspace( pla.label ) );

				FRect r = (FRect) pla.hasA;
				
				double scale = ( ni.resolution - 2 * PanesLabelApp.pad ) / Math.max( r.width, r.height );
				
				DRectangle imBounds = new DRectangle(r);
				
				imBounds = r.scale( scale );
				imBounds.x = (ni.resolution - imBounds.width) / 2;
				imBounds.y = (ni.resolution - imBounds.height) / 2;

				lg.setColor( Color.black );
				lg.fillRect( 0, 0, ni.resolution, ni.resolution );
				eg.setColor( Color.black );
				eg.fillRect( 0, 0, ni.resolution, ni.resolution );
				
				lg.drawImage( labelSrc, (int) imBounds.x, (int) imBounds.y, (int) imBounds.width, (int)imBounds.height, null);
				eg.setColor( Color.red );
				eg.fillRect( (int) imBounds.x, (int) imBounds.y, (int) imBounds.width, (int)imBounds.height);
				
				Meta meta = new Meta( pta, imBounds ); 
				
				p2.addInput( lBi, eBi, null, meta, a.styleZ, pta.parent.frameScale );


			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		}
		
		lg.dispose();
		eg.dispose();
		
		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {

				try {
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta) e.getKey();
						
						String dest = Pix2Pix.importTexture( e.getValue(), -1, specLookup, meta.imBounds );

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
		PanesTexApp pta;
		DRectangle imBounds;
		
		private Meta( PanesTexApp pta, DRectangle imBounds ) {
			this.pta = pta;
			this.imBounds = imBounds;
		}
	}

	public Enum[] getValidAppModes() {
		return new Enum[] { AppMode.Off, AppMode.Net };
	}
}
