package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
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
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Pix2Pix.CMPLabel;
import org.twak.viewTrace.facades.Pix2Pix.Job;
import org.twak.viewTrace.facades.Pix2Pix.JobResult;

public class FacadeApp extends App {

	FacadeSuper zuper = new FacadeSuper(this);
	public SuperFace parent;
	
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
		specLookup.put( Pix2Pix.CMPLabel.Window.rgb, Color.white );
		specLookup.put( Pix2Pix.CMPLabel.Shop.rgb  , Color.darkGray );
		specLookup.put( Pix2Pix.CMPLabel.Door.rgb  , Color.gray );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {

		
		BufferedImage bi = new BufferedImage( resolution * 2, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

		Map<MiniFacade, Meta> index = new HashMap<>();
		
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

			{
				mask = mask.scale( 256 / Math.max( mini.height, mini.width ) );
				mask.x = ( resolution - mask.width ) * 0.5 + /*
																 * draw on the
																 * right of the
																 * input image
																 */ resolution;
				mask.y = 0; //( 256 - mask.height ) * 0.5;
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

			String name = System.nanoTime() + "@" + index.size();

			index.put( mf, new Meta( name, mask ) );

			Pix2Pix.addInput( bi, name, netName );
		}

		Pix2Pix.submit( new Job( netName, System.nanoTime() + "_" + zAsString(), new JobResult() {

			@Override
			public void finished( File f ) {

				boolean found = false;

				String dest;
				try {

					new File( Tweed.SCRATCH ).mkdirs();

					for ( Map.Entry<MiniFacade, Meta> e : index.entrySet() ) {

						dest = Pix2Pix.importTexture( f, e.getValue().name, -1, specLookup, e.getValue().mask );

						if ( dest != null ) {
							e.getKey().app.texture = dest;
							found = true;
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}

		} ) );
		//			whenDone );
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

			if ( mini.contains( r ) ) {

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

		private Meta( String name, DRectangle mask ) {
			this.name = name;
			this.mask = mask;
		}
	}
}
