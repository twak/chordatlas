package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;

import org.twak.tweed.gen.SuperFace;
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
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class FacadeTexApp extends App {

	public FacadeSuperApp zuper = new FacadeSuperApp(this);
	public SuperFace parent; // for non-label pipeline
	public String coarse;
	
	public FacadeTexApp( HasApp ha ) {
		super( ha );
	}

	public FacadeTexApp( FacadeTexApp facadeCoarse ) {
		super( facadeCoarse );
		this.zuper = facadeCoarse.zuper;
	}

	@Override
	public App getUp() {
		
		MiniFacade mf = (MiniFacade) hasA;
		
		return mf.appLabel;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MiniFacade mf = (MiniFacade)hasA;
		
		MultiMap<String, App> out = new MultiMap<>();
		
		if (mf.postState != null)
		for (FRect r : mf.featureGen.get( Feature.WINDOW ))
			out.put( "window", r.app );
		
		out.put( "super", zuper );
		
		return out;
	}

	@Override
	public App copy() {
		return new FacadeTexApp( this );
	}

	final static Map<Color, Color> specLookup = new HashMap<>();
	static {
		specLookup.put( CMPLabel.Window.rgb, Color.white );
		specLookup.put( CMPLabel.Shop.rgb  , Color.darkGray );
		specLookup.put( CMPLabel.Door.rgb  , Color.gray );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {

		NetInfo ni =NetInfo.get(this) ;
		int resolution = ni.resolution;
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage 
			labels = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR ),
			empty  = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		
		Graphics2D gL = labels.createGraphics(),
				gE = empty.createGraphics();

//		Map<MiniFacade, Meta> index = new HashMap<>();
		
		List<MiniFacade> mfb = batch.stream().map( x -> (MiniFacade)x.hasA ).collect( Collectors.toList() );

		for ( MiniFacade mf : mfb ) {
			
			if (mf.featureGen instanceof CGAMini)
				mf.featureGen = new FeatureGenerator( mf, mf.featureGen );

			DRectangle mini = Pix2Pix.findBounds( mf );

			gL.setColor( CMPLabel.Background.rgb );
			gL.fillRect( 0, 0, resolution, resolution );
			gE.setColor( CMPLabel.Background.rgb );
			gE.fillRect( 0, 0, resolution, resolution );

			mini = mf.postState == null ? mf.getAsRect() : mf.postState.outerFacadeRect;

			if (mini == null)
				continue;
			
			DRectangle maskLabel = new DRectangle( mini );
//			mask = mask.centerSquare();

			double scale = resolution / Math.max( mini.height, mini.width );
			
			{
				maskLabel = maskLabel.scale( scale );
				maskLabel.x = ( resolution - maskLabel.width ) * 0.5;
				maskLabel.y = 0; 
			}
			
//			DRectangle maskEmpty = new DRectangle(maskLabel);
//			maskEmpty.x -= resolution;

			

			if ( mf.postState == null ) {
				
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Facade.rgb, Collections.singletonList( new FRect( mini, mf ) ) );
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, mf.featureGen.getRects( Feature.WINDOW ) );
				
			} else {
				
				gL.setColor( CMPLabel.Facade.rgb );
				gE.setColor( CMPLabel.Facade.rgb );
				
				for ( Loop<? extends Point2d> l : mf.postState.skelFaces ) {
					gL.fill( Pix2Pix.toPoly( mf, maskLabel, mini, l ) );
					gE.fill( Pix2Pix.toPoly( mf, maskLabel, mini, l ) );
				}

				gE.setColor( CMPLabel.Background.rgb );
				
				for ( LoopL<Point2d> ll : mf.postState.occluders )
					for ( Loop<Point2d> l : ll ) {
						gL.fill( Pix2Pix.toPoly( mf, maskLabel, mini, l ) );
						gE.fill( Pix2Pix.toPoly( mf, maskLabel, mini, l ) );
					}
				
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, new ArrayList<>( mf.postState.generatedWindows ) );// featureGen.getRects( Feature.WINDOW ) );
			}

			Meta meta = new Meta( mf, maskLabel );

			p2.addInput( labels, empty, null, meta, mf.app.styleZ,  FacadeLabelApp.FLOOR_HEIGHT * scale / 255. );
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
						
						dest = Pix2Pix.importTexture( e.getValue(), -1, specLookup, meta.mask );

						if ( dest != null ) {
							meta.mf.app.coarse = meta.mf.app.texture = dest;
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}
		} ) );
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
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
}
