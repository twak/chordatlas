package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.twak.tweed.gen.skel.FCircle;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class VeluxTexApp extends App {

	public FCircle fc;
	public RoofTexApp parent;
	
	public String texture;
	
	public VeluxTexApp (FCircle fc) {
		this.fc = fc;
	}
	
	public VeluxTexApp( VeluxTexApp v ) {
		super(v);
		this.fc = v.fc;
	}

	@Override
	public App copy() {
		return new VeluxTexApp( this );
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
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		
		NetInfo ni = NetInfo.get(PanesTexApp.class); 
		Pix2Pix p2 = new Pix2Pix( NetInfo.get(this) );

		BufferedImage lBi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D lg =  lBi.createGraphics();
		
		BufferedImage eBi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D eg =  eBi.createGraphics();
		
		for ( App a : batch ) {

			if (a.appMode != AppMode.Net)
				continue;
			
			try {

				VeluxTexApp vta = (VeluxTexApp) a;

				DRectangle rect = vta.fc.toRect();

				double scale = ( ni.resolution - 60 ) / Math.max( rect.width, rect.height );
				
				DRectangle imBounds = new DRectangle(rect);
				
				imBounds = rect.scale( scale );
				imBounds.x = (ni.resolution - imBounds.width) / 2;
				imBounds.y = (ni.resolution - imBounds.height) / 2;

				lg.setColor( Color.black );
				lg.fillRect( 0, 0, ni.resolution, ni.resolution );
				eg.setColor( Color.black );
				eg.fillRect( 0, 0, ni.resolution, ni.resolution );
				
				lg.setColor( Color.red );
				lg.fillRect( (int) imBounds.x, (int) imBounds.y, (int) imBounds.width, (int)imBounds.height);
				
				int d = (int) ( scale * 0.1 ); 
				lg.setColor( Color.blue );
				lg.fillRect( (int) imBounds.x + d, (int) imBounds.y + d, (int) imBounds.width - d * 2, (int) imBounds.height - d * 2 );
				
				eg.setColor( Color.red );
				eg.fillRect( (int) imBounds.x, (int) imBounds.y, (int) imBounds.width, (int)imBounds.height);
				
				Meta meta = new Meta(imBounds, vta);
				
				p2.addInput( lBi, eBi, null, meta, new double[] {0,0,0,0,0,0,0,0},
						0.5 * PanesLabelApp.frameWidth * scale / ni.resolution );
			}
			catch (Throwable th) {
				th.printStackTrace();
			}
		}
		
		lg.dispose();
		eg.dispose();
		
		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				
				for ( Map.Entry<Object, File> e : results.entrySet() ) {
					Meta meta = (Meta) e.getKey();
					try {
						meta.vta.texture =  Pix2Pix.importTexture( e.getValue(),  -1, PanesTexApp.specLookup,
								meta.imBounds, null, new BufferedImage[3] );
					} catch ( IOException e1 ) {
						e1.printStackTrace();
					}
				}
			}
		} ) );
	}

	private static class Meta {
		DRectangle imBounds;
		VeluxTexApp vta;
		
		private Meta( DRectangle imBounds, VeluxTexApp vta ) {
			this.imBounds = imBounds;
			this.vta = vta;
		}
	}
}
