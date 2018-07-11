package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.TweedSettings;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class DoorTexApp extends App {

	FRect fr;
	public Color color = Color.gray;
	public String texture;
	
	public DoorTexApp(FRect fr ) {
		super( );
		
		this.fr = fr;
		
		if (TweedSettings.settings.siteplanInteractiveTextures)
			appMode = AppMode.Net;
		
		getUp( ).styleSource.install(this);
	}
	
	public DoorTexApp(DoorTexApp t) {
		super ( (App ) t);

		this.fr = t.fr;
		
		if (TweedSettings.settings.siteplanInteractiveTextures)
			appMode = AppMode.Net;
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {

		JPanel out = new JPanel(new ListDownLayout() );

		if ( appMode == AppMode.Manual ) {
			JButton col = new JButton( "color" );

			col.addActionListener( e -> new ColourPicker( null, color ) {
				@Override
				public void picked( Color color ) {

					for ( App a : apps ) {
						( (DoorTexApp) a ).color = color;
						( (DoorTexApp) a ).fr.panesLabelApp.texture = null;
					}

					globalUpdate.run();
				}
			} );

			out.add( col );
		}

		return out;
	}
	
	@Override
	public App getUp() {
		return fr.panesLabelApp;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
			return new DoorTexApp( this );
	}

	public final static int pad = 20;
	
	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) { // first compute latent variables
		
		NetInfo ni = NetInfo.get(this); 
		Pix2Pix p2 = new Pix2Pix( ni );

		
		BufferedImage bi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();
		
		
		for ( App a_ : batch ) {
			try {
				
				if (a_.appMode != AppMode.Net)
					continue;
				
				DoorTexApp a = (DoorTexApp)a_;
				
				FRect r = a.fr;
				
				if ( !Pix2Pix.findBounds( a.fr.mf, true ).contains( r.getCenter() ) )
					continue;
				
				double scale = ( ni.resolution - 2 * pad ) / Math.max( r.width, r.height );
				
				DRectangle imBounds = new DRectangle(r);
				
				imBounds = r.scale( scale );
				imBounds.x = (ni.resolution - imBounds.width) / 2;
				imBounds.y = (ni.resolution - imBounds.height) / 2;

				g.setColor( Color.black );
				g.fillRect( 0, 0, ni.resolution, ni.resolution );
				
				g.setColor( Color.red );
				g.fillRect ( (int) imBounds.x, (int)imBounds.y, (int)imBounds.width, (int) imBounds.height);
				
				Meta meta = new Meta( a, r, imBounds );
				
				p2.addInput( bi, bi, null, meta, a.styleZ, null );

			} catch ( Throwable e1 ) {
				e1.printStackTrace();
			}
		}

		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta) e.getKey();

						BufferedImage[] maps = new BufferedImage[3];

						String dest;
						dest = Pix2Pix.importTexture( e.getValue(), 100, null, meta.mask, null, maps );

						if ( dest != null )
							meta.app.texture = dest;
					}
				} catch ( IOException e1 ) {
					e1.printStackTrace();
				}
			}
		} ) );
	}
	
	@Override
	public void finishedBatches( List<App> all ) {
		super.finishedBatches( all );
	}
	

	private static class Meta {
		
		FRect r; // the HasApp
		DRectangle mask;
		DoorTexApp app;
		
		private Meta( DoorTexApp a, FRect r, DRectangle mask ) {
			this.r = r;
			this.mask = mask;
			this.app = a;
		}
	}

	public Enum[] getValidAppModes() {
		return new Enum[] { AppMode.Manual, AppMode.Net };
	}
}
