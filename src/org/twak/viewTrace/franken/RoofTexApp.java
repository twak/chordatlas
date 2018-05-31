package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output.Face;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.FCircle;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.tweed.gen.skel.RoofTag;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;


public class RoofTexApp extends App {

	public RoofSuperApp zuper = new RoofSuperApp(this);

	public RoofGreebleApp greebles = new RoofGreebleApp( this );
	
	public SuperFace superFace;
	
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
		return greebles;
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
	
	public String getTexture( RoofTag rt ) {
		
		String out = null;
		
		if ( zuper.appMode == AppMode.Net && zuper.textures != null) 
			out = zuper.textures.get( rt );
		
		if (out == null) 
			out = texture;
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		NetInfo ni = NetInfo.get(this);
		Pix2Pix p2 = new Pix2Pix( ni );
		
		int resolution = ni.resolution;
		
		addCoarseRoofInputs( batch.stream().map( x -> (MiniRoof)x.hasA ).collect( Collectors.toList() ), p2, resolution );

		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						MiniRoof mr = ((MiniRoof)e.getKey());
						
						String dest = Pix2Pix.importTexture( e.getValue(), -1, null,  null, new RescaleOp(0.5f, 1.5f, null ), new BufferedImage[3] );

						if ( dest != null ) {
							zuper.textures = null;
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

	public static void addCoarseRoofInputs( List<MiniRoof> mrb, Pix2Pix p2, int resolution ) {
		
		BufferedImage label = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gL = (Graphics2D) label.getGraphics();
		
		BufferedImage empty = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gE = (Graphics2D) empty.getGraphics();

		DRectangle drawTo = new DRectangle( 0, 0, resolution, resolution );
		
		for ( MiniRoof toEdit : mrb ) {

			DRectangle bounds = draw (gL, drawTo, toEdit);
			drawEmpty (gE, drawTo, toEdit, bounds);
			
			p2.addInput( label, empty, null, toEdit, toEdit.app.styleZ, null );
		}
	}
	
	private static DRectangle draw( Graphics2D g, DRectangle drawTo, MiniRoof mr ) {
		
		
		DRectangle bounds = new DRectangle(mr.bounds);
		
		if ( bounds.width > bounds.height ) {
			bounds.y -= ( bounds.width - bounds.height ) / 2;
			bounds.height = bounds.width;
		} else {
			bounds.x -= ( bounds.height - bounds.width ) / 2;
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
		
		g.setStroke( new BasicStroke( 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		
		g.setColor( Color.cyan );
		for (Polygon p : boundary) {
			g.fill( p );
			g.draw( p );
		}
		
		g.setColor( Color.red );
		for (Polygon p : flats) { 
			g.fill( p );
			g.draw( p );
		}
		
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
		
		g.setStroke( new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		
		for (Face f : mr.origins.ab.values()) 
			for (FCircle greeble : mr.getGreebles( f ) ) {
				
				double  radiusW = greeble.radius, 
						radiusH = greeble.radius * ( greeble.f.verticalProjection ? 1 : Math.cos ( f.edge.getAngle() ) );
				
				Loop<Point2d> loop = new Loop<>();
				

				Vector3d e3 = f.edge.direction();
				e3.normalize();
				
				Vector2d 
						x = new Vector2d (  e3.x, e3.y), 
						y = new Vector2d ( -e3.y, e3.x );
				
				
				
				loop.append( new Point2d (greeble.loc.x + x.x * radiusW + y.x * radiusH, greeble.loc.y + x.y * radiusW + y.y * radiusH ) );
				loop.append( new Point2d (greeble.loc.x - x.x * radiusW + y.x * radiusH, greeble.loc.y - x.y * radiusW + y.y * radiusH ) );
				loop.append( new Point2d (greeble.loc.x - x.x * radiusW - y.x * radiusH, greeble.loc.y - x.y * radiusW - y.y * radiusH ) );
				loop.append( new Point2d (greeble.loc.x + x.x * radiusW - y.x * radiusH, greeble.loc.y + x.y * radiusW - y.y * radiusH ) );
				
				Polygon p = Loopz.toPolygon (loop.singleton() , bounds, drawTo ).get(0);
				
				g.setColor( greeble.f.colour );
				g.fill(p);
				
			}
		
		
		g.setColor( Color.magenta );
		g.setStroke( new BasicStroke( 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		for (HalfEdge e : mr.app.superFace) {
			MiniFacade mf = ((SuperEdge)e).toEdit; 
			for (FRect f : mf.featureGen.getRects( Feature.WINDOW )) 
				if (f.app.coveringRoof != null) {


					int count = 0;
					for (Loopable<Point2d> ll : f.app.coveringRoof.loopableIterator()) {
						
						if (count ++ == 3)
							continue;
						
						Point2d a = drawTo.transform( bounds.normalize( ll.get() ) );
						Point2d b = drawTo.transform( bounds.normalize( ll.getNext().get() ) );
						
						g.drawLine( (int)a.x, (int)a.y, (int)b.x, (int)b.y );
					}
					
//					Polygon dormer = Loopz.toPolygon (f.app.coveringRoof.singleton(), bounds, drawTo ).get(0);
//					g.setColor( Color.red );
//					g.fill( dormer );
//					g.setColor( Color.magenta );
//					g.draw( dormer );
				}
		}
		
		return bounds;
	}
	
	private static void drawEmpty( Graphics2D g, DRectangle drawTo, MiniRoof mr, DRectangle bounds ) {
		
		g.setColor( Color.black );
		g.fillRect( (int) drawTo.x, (int) drawTo.x, (int) drawTo.width, (int) drawTo.height );
		
		List<Polygon> boundary = Loopz.toPolygon (mr.boundary, bounds, drawTo );
		
		g.setColor( Color.blue );
		g.setStroke( new BasicStroke( 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		
		for (Polygon p : boundary) {
			g.fill( p );
			g.draw( p );
		}
		
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net, AppMode.Bitmap};
	}

}
