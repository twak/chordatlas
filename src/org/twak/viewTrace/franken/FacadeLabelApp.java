package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.CMPLabel;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Regularizer;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FacadeLabelApp extends App {

	public static final double FLOOR_HEIGHT = 2.5;
	
	public SuperFace superFace;
	public double regFrac = 0.1, regAlpha = 0.3, regScale = 0.4;
	
	public FacadeLabelApp( HasApp ha ) {
		super( ha );
	}

	public FacadeLabelApp( FacadeLabelApp facadeCoarse ) {
		super( facadeCoarse );
		this.superFace = facadeCoarse.superFace;
		this.regFrac   = facadeCoarse.regFrac;
		this.regAlpha  = facadeCoarse.regAlpha;
		this.regScale  = facadeCoarse.regScale;
	}

	@Override
	public App getUp() {
		return superFace.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> out = new MultiMap<>();
		
		MiniFacade mf = (MiniFacade)hasA;
		out.put( "facade texture", mf.app );
		
		return out;
	}

	@Override
	public App copy() {
		return new FacadeLabelApp( this );
	}

	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add ( new AutoDoubleSlider( this, "regFrac", "reg %", 0, 1 ) {
			public void updated(double value) {
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		out.add ( new AutoDoubleSlider( this, "regAlpha", "reg alpha", 0, 1 ) {
			public void updated(double value) {
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		out.add ( new AutoDoubleSlider( this, "regScale", "reg scale", 0, 1 ) {
			public void updated(double value) {
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {

		NetInfo ni = NetInfo.get(this);
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage bi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

//		Map<MiniFacade, Meta> index = new HashMap<>();
		
		List<MiniFacade> mfb = batch.stream().map( x -> (MiniFacade)x.hasA ).collect( Collectors.toList() );

		for (App a : batch) {
//		for ( MiniFacade mf : mfb ) {

			MiniFacade mf = (MiniFacade) a.hasA;
			
			mf.postState.generatedWindows.clear();
			
			DRectangle mini = Pix2Pix.findBounds( mf );

			g.setColor( Color.black );
			g.fillRect( 0, 0, ni.resolution, ni.resolution );

			mini = mf.postState == null ? mf.getAsRect() : mf.postState.outerFacadeRect;

			DRectangle mask = new DRectangle( mini );

			double scale = ni.resolution / Math.max( mini.height, mini.width );
			
			{
				mask = mask.scale( scale );
				mask.x = ( ni.resolution - mask.width ) * 0.5;
				mask.y = 0; 
			}

			Pix2Pix.drawFacadeBoundary( g, mf, mini, mask );

			Meta meta = new Meta( mf, mask, mini, a );

			p2.addInput( bi, bi, null, meta, mf.app.styleZ, FLOOR_HEIGHT * scale / 255.  );
		}

		g.dispose();
		
		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				String dest;
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						importLabels(meta, new File (e.getValue().getParentFile(), e.getValue().getName()+"_boxes" ) );
						
						dest = Pix2Pix.importTexture( e.getValue(), -1, null, meta.mask, null );

						if ( dest != null ) 
							meta.mf.appLabel.texture = meta.mf.app.texture = dest;
					}
					
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
	private void importLabels( Meta m,  File file ) {
		
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
							
					FRect window = m.mf.featureGen.add( Feature.WINDOW, m.mfBounds.transform ( m.mask.normalize( f ) ) );
					
				}
				
				if (regFrac > 0) {
					Regularizer reg = new Regularizer();
					reg.alpha = regAlpha;
					reg.scale = regScale;
					m.mf.featureGen = reg.go(Collections.singletonList( m.mf ), regFrac, null ).get( 1 ).featureGen;
				}
				
				m.mf.postState.generatedWindows.addAll( m.mf.featureGen.get( Feature.WINDOW ) );
				
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
	}

	private static class Meta {
		DRectangle mask, mfBounds;
		MiniFacade mf;
		App a;
		
		private Meta( MiniFacade mf, DRectangle mask, DRectangle mfBounds, App a ) {
			this.mask = mask;
			this.mf = mf;
			this.mfBounds = mfBounds;
			this.a = a;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
}
