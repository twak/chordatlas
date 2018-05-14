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

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Pix2Pix.Job;
import org.twak.viewTrace.facades.Pix2Pix.JobResult;

public class RoofTex extends App {

	public RoofTex(HasApp ha) {
		super(ha, "roof", "roofs2", 8, 512);
	}

	public RoofTex( RoofTex ruf ) {
		super( ruf );
	}

	@Override
	public App getUp() {
		return null;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
		return new RoofTex( this );
	}
	
	@Override
	public void computeSelf(Runnable globalUpdate, Runnable whenDone) {
		
		BufferedImage bi = new BufferedImage( resolution * 2, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = (Graphics2D) bi.getGraphics();

		Map<MiniRoof, String> index = new HashMap<>();

		for ( MiniRoof toEdit : Collections.singletonList( (MiniRoof) hasA ) ) {

			DRectangle drawTo = new DRectangle( resolution, 0, resolution, resolution );

			draw (g, drawTo, toEdit);

			String name = System.nanoTime() + "@" + index.size();

			index.put( toEdit, name );

			Pix2Pix.addInput( bi, name, netName );
		}

		Pix2Pix.submit( new Job( netName, System.nanoTime() + "_" + zAsString(), new JobResult() {
			
			@Override
			public void finished( File f ) {
				try {

					new File( Tweed.SCRATCH ).mkdirs();

					for ( Map.Entry<MiniRoof, String> e : index.entrySet() ) {

						String dest = Pix2Pix.importTexture( f, e.getValue(), -1, null );

						if ( dest != null ) 
							e.getKey().app.texture = dest;
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
}
