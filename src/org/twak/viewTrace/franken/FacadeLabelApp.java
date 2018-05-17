package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;


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
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class FacadeLabelApp extends App {

	public SuperFace superFace;
	public String coarse;
	
	public FacadeLabelApp( HasApp ha ) {
		super( ha, "facade labels", "blank", 8, 256 );
	}

	public FacadeLabelApp( FacadeLabelApp facadeCoarse ) {
		super( facadeCoarse );
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
	public void computeBatch(Runnable whenDone, List<App> batch) {

		Pix2Pix p2 = new Pix2Pix( batch.get( 0 ) );
		
		BufferedImage bi = new BufferedImage( resolution * 2, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

//		Map<MiniFacade, Meta> index = new HashMap<>();
		
		List<MiniFacade> mfb = batch.stream().map( x -> (MiniFacade)x.hasA ).collect( Collectors.toList() );

		for ( MiniFacade mf : mfb ) {
			
			if (mf.featureGen instanceof CGAMini)
				mf.featureGen = new FeatureGenerator( mf );

			DRectangle mini = Pix2Pix.findBounds( mf );

			g.setColor( Color.black );
			g.fillRect( resolution, 0, resolution, resolution );

			mini = mf.postState == null ? mf.getAsRect() : mf.postState.outerFacadeRect;

			DRectangle mask = new DRectangle( mini );

			{
				mask = mask.scale( resolution / Math.max( mini.height, mini.width ) );
				mask.x = ( resolution - mask.width ) * 0.5 + resolution;
				mask.y = 0; 
			}


			if ( mf.postState == null ) {
				Pix2Pix.cmpRects( mf, g, mask, mini, Color.blue, Collections.singletonList( new FRect( mini, mf ) ) );
			} else {
				g.setColor( Color.blue );
				
				for ( Loop<? extends Point2d> l : mf.postState.skelFaces )
					g.fill( Pix2Pix.toPoly( mf, mask, mini, l ) );

				g.setColor( CMPLabel.Background.rgb );
				for ( LoopL<Point2d> ll : mf.postState.occluders )
					for ( Loop<Point2d> l : ll )
						g.fill( Pix2Pix.toPoly( mf, mask, mini, l ) );
			}

			mask.x -= resolution;

			Meta meta = new Meta( mf, mask );

			p2.addInput( bi, meta, mf.app.styleZ );
		}

		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				String dest;
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						importLabels(meta.mf, new File (e.getValue().getParentFile(), e.getValue().getName()+"_boxes" ) );
						
						dest = Pix2Pix.importTexture( e.getValue(), -1, null, meta.mask );

						if ( dest != null ) {
							meta.mf.appLabel.texture = meta.mf.app.texture = dest;
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}

		} ) );
	}
	
	
	//{"other": [[196, 255, 0, 255], [0, 62, 0, 255]], 
	//"wall": [[62, 196, 0, 255]], 
	// "window": [[128, 192, 239, 255], [65, 114, 239, 255], [67, 113, 196, 217], [133, 191, 194, 217], [132, 185, 144, 161], [67, 107, 144, 161], [175, 183, 104, 118], [131, 171, 103, 120], [68, 105, 101, 119]]}
	private void importLabels( MiniFacade mf, File file ) {
		
		if (file.exists()) {
			
//			JsonObject jObj = new JsonObject(Files.readAllLines( file.toPath() ).get( arg0 )[0]);
			
		}
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
		return new Enum[] {AppMode.Color, AppMode.Net};
	}
}
