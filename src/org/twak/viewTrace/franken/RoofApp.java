package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
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

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class RoofApp extends App {

	public SuperFace parent;

	public RoofApp(HasApp ha) {
		super(ha );
	}

	public RoofApp( RoofApp ruf ) {
		super( ruf );
	}

	@Override
	public App getUp() {
		return parent.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
		return new RoofApp( this );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		NetInfo ni = NetInfo.get(this);
		
		int resolution = ni.resolution;
		
		BufferedImage bi = new BufferedImage( resolution * 2, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

//		Map<MiniRoof, String> index = new HashMap<>();

		List<MiniRoof> mrb = batch.stream().map( x -> (MiniRoof)x.hasA ).collect( Collectors.toList() );
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		for ( MiniRoof toEdit : mrb ) {

			DRectangle drawTo = new DRectangle( resolution, 0, resolution, resolution );

			draw (g, drawTo, toEdit);

//			String name = System.nanoTime() + "@" + index.size();
//
//			index.put( toEdit, name );

			p2.addInput( bi, toEdit, toEdit.app.styleZ );
		}

		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						
						String dest = Pix2Pix.importTexture( e.getValue(), -1, null,  null );

						if ( dest != null ) 
							((MiniRoof)e.getKey()).app.texture = dest;
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				} finally {
					whenDone.run();
				}
			}
		} ) );
	}
	
	private void draw( Graphics2D g, DRectangle drawTo, MiniRoof mr ) {
		
		DRectangle bounds = new DRectangle(mr.bounds);
		
		if ( bounds.width > bounds.height ) {
			bounds.y -= (bounds.width - bounds.height) / 2;
			bounds.height = bounds.width;
		}
		else
		{
			bounds.x -= (bounds.height - bounds.width) / 2;
			bounds.width = bounds.height;
		}

		bounds.grow( 2 );
		
		mr.app.textureUVs = TextureUVs.Rectangle;
		mr.app.textureRect = new DRectangle ( bounds );
		
		bounds.y += bounds.height;
		bounds.height = -bounds.height;
		
		List<Polygon> boundary = Loopz.toPolygon (mr.boundary, bounds, drawTo );
		List<Polygon> pitches  = Loopz.toPolygon (mr.pitches , bounds, drawTo );
		List<Polygon> flats    = Loopz.toPolygon (mr.flats   , bounds, drawTo );
		
		g.setColor( Color.cyan );
		for (Polygon p : boundary) 
			g.fill( p );
		
		g.setColor( Color.red );
		for (Polygon p : flats) 
			g.fill( p );
		
		g.setStroke( new BasicStroke( 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		g.setColor( Color.magenta );
		
		for (Polygon p : pitches)
			g.draw( p );
		
		g.setStroke( new BasicStroke( 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		g.setColor( Color.gray );
		
		for (Polygon p : flats) 
			g.draw( p );
		for (Polygon p : boundary) 
			g.draw( p );
		
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net, AppMode.Bitmap};
	}
}
