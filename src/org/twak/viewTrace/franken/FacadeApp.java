package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
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

public class FacadeApp extends App {

	public FacadeSuper zuper = new FacadeSuper(this);
	public SuperFace parent;
	public String coarse;
	
	public FacadeApp( HasApp ha ) {
		super( ha, "facade coarse", "bike_2", 8, 256 );
	}

	public FacadeApp( FacadeApp facadeCoarse ) {
		super( facadeCoarse );
		this.zuper = facadeCoarse.zuper;
	}

	@Override
	public App getUp() {
		return parent.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MiniFacade mf = (MiniFacade)hasA;
		
		MultiMap<String, App> out = new MultiMap<>();
		
		if (mf.postState != null)
		for (FRect r : mf.postState.generatedWindows)
			out.put( "window", r.app );
		
		out.put( "super", zuper );
		
		return out;
	}

	@Override
	public App copy() {
		return new FacadeApp( this );
	}

	final static Map<Color, Color> specLookup = new HashMap<>();
	static {
		specLookup.put( CMPLabel.Window.rgb, Color.white );
		specLookup.put( CMPLabel.Shop.rgb  , Color.darkGray );
		specLookup.put( CMPLabel.Door.rgb  , Color.gray );
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
				mf.featureGen = new FeatureGenerator( mf, mf.featureGen );

			DRectangle bounds = new DRectangle( resolution, 0, resolution, resolution );
			DRectangle mini = Pix2Pix.findBounds( mf );

			g.setColor( Color.black );
			g.fillRect( resolution, 0, resolution, resolution );

			mini = mf.postState == null ? mf.getAsRect() : mf.postState.outerFacadeRect;

			DRectangle mask = new DRectangle( mini );
//			mask = mask.centerSquare();

			{
				mask = mask.scale( resolution / Math.max( mini.height, mini.width ) );
				mask.x = ( resolution - mask.width ) * 0.5 + resolution;
				mask.y = 0; 
			}

			g.setColor( CMPLabel.Facade.rgb );

			if ( mf.postState == null ) {
				cmpRects( mf, g, mask, mini, CMPLabel.Facade.rgb, Collections.singletonList( new FRect( mini, mf ) ) );
			} else {
				for ( Loop<? extends Point2d> l : mf.postState.skelFaces )
					g.fill( toPoly( mf, mask, mini, l ) );

				g.setColor( CMPLabel.Background.rgb );
				for ( LoopL<Point2d> ll : mf.postState.occluders )
					for ( Loop<Point2d> l : ll )
						g.fill( toPoly( mf, mask, mini, l ) );
			}

			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Window .rgb, toEdit.featureGen.getRects( Feature.DOOR  ) );
			cmpRects( mf, g, mask, mini, CMPLabel.Window.rgb, mf.featureGen.getRects( Feature.WINDOW ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Molding.rgb, toEdit.featureGen.getRects( Feature.MOULDING ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Cornice.rgb, toEdit.featureGen.getRects( Feature.CORNICE  ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Sill   .rgb, toEdit.featureGen.getRects( Feature.SILL     ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Shop   .rgb, toEdit.featureGen.getRects( Feature.SHOP     ) );

			mask.x -= resolution;

//			String name = System.nanoTime() + "@" + index.size();

			Meta meta = new Meta( mf, name, mask );
//			index.put( mf, meta );

			p2.addInput( bi, meta, mf.app.styleZ );
		}

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

	private static Polygon toPoly( MiniFacade toEdit, DRectangle bounds, DRectangle mini, Loop<? extends Point2d> loop ) {
		Polygon p = new Polygon();

		for ( Point2d pt : loop ) {
			Point2d p2 = bounds.scale( mini.normalize( pt ) );
			p.addPoint( (int) p2.x, (int) ( -p2.y + 256 ) );
		}
		return p;
	}

	public static void cmpRects( MiniFacade toEdit, Graphics2D g, DRectangle bounds, DRectangle mini, Color col, List<FRect> rects ) {

		//		double scale = 1/ ( mini.width < mini.height ? mini.height : mini.width );
		//		
		//		mini = new DRectangle(mini);
		//		mini.scale( scale );
		//		
		//		mini.x = (1-mini.width) / 2;
		//		mini.y = (1-mini.height) / 2;

		for ( FRect r : rects ) {

			if ( mini.contains( r ) && toEdit.postState.generatedWindows.contains( r ) ) {
				
				DRectangle w = bounds.scale( mini.normalize( r ) );

				w.y = 256 - w.y - w.height;

				g.setColor( col );
				g.fillRect( (int) w.x, (int) w.y, (int) w.width, (int) w.height );
			}
		}
	}

	private static class Meta {
		String name;
		DRectangle mask;
		MiniFacade mf;
		
		private Meta( MiniFacade mf, String name, DRectangle mask ) {
			this.name = name;
			this.mask = mask;
			this.mf = mf;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Color, AppMode.Net};
	}
}
