package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Pix2Pix.CMPLabel;
import org.twak.viewTrace.facades.Pix2Pix.Job;
import org.twak.viewTrace.facades.Pix2Pix.JobResult;

public class FacadeCoarse extends App {

	public FacadeCoarse( HasApp ha ) {
		super( ha, "bike_2", 8, 256 );
	}

	public FacadeCoarse( FacadeCoarse facadeCoarse ) {
		super( facadeCoarse );
	}

	@Override
	public App getUp() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
		return new FacadeCoarse( this );
	}

	@Override
	public void computeSelf( Runnable whenDone ) {

		BufferedImage bi = new BufferedImage( resolution * 2, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

		Map<MiniFacade, Meta> index = new HashMap<>();

		for ( MiniFacade toEdit : Collections.singletonList( (MiniFacade) app ) ) {

			DRectangle bounds = new DRectangle( resolution, 0, resolution, resolution );
			DRectangle mini = findBounds( toEdit );

			g.setColor( Color.black );
			g.fillRect( resolution, 0, resolution, resolution );

			mini = toEdit.postState == null ? toEdit.getAsRect() : toEdit.postState.outerFacadeRect;

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

			if ( toEdit.postState == null ) {
				cmpRects( toEdit, g, mask, mini, CMPLabel.Facade.rgb, Collections.singletonList( new FRect( mini ) ) );
			} else {
				for ( Loop<? extends Point2d> l : toEdit.postState.skelFaces )
					g.fill( toPoly( toEdit, mask, mini, l ) );

				g.setColor( CMPLabel.Background.rgb );
				for ( LoopL<Point2d> ll : toEdit.postState.occluders )
					for ( Loop<Point2d> l : ll )
						g.fill( toPoly( toEdit, mask, mini, l ) );
			}

			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Window .rgb, toEdit.featureGen.getRects( Feature.DOOR  ) );
			cmpRects( toEdit, g, mask, mini, CMPLabel.Window.rgb, toEdit.featureGen.getRects( Feature.WINDOW ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Molding.rgb, toEdit.featureGen.getRects( Feature.MOULDING ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Cornice.rgb, toEdit.featureGen.getRects( Feature.CORNICE  ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Sill   .rgb, toEdit.featureGen.getRects( Feature.SILL     ) );
			//			cmpRects( toEdit, g, mask, mini, CMPLabel.Shop   .rgb, toEdit.featureGen.getRects( Feature.SHOP     ) );

			mask.x -= resolution;

			String name = System.nanoTime() + "@" + index.size();

			index.put( toEdit, new Meta( name, mask ) );

			Pix2Pix.setInput( bi, name, netName );
		}

		Pix2Pix.submit( new Job( netName, System.nanoTime() + "_" + zAsString(), new JobResult() {

			@Override
			public void finished( File f ) {

				boolean found = false;

				String dest;
				try {

					List<MiniFacade> subfeatures = new ArrayList();

					new File( Tweed.SCRATCH ).mkdirs();

					for ( Map.Entry<MiniFacade, Meta> e : index.entrySet() ) {

						dest = Pix2Pix.importTexture( f, e.getValue().name, -1, e.getValue().mask );

						if ( dest != null ) {
							e.getKey().app.texture = dest;
							subfeatures.add( e.getKey() );
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

	private DRectangle findBounds( MiniFacade toEdit ) {

		if ( toEdit.postState == null )
			return toEdit.getAsRect();
		else
			return toEdit.postState.outerFacadeRect;
	}
}
