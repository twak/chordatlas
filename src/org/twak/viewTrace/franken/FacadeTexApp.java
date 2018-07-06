package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.Imagez;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.Colourz;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.CMPLabel;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.PostProcessState;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class FacadeTexApp extends App {

	public SuperFace parent; // for non-label pipeline
	public String coarse;
	public String coarseWithWindows;
	
	public ArrayList<FRect> oldWindows; // when we create windows, we take the styles from this list
	
	public MiniFacade mf;
	public String texture;
	
	private static Color defaultGFColor = new Color (188,156,255);
	
	public Color 
		color            =  Colourz.to4 ( GreebleSkel.BLANK_WALL ),
		groundFloorColor = defaultGFColor;
	public PostProcessState postState = null;


	public TextureUVs textureUVs = TextureUVs.Square;
	public DRectangle textureRect;
	
	public final static int CHIMNEY_PAD = 20;
	
	public FacadeTexApp( MiniFacade mf ) {
		super( );
		this.mf = mf;
		if (mf.wallColor != null)
			this.color = mf.wallColor;
	}

	public FacadeTexApp( FacadeTexApp fta ) {

		super( fta );
		this.parent = fta.parent;
		this.coarse = fta.coarse;
		this.coarseWithWindows = fta.coarseWithWindows;
		this.texture = fta.texture;
		this.textureUVs = fta.textureUVs;
		if (fta.textureRect != null)
			this.textureRect = new DRectangle(fta.textureRect);
	}

	@Override
	public App getUp() {
		return mf.facadeLabelApp;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> out = new MultiMap<>();
		
		for (FRect r : mf.featureGen.getRects( Feature.WINDOW, Feature.SHOP ) )
			if (r.panesLabelApp.renderedOnFacade)
				out.put( "window", r.panesLabelApp );
		
		out.put( "super", mf.facadeSuperApp );
		out.put( "greeble", mf.facadeGreebleApp ); 
		
		return out;
	}

	@Override
	public App copy() {
		return new FacadeTexApp( this );
	}

	public final static Map<Color, Color> specLookup = new HashMap<>();
	static {
		specLookup.put( CMPLabel.Window.rgb, new Color (180, 180, 180) );
		specLookup.put( CMPLabel.Shop.rgb  , Color.darkGray );
		specLookup.put( CMPLabel.Door.rgb  , Color.gray );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		if ( appMode != AppMode.Net ) {
			
			if (appMode == AppMode.Manual) {
				texture = null;
			}
			
			whenDone.run();
			return;
		}
		
		NetInfo ni =NetInfo.get(this) ;
		int resolution = ni.resolution;
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage 
			labels = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR ),
			empty  = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		
		Graphics2D gL = labels.createGraphics(),
				   gE = empty.createGraphics();

//		Map<MiniFacade, Meta> index = new HashMap<>();
		
//		List<MiniFacade> mfb = batch.stream().map( x -> ((FacadeTexApp)x).ha ).collect( Collectors.toList() );

		for (App a : batch) {
			
			FacadeTexApp fta = (FacadeTexApp )a;
			MiniFacade mf = fta.mf;
			
			if (!TweedSettings.settings.siteplanInteractiveTextures && mf.featureGen instanceof CGAMini) {
				mf.featureGen = new FeatureGenerator( mf, mf.featureGen );
			}

			DRectangle mini = Pix2Pix.findBounds( mf, false );

			mini = Pix2Pix.findBounds( mf, false );

			if (mini == null)
				continue;
			
			DRectangle maskLabel = new DRectangle( mini );
//			mask = mask.centerSquare();

			double scale = resolution / ( Math.max( mini.height, mini.width ) + 0.4);
			
			{
				maskLabel = maskLabel.scale( scale );
				maskLabel.x = ( resolution - maskLabel.width  ) * 0.5;
				maskLabel.y = ( resolution - maskLabel.height ) * 0.5;
			}
			
//			DRectangle maskEmpty = new DRectangle(maskLabel);
//			maskEmpty.x -= resolution;

			

			gL.setColor( CMPLabel.Background.rgb );
			gL.fillRect( 0, 0, resolution, resolution );
			
			gE.setColor( CMPLabel.Background.rgb );
			gE.fillRect( 0, 0, resolution, resolution );

			
			if ( fta.postState == null ) {
				
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Facade.rgb, Collections.singletonList( new FRect( mini, mf ) ) );
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, mf.featureGen.getRects( Feature.WINDOW ) );
				
			} else {
				
				gL.setColor( CMPLabel.Facade.rgb );
				gE.setColor( CMPLabel.Facade.rgb );
				
				Stroke stroke = new BasicStroke( 0.2f * (float) scale );
				
				gL.setStroke( stroke );
				gE.setStroke( stroke );
				
				for ( Loop<? extends Point2d> l : fta.postState.wallFaces ) {
					
					Polygon p = Pix2Pix.toPoly( mf, maskLabel, mini, l ) ; 
					
					gL.fill( p );
					gL.draw( p );
					gE.fill( p );
					gE.draw( p );
				}

				stroke = new BasicStroke( 2 );
				gL.setStroke( stroke );
				gE.setStroke( stroke );
				
				for ( Loop<Point2d> l : fta.postState.occluders ) {
						Polygon poly = Pix2Pix.toPoly( mf, maskLabel, mini, l );
						gL.setColor( CMPLabel.Background.rgb );
						gE.setColor( CMPLabel.Background.rgb );
						gL.fill( poly );
						gE.fill( poly );
						gL.setColor( CMPLabel.Facade.rgb );
						gE.setColor( CMPLabel.Facade.rgb );
						gL.draw( poly );
						gE.draw( poly );
					}

				List<FRect> renderedWindows = mf.featureGen.getRects( Feature.WINDOW ).stream().filter( r -> r.panesLabelApp.renderedOnFacade ).collect( Collectors.toList() );
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, new ArrayList<>( renderedWindows ) );
			}

			Meta meta = new Meta( mf, maskLabel );

			FacadeTexApp mfa = mf.facadeTexApp;
			
			p2.addInput( labels, empty, null, meta, mfa.styleZ,  FacadeLabelApp.FLOOR_HEIGHT * scale / 255. );
			
			if ( mfa.getChimneyTexture() == null) {
				Meta m2 = new Meta (mf, null);

				gL.setColor( CMPLabel.Background.rgb );
				gL.fillRect( 0, 0, resolution, resolution );
				
				gL.setColor( CMPLabel.Facade.rgb );
				gL.fillRect( CHIMNEY_PAD, CHIMNEY_PAD, resolution - 2*CHIMNEY_PAD, resolution - 2*CHIMNEY_PAD );
				
				
				p2.addInput( labels, empty, null, m2, mfa.styleZ,  0.3 );
				mfa.setChimneyTexture( "in progress" );
			}
		}
		
		
		gL.dispose();
		gE.dispose();

		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				String dest;
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						boolean isChimney = meta.mask == null;
						
						BufferedImage[] channels = new BufferedImage[3];
						dest = Pix2Pix.importTexture( e.getValue(), -1, specLookup, meta.mask, null, channels );

						
						FacadeTexApp fta = meta.mf.facadeTexApp;
						
						if ( dest != null ) {
							
							if (isChimney) {
								
								BufferedImage rgb = Imagez.clone( channels[0] );
								
								Graphics2D g = channels[0].createGraphics();
								g.drawImage( rgb, 
										0, 0, rgb.getWidth(), rgb.getHeight(),
										CHIMNEY_PAD*2, CHIMNEY_PAD*2, rgb.getWidth() - CHIMNEY_PAD*2, rgb.getHeight() - CHIMNEY_PAD*2, 
										null );
								
								ImageIO.write( channels[0], "png", new File (Tweed.DATA, dest ) );
								
								fta.setChimneyTexture( dest );
								
							} else {
								
								fta.coarse = fta.texture = dest;
								fta.coarseWithWindows = null;

								for ( FRect r : meta.mf.featureGen.getRects( Feature.WINDOW ) ) {
									PanesLabelApp pla = r.panesLabelApp;
									pla.texture = null;
									pla.panes = new ArrayList<>();
								}
							}
							
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}
		} ) );
	}
	
	public String getChimneyTexture() {
		return  mf.sf.buildingApp.chimneyTexture;
	}

	public void setChimneyTexture( String tex ) {
		 mf.sf.buildingApp.chimneyTexture = tex;
	}

	private static class Meta {
		DRectangle mask;
		MiniFacade mf;
		
		private Meta( MiniFacade mf, DRectangle mask ) {
			this.mask = mask;
			this.mf = mf;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Manual, AppMode.Bitmap, AppMode.Net};
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		if (appMode == AppMode.Manual) {
			
			JButton col = new JButton("main color");
			
			col.addActionListener( e -> new ColourPicker(null, color) {
				@Override
				public void picked( Color color ) {
					
					for (App a : apps)  {
						((FacadeTexApp)a).color = color;
						((FacadeTexApp)a).texture = null;
					}
					
					globalUpdate.run();
				}
			} );
			
			out.add( col );
			
			JButton gc = new JButton("ground floor color");
			
			gc.addActionListener( e -> new ColourPicker(null, color) {
				@Override
				public void picked( Color color ) {
					
					for (App a : apps)  {
						((FacadeTexApp)a).groundFloorColor = color;
						((FacadeTexApp)a).texture = null;
					}
					
					globalUpdate.run();
				}
			} );
			
			out.add( gc );
			
		}
		
		return out;
	}

	public void resetPostProcessState() {
		postState = new PostProcessState();
	}
}
