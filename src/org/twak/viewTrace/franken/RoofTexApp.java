package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class RoofTexApp extends App {

	public RoofSuperApp zuper = new RoofSuperApp(this);

	public SuperFace parent;
	
	public String coarse;

	public RoofTexApp(HasApp ha) {
		
		super( ha );
	}

	public RoofTexApp( RoofTexApp ruf ) {
		
		super( ruf );
		this.zuper = ruf.zuper;
	}

	@Override
	public App getUp() {
		
		return parent.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> out = new MultiMap<>();
		out.put( "super", zuper );
		return out;	
	}

	@Override
	public App copy() {
		return new RoofTexApp( this );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		NetInfo ni = NetInfo.get(this);
		
		int resolution = ni.resolution;
		
		BufferedImage label = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gL = (Graphics2D) label.getGraphics();
		
		BufferedImage empty = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gE = (Graphics2D) empty.getGraphics();

		DRectangle drawTo = new DRectangle( 0, 0, resolution, resolution );
		
		List<MiniRoof> mrb = batch.stream().map( x -> (MiniRoof)x.hasA ).collect( Collectors.toList() );
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		
		for ( MiniRoof toEdit : mrb ) {

			DRectangle bounds = draw (gL, drawTo, toEdit);
			drawEmpty (gE, drawTo, toEdit, bounds);
			
			p2.addInput( label, empty, null, toEdit, toEdit.app.styleZ, null );
		}

		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						MiniRoof mr = ((MiniRoof)e.getKey());
						
						String dest = Pix2Pix.importTexture( e.getValue(), -1, null,  null );

						if ( dest != null ) {
							mr.app.coarse = mr.app.texture = dest;
							mr.app.textureUVs = TextureUVs.Rectangle;
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				} finally {
					whenDone.run();
				}
			}
		} ) );
	}
	
	private DRectangle draw( Graphics2D g, DRectangle drawTo, MiniRoof mr ) {
		
		
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
		
		mr.app.textureRect = new DRectangle ( bounds );
		
		bounds.y += bounds.height;
		bounds.height = -bounds.height;
		
		List<Polygon> boundary = Loopz.toPolygon (mr.boundary, bounds, drawTo );
		List<Polygon> pitches  = Loopz.toPolygon (mr.pitches , bounds, drawTo );
		List<Polygon> flats    = Loopz.toPolygon (mr.flats   , bounds, drawTo );
		
		g.setColor( Color.black );
		g.fillRect( (int) drawTo.x, (int) drawTo.x, (int) drawTo.width, (int) drawTo.height );
		
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
		
		return bounds;
	}
	
	private void drawEmpty( Graphics2D g, DRectangle drawTo, MiniRoof mr, DRectangle bounds ) {
		
		g.setColor( Color.black );
		g.fillRect( (int) drawTo.x, (int) drawTo.x, (int) drawTo.width, (int) drawTo.height );
		
		List<Polygon> boundary = Loopz.toPolygon (mr.boundary, bounds, drawTo );
		
		g.setColor( Color.blue );
		
		for (Polygon p : boundary) 
			g.fill( p );
		
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net, AppMode.Bitmap};
	}
}
