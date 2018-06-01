package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.utils.Mathz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.CMPLabel;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Regularizer;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;

public class FacadeGreebleApp extends App implements HasApp {
	
	private FacadeTexApp parent;

	public double regFrac = 0.1, regAlpha = 0.3, regScale = 0.4;

	
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
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add ( new AutoDoubleSlider( this, "regFrac", "reg %", 0, 1 ) {
			public void updated(double value) {
				
				for (App a : apps)
					((FacadeGreebleApp)a).regFrac = value;
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		out.add ( new AutoDoubleSlider( this, "regAlpha", "reg alpha", 0, 1 ) {
			public void updated(double value) {
				for (App a : apps)
					((FacadeGreebleApp)a).regAlpha = value;
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		out.add ( new AutoDoubleSlider( this, "regScale", "reg scale", 0, 1 ) {
			public void updated(double value) {
				for (App a : apps)
					((FacadeGreebleApp)a).regScale = value;
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		return out;
	}

	final static Feature[] toGenerate = new Feature[] {
			
			Feature.BALCONY,
			Feature.CORNICE,
			Feature.DOOR,
			Feature.SHOP,
			Feature.SILL,
			Feature.DOOR,
			Feature.MOULDING,
	}; 
	
	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {

		NetInfo ni = NetInfo.get(this) ;
		int resolution = ni.resolution;
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage 
			labels = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR ),
			empty  = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		
		Graphics2D 
			gL = labels.createGraphics(),
			gE = empty .createGraphics();


		for (App a : batch) {
			
			try {
				BufferedImage rgb    = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
				Graphics2D gR = rgb   .createGraphics();

				FacadeGreebleApp fga = (FacadeGreebleApp) a;
				MiniFacade mf = (MiniFacade) fga.parent.hasA;

				mf.featureGen.removeAll( toGenerate );

				gR.setColor( CMPLabel.Background.rgb );
				gR.fillRect( 0, 0, resolution, resolution );

				gL.setColor( CMPLabel.Background.rgb );
				gL.fillRect( 0, 0, resolution, resolution );

				gE.setColor( CMPLabel.Background.rgb );
				gE.fillRect( 0, 0, resolution, resolution );

				DRectangle mini = Pix2Pix.findBounds( mf, false );

				DRectangle maskLabel = new DRectangle( mini );

				double scale = resolution / Math.max( mini.height, mini.width );

				{
					maskLabel = maskLabel.scale( scale );
					maskLabel.x = ( resolution - maskLabel.width ) * 0.5;
					maskLabel.y = 0;
				}

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( fga.parent.coarse ) );
				gR.drawImage( src, (int) maskLabel.x, (int) maskLabel.y, (int) maskLabel.width, (int) maskLabel.height, null );

				Pix2Pix.drawFacadeBoundary( gL, mf, mini, maskLabel, false );
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, new ArrayList<>( mf.postState.generatedWindows ) );

				Pix2Pix.drawFacadeBoundary( gE, mf, mini, maskLabel, false );

				Meta meta = new Meta( mf, maskLabel, mini, rgb );

				p2.addInput( rgb, empty, labels, meta, new double[0], FacadeLabelApp.FLOOR_HEIGHT * scale / 255. );

				gR.dispose();
				
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
		
		gL.dispose();
		gE.dispose();

		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						Pix2Pix.importTexture( e.getValue(), -1, null, meta.mask, null, new BufferedImage[3] );

						File boxFile = new File (e.getValue().getParentFile(), e.getValue().getName()+"_boxes" );
						
						Files.copy( boxFile, new File( Tweed.SCRATCH + "/" + UUID.randomUUID() + "_boxes.txt" ) );
						
						importLabels(meta, boxFile );
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

//				m.mf.featureGen = new FeatureGenerator( m.mf );

				root = om.readTree(FileUtils.readFileToString( file ) );

				for (Feature f : toGenerate) { // map all back to windows
					
					List<FRect> rects = m.mf.featureGen.getRects( f );
					
					if (f == Feature.SHOP || f == Feature.DOOR)
					{
						for (FRect r : rects) {
							m.mf.featureGen.map.remove( f, r );
							m.mf.featureGen.add( Feature.WINDOW, r );
						}
					}
					else for (FRect r : rects) 
						m.mf.featureGen.map.remove( f, r );
				}
				
				for ( Feature f : toGenerate ) {

					JsonNode node = root.get( f.name().toLowerCase().replace ("moulding", "molding") );

					if ( node == null )
						continue;

					List<DRectangle> frects = new ArrayList<>(), balconies = new ArrayList<>();
					
					for ( int i = 0; i < node.size(); i++ ) {

						JsonNode rect = node.get( i );

						DRectangle fr = new DRectangle( rect.get( 0 ).asDouble(),
								NetInfo.get( this ).resolution - rect.get( 3 ).asDouble(), rect.get( 1 ).asDouble() - rect.get( 0 ).asDouble(), 
								rect.get( 3 ).asDouble() - rect.get( 2 ).asDouble() );
						
						fr = m.mfBounds.transform( m.mask.normalize( fr ) );
						frects.add( fr );
					}

					if ( f != Feature.SHOP && f != Feature.DOOR )

						for ( DRectangle r : frects ) {
							m.mf.featureGen.add( f, r );
						}
					else

					if ( f == Feature.SHOP || f == Feature.DOOR )
						for ( DRectangle r : frects ) {

							Iterator<FRect> fit = m.mf.featureGen.get( Feature.WINDOW ).iterator();

							while ( fit.hasNext() ) {
								
								FRect w = fit.next();
								if ( w.intersects( r ) )
									if ( Math.abs( w.area() - r.area() ) < w.area() / 2 ) {
										// don't change the size
										r = new FRect( w, false );
										
										m.mf.featureGen.add( f, r );
										
										fit.remove();
										break;
									}
							}
						}
				}

				Regularizer r = new Regularizer();

				r.toReg = new Feature[] {};
				r.toReg2 = new Feature[] { Feature.WINDOW, Feature.SHOP, Feature.DOOR, Feature.MOULDING };

				r.alpha = regAlpha;
				r.scale = regScale;

				m.mf.featureGen = r.go( Collections.singletonList( m.mf ), 1, null ).get( 0 ).featureGen;

				
				for ( FRect win : m.mf.featureGen.getRects( Feature.SHOP, Feature.WINDOW ) ) {

					for ( Feature ff : win.attachedHeight.cache.keySet() ) {

						DRectangle feat = new DRectangle( win.x, win.y, win.width, win.attachedHeight.get( ff ).d );

						if (feat.height == 0)
							continue;
						
						Color c = null;
						
						switch ( ff ) {
						case BALCONY:

							if (feat.height < 0.2)
								continue;
							
							feat.height = Mathz.clamp( feat.height, 0.5, 1.5 );
							double d = Mathz.clamp( win.width / 6, 0.1, 0.5 );

							feat.width += d * 2;
							feat.x -= d;
							
							DRectangle bounds = new DRectangle(m.rgb.getWidth(), m.rgb.getHeight()).transform( m.mfBounds.normalize( feat ) );
							
							c = mean( m.rgb, Collections.singletonList( bounds ) );

							break;

						case SILL:

							feat.height = Mathz.clamp( feat.height, 0.1, 0.5 );
							
							feat.y -= feat.height;

							feat.width += 0.1;
							feat.x -= 0.05;

							break;

						case CORNICE:

							feat.height = Mathz.clamp( feat.height, 0.1, 0.5 );
							
							feat.y += win.height;

							feat.width += 0.1;
							feat.x -= 0.05;

							break;
						default:
						}

						if ( feat.height > 0 ) {
							FRect f = m.mf.featureGen.add( ff, feat );
							if (c != null)
								f.app.color = c;
						}
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
		private BufferedImage rgb;
		
		
		private Meta( MiniFacade mf, DRectangle mask, DRectangle mfBounds, BufferedImage rgb ) {
			this.mask = mask;
			this.mf = mf;
			this.mfBounds = mfBounds;
			this.rgb = rgb;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
	

	public static Color mean( BufferedImage rgb, List<? extends DRectangle> frects ) {
		
		int count = 0;
		long[] values = new long[3];
		
		
		for (DRectangle f : frects)
			for (int x = (int) f.x; x < f.x + f.width; x++)
				for (int y = rgb.getHeight() - (int)  (f.y + f.height); y < rgb.getHeight() - (f.y ); y++) 
				{
					int val = rgb.getRGB( x, y );
					values[0] += ( val & 0xff0000 ) >> 16;
					values[1] += ( val & 0x00ff00 ) >> 8;
					values[2] += ( val & 0x0000ff ) >> 0;
        			count++;
				}
		
		if (count > 0) {
			values[0] /= count;
			values[1] /= count;
			values[2] /= count;
		}
		
		return new Color( (int) ( (values[0] <<16) + (values[1] <<8) + values[2]  ) );
		
	}
}
