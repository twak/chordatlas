package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.utils.Filez;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.CMPLabel;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.Regularizer;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FacadeGreebleApp extends App implements HasApp {
	
	private FacadeTexApp parent;

	public FacadeGreebleApp( FacadeTexApp parent ) {
		super( (HasApp) null );
		this.hasA = this;
		this.parent = parent;
	}

	public FacadeGreebleApp( FacadeGreebleApp o ) {

		super( (App) o );
		
		this.parent = o.parent;
	}

	@Override
	public App copy() {
		return new FacadeGreebleApp( this );
	}

	@Override
	public App getUp() {
		return parent;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	final static Feature[] toGenerate = new Feature[] {
			
			Feature.BALCONY,
//			Feature.CORNICE,
			Feature.DOOR,
			Feature.SHOP,
			Feature.SILL,
//			Feature.MOULDING,
	}; 
	
	
	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {

		NetInfo ni = NetInfo.get(this) ;
		int resolution = ni.resolution;
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage 
			rgb    = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR ),
			labels = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR ),
			empty  = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		
		Graphics2D 
			gR = rgb   .createGraphics(),
			gL = labels.createGraphics(),
			gE = empty .createGraphics();


		for (App a : batch) {
			
			try {

				FacadeGreebleApp fga = (FacadeGreebleApp) a;
				MiniFacade mf = (MiniFacade) fga.parent.hasA;

				mf.featureGen.removeAll( toGenerate );

				gR.setColor( CMPLabel.Background.rgb );
				gR.fillRect( 0, 0, resolution, resolution );

				gL.setColor( CMPLabel.Background.rgb );
				gL.fillRect( 0, 0, resolution, resolution );

				gE.setColor( CMPLabel.Background.rgb );
				gE.fillRect( 0, 0, resolution, resolution );

				DRectangle mini = Pix2Pix.findBounds( mf );

				DRectangle maskLabel = new DRectangle( mini );

				double scale = resolution / Math.max( mini.height, mini.width );

				{
					maskLabel = maskLabel.scale( scale );
					maskLabel.x = ( resolution - maskLabel.width ) * 0.5;
					maskLabel.y = 0;
				}

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( fga.parent.coarse ) );
				gR.drawImage( src, (int) maskLabel.x, (int) maskLabel.y, (int) maskLabel.width, (int) maskLabel.height, null );

				Pix2Pix.drawFacadeBoundary( gL, mf, mini, maskLabel );
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, new ArrayList<>( mf.postState.generatedWindows ) );

				Pix2Pix.drawFacadeBoundary( gE, mf, mini, maskLabel );

				Meta meta = new Meta( mf, maskLabel, mini );

				p2.addInput( rgb, labels, empty, meta, new double[0], FacadeLabelApp.FLOOR_HEIGHT * scale / 255. );

			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
		
		gR.dispose();
		gL.dispose();
		gE.dispose();

		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
//						Pix2Pix.importTexture( e.getValue(), -1, null, meta.mask );

						importLabels(meta, new File (e.getValue().getParentFile(), e.getValue().getName()+"_boxes" ) );
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}
		} ) );
	}
    private final static ObjectMapper om = new ObjectMapper();

	private void importLabels( Meta m, File file ) {

		if ( file.exists() ) {

			JsonNode root;
			try {

				m.mf.featureGen = new FeatureGenerator( m.mf );

				root = om.readTree( FileUtils.readFileToString( file ) );

				for ( Feature f : toGenerate ) {

					JsonNode node = root.get( f.name().toLowerCase() );

					if ( node == null )
						continue;

					for ( int i = 0; i < node.size(); i++ ) {

						JsonNode rect = node.get( i );

						DRectangle r = new DRectangle( rect.get( 0 ).asDouble(), NetInfo.get( this ).resolution - rect.get( 3 ).asDouble(), rect.get( 1 ).asDouble() - rect.get( 0 ).asDouble(), rect.get( 3 ).asDouble() - rect.get( 2 ).asDouble() );

						m.mf.featureGen.add( f, m.mfBounds.transform( m.mask.normalize( r ) ) );

					}
				}

			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
	}

	private static class Meta {
		DRectangle mask, mfBounds;
		MiniFacade mf;
		
		private Meta( MiniFacade mf, DRectangle mask, DRectangle mfBounds ) {
			this.mask = mask;
			this.mf = mf;
			this.mfBounds = mfBounds;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
}
