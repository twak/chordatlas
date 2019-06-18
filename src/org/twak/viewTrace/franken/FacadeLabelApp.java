package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;
import org.twak.mmg.MOgram;
import org.twak.mmg.media.GreebleMMG;
import org.twak.mmg.ui.MOgramEditor;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.skel.FacadeDesigner;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.auto.Auto;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MMGFeatureGen;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Regularizer;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FacadeLabelApp extends App {

	public static final double FLOOR_HEIGHT = 2.5;
	public double regFrac = 0.1, regAlpha = 0.3, regScale = 0.4;
	
	public double scale = 1;
	
	public MiniFacade mf;
	public String     texture;
	public MOgram     mogram;
	
	public boolean 
			debugLabels = false, 
			showRawLabels = false;
	
	public boolean disableDormers = false;
	
	private FacadeLabelApp() {}
	
	public FacadeLabelApp( MiniFacade mf ) {
		super( );
		this.mf = mf;
		
	}

	public FacadeLabelApp( FacadeLabelApp o ) {
		super( o );
		this.mf = o.mf;
		this.regFrac   = o.regFrac;
		this.regAlpha  = o.regAlpha;
		this.regScale  = o.regScale;
		
		this.texture = o.texture;
		
	}

	@Override
	public App getUp() {
		return mf.sf.buildingApp;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> out = new MultiMap<>();
		
		out.put( "facade texture", mf.facadeTexApp );
		
		return out;
	}

	@Override
	public App copy() {
		return new FacadeLabelApp( this );
	}

	@Override
	public JComponent createUI( GlobalUpdate globalUpdate, SelectedApps apps ) {
		JPanel out = new JPanel(new ListDownLayout());
		
		
		if ( appMode == TextureMode.Off ) {

			JButton fac = new JButton( "edit facade" );
			fac.addActionListener( e -> new FacadeDesigner( mf, globalUpdate ) );
			out.add( fac );
			
			AutoDoubleSlider gfh = new AutoDoubleSlider( mf, "groundFloorHeight", "ground floor", 0, 5 ) {
				public void updated(double value) {
					FacadeDesigner.close();
					for (App a : apps) {
						((FacadeLabelApp)a).mf.groundFloorHeight = value;
						globalUpdate.run();
					}
				};
			};
			
		}
		else if (appMode == TextureMode.MMG ) {
			
			JButton edit = new JButton("edit mogram");
			edit.addActionListener( new ActionListener() {
				
				@Override
				public void actionPerformed( ActionEvent e ) {
					if (mogram == null)
						mogram = GreebleMMG.createMOgram(mf);
					
					MOgramEditor me = new MOgramEditor( mogram );
					MOgramEditor.quitOnLastClosed = false;
					me.setVisible( true );		
					
				}
			} );
			out.add( edit );
			
		}
		else if (appMode == TextureMode.Procedural ) {
			
			if ( ! ( mf.featureGen instanceof CGAMini ) )
				mf.featureGen = new CGAMini( mf );
			
			CGAMini cga = (CGAMini) mf.featureGen;
			
			out.add( new Auto( cga, true ) {
				
				public void updateOkayCancel() {
					super.updateOkayCancel();
					apply();
					globalUpdate.run();
				};
			} .build() ); 
			
		} else if ( appMode == TextureMode.Net ) {

			out.add( new AutoDoubleSlider( this, "scale", "scale", 0.01, 3 ) {
				public void updated( double value ) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).scale = scale;
					globalUpdate.run();
				};
			}.notWhileDragging() );
			
			out.add( new AutoDoubleSlider( this, "regFrac", "reg %", 0, 1 ) {
				public void updated( double value ) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).regFrac = value;
					globalUpdate.run();
				};
			}.notWhileDragging() );

			out.add( new AutoDoubleSlider( this, "regAlpha", "reg alpha", 0, 1 ) {
				public void updated( double value ) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).regAlpha = value;
					globalUpdate.run();
				};
			}.notWhileDragging() );
			

			out.add( new AutoDoubleSlider( this, "regScale", "reg scale", 0, 1 ) {
				public void updated( double value ) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).regScale = value;
					globalUpdate.run();
				};
			}.notWhileDragging() );
			
			out.add (new AutoCheckbox( this, "debugLabels", "debug labels" ) {
				public void updated(boolean selected) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).debugLabels = selected;
					globalUpdate.run();
				};
			});
			
			out.add (new AutoCheckbox( this, "showRawLabels", "debug raw labels" ) {
				public void updated(boolean selected) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).showRawLabels = selected;
					globalUpdate.run();
				};
			});
			
			out.add (new AutoCheckbox( this, "disableDormers", "debug disable dormers" ) {
				public void updated(boolean selected) {
					for ( App a : apps )
						( (FacadeLabelApp) a ).disableDormers = selected;
					globalUpdate.run();
				};
			});
			
		}
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {

		if ( appMode != TextureMode.Net ) {
			for ( App a : batch ) {

				MiniFacade mf = ( (FacadeLabelApp) a ).mf;

				if ( appMode == TextureMode.Off ) {
					if ( !( mf.featureGen.getClass() == FeatureGenerator.class ) )
						mf.featureGen = new FeatureGenerator( mf.featureGen );
				} else if ( appMode == TextureMode.Procedural ) {
					if ( !( mf.featureGen.getClass() == CGAMini.class ) )
						mf.featureGen = new CGAMini( mf );
				} else if ( appMode == TextureMode.MMG ) {
					mf.featureGen = new MMGFeatureGen( mogram );
				}
			}

			whenDone.run();
			return;
		}
		
		
		NetInfo ni = NetInfo.get(this);
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage bi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

//		List<MiniFacade> mfb = batch.stream().map( x -> (MiniFacade)x.hasA ).collect( Collectors.toList() );

		for (App a : batch) {

			FacadeLabelApp fla = ((FacadeLabelApp)a);
			MiniFacade amf = fla.mf;
			BuildingApp ba = amf.sf.buildingApp;
			
			boolean dormers =  ba.createDormers && !fla.disableDormers;
			
			DRectangle mini = Pix2Pix.findBounds( amf, dormers), facadeOnly = Pix2Pix.findBounds( amf,  false );

			if (mini.area() < 0.1)
				continue;

			amf.groundFloorHeight = 0;
			
			g.setColor( Color.black );
			g.fillRect( 0, 0, ni.resolution, ni.resolution );

			DRectangle mask = new DRectangle( mini );
			

			double scale = ni.resolution / Math.max( mini.height, mini.width );
			
			{
				mask = mask.scale( scale );
				mask.x = ( ni.resolution - mask.width ) * 0.5;
				mask.y = 0; 
				
				facadeOnly = facadeOnly.scale( scale );
				facadeOnly.x = 0;
				facadeOnly.y = 0;
			}

			Pix2Pix.drawFacadeBoundary( g, amf, mini, mask, dormers );

			Meta meta = new Meta( amf, mask, facadeOnly, mini );

			p2.addInput( bi, bi, null, meta, amf.facadeLabelApp.styleZ, fla.scale * FLOOR_HEIGHT * scale / 255.  );
		}

		g.dispose();
		
		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				String dest;
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						importLabels( meta, new File (e.getValue().getParentFile(), e.getValue().getName()+"_boxes" ) );
						
						dest = Pix2Pix.importTexture( e.getValue(), -1, null, meta.mask, null, new BufferedImage[3] );

						
						if ( dest != null ) 
							 meta.mf.facadeLabelApp.texture = dest; // todo: set on FacadeTexApp after cropping for dormers
						
						if (debugLabels) {
							
//							meta.mf.featureGen = new FeatureGenerator();
							
							if (showRawLabels) {
							
//								String dF = Pix2Pix.importTexture( e.getValue(), -1, null, meta.facadeOnly, null, new BufferedImage[3] );
								meta.mf.facadeTexApp.texture = dest;
							}
							else {
//								List<FRect> renderedWindows = mf.featureGen.getRects( Feature.WINDOW ).stream().filter( r -> r.panesLabelApp.renderedOnFacade ).collect( Collectors.toList() );
								
								NetInfo ni = NetInfo.get(FacadeLabelApp.this.getClass());
								
								BufferedImage regularized = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
								Graphics2D gL = regularized.createGraphics();
								
								gL.setColor( Color.blue );
								gL.fillRect( 0, 0, regularized.getWidth(), regularized.getHeight() );
								
								Pix2Pix.cmpRects( meta.mf, gL, 
										new DRectangle(ni.resolution, ni.resolution), meta.mfBounds, Color.green, 
										meta.mf.featureGen.getRects( Feature.WINDOW ), getNetInfo().resolution );

								gL.dispose();
								
								String rawLabelFile = "scratch/"+UUID.randomUUID()+".png";
								
								ImageIO.write( regularized, "png", new File( Tweed.DATA + "/" + rawLabelFile ) );
								meta.mf.facadeTexApp.texture = rawLabelFile;
							}
							
							meta.mf.featureGen = new FeatureGenerator();
						}
					}
					
					System.out.println("done here");
					
				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}

		} ) );
	}

    private final static ObjectMapper om = new ObjectMapper();

    //{"other": [[196, 255, 0, 255], [0, 62, 0, 255]], 
    //"wall": [[62, 196, 0, 255]], 
    // "window": [[128, 192, 239, 255], [65, 114, 239, 255], [67, 113, 196, 217], [133, 191, 194, 217], [132, 185, 144, 161], [67, 107, 144, 161], [175, 183, 104, 118], [131, 171, 103, 120], [68, 105, 101, 119]]}
	private void importLabels( Meta m, File file ) {
		
		FeatureGenerator oldMF = m.mf.featureGen;
		
		if (file.exists()) {
			
			JsonNode root;
			try {
				
				
				
				m.mf.featureGen = new FeatureGenerator( m.mf );
				
				root = om.readTree( FileUtils.readFileToString( file ) );
				JsonNode node = root.get( "window" );
				
				for (int i = 0; i < node.size(); i++) {
					
					JsonNode rect = node.get( i );
					
					DRectangle f = new DRectangle( rect.get( 0 ).asDouble(), NetInfo.get(this).resolution - rect.get( 3 ).asDouble(),
							rect.get( 1 ).asDouble() - rect.get( 0 ).asDouble(),
							rect.get( 3 ).asDouble() - rect.get( 2 ).asDouble() );
							
					
					f = m.mfBounds.transform ( m.mask.normalize( f ) );
					
					{ // move away from edges
						double gap = 0.1;
						if (f.x < m.mfBounds.x + gap )
							f.x += gap;
						else
						if (f.x + f.width > m.mfBounds.getMaxX() - gap)
							f.x -= gap;
					}
					
					m.mf.featureGen.add( Feature.WINDOW, f );
				}
				
				if ( m.mf.facadeLabelApp.regFrac > 0) {
					
					Regularizer reg = new Regularizer();
					
					reg.alpha = m.mf.facadeLabelApp.regAlpha;
					reg.scale = m.mf.facadeLabelApp.regScale;
					
					m.mf.featureGen = reg.go(Collections.singletonList( m.mf ), m.mf.facadeLabelApp.regFrac, null ).get( 0 ).featureGen;
					m.mf.featureGen.setMF(m.mf);
				}
				
				for (FRect window : m.mf.featureGen.getRects( Feature.WINDOW, Feature.SHOP, Feature.DOOR )) {
					
						FRect nearestOld = closest( window, oldMF.getRects( Feature.WINDOW, Feature.SHOP, Feature.DOOR ) );
						if ( nearestOld != null ) {
							
							window.panesLabelApp = new PanesLabelApp( nearestOld.panesLabelApp );
							window.panesLabelApp.fr = window;
							

							window.panesTexApp = (PanesTexApp) nearestOld.panesTexApp.copy();
							window.panesTexApp.fr = window;
						}
				}
				
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
	}

	private FRect closest( FRect window, List<FRect> oldWindows ) {
		
		double bestDist = 1.2;
		FRect bestWin = null;
		if (oldWindows != null)
		for ( FRect r : oldWindows ) {
			double dist = window.getCenter().distanceSquared( r.getCenter() );

			if ( dist < bestDist ) {
				bestDist = dist;
				bestWin = r;
			}
		}
		
		return bestWin;
	}

	private static class Meta {
		DRectangle mask, mfBounds, facadeOnly;
		MiniFacade mf;
		
		private Meta( MiniFacade mf, DRectangle mask, DRectangle facadeOnly, DRectangle mfBounds ) {
			this.mask = mask;
			this.facadeOnly = facadeOnly;
			this.mf = mf;
			this.mfBounds = mfBounds;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {TextureMode.Off /* manual */, TextureMode.Procedural, TextureMode.Net, TextureMode.MMG };
	}
	
	@Override
	public void finishedBatches( List<App> all ) {

		super.finishedBatches( all );
		
		for (App a : all) {
			
			FacadeLabelApp fla = ( (FacadeLabelApp) a );
			MiniFacade mf = fla.mf;
			FacadeTexApp fta = mf.facadeTexApp;

			fta.oldWindows = new ArrayList<FRect>( mf.featureGen.getRects( Feature.WINDOW ) );
			
			// compute dormer-roof locations
			new GreebleSkel( null, mf.sf ).showSkeleton( mf.sf.skel.output, null, mf.sf.mr );
		}
	}
}
