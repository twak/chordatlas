package org.twak.viewTrace.facades;

import static org.twak.utils.geom.DRectangle.Bounds.*;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;

import org.twak.utils.Line;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.collections.Loop;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.DRectangle.Bounds;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.Rainbow;
import org.twak.utils.ui.SimplePopup2;
import org.twak.utils.ui.Plot.ICanEdit;
import org.twak.viewTrace.facades.MiniFacade.Feature;


public class MiniFacadePainter implements ICanPaintU, ICanEdit {
	public static boolean PAINT_IMAGE = true;

	
	@Override
	public void paint(Object oa, Graphics2D g, PanMouseAdaptor ma ) {

		MiniFacade mf = (MiniFacade)oa;
		
		if ( PAINT_IMAGE && mf.imageFeatures != null) {
			
			Composite oldC = g.getComposite();
		    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f) );
			paintImage( g, ma, mf, mf.left, -mf.height, mf.left+mf.width, 0 );
			g.setComposite( oldC );
		}
		
		g.setStroke( new BasicStroke( 2f ) );

		if (mf.width < 5)
			return;
		
		for ( Feature f : Feature.values() )
			if (f != Feature.GRID)
			for ( FRect w : mf.featureGen.get( f ) ) {
				if ( w.outer == null ) 
				{
					g.setColor( f.color );
					
//					if (w.width < 10)
					g.drawRect( ma.toX( w.x ), ma.toY( -w.y - w.height ), ma.toZoom( w.width ), ma.toZoom( w.height ) );
					g.setColor( Color.black );
					if ( w.id >= 0 )
						g.drawString( w.id + "", ma.toX( w.x ) + 3, ma.toY( -w.y - w.height ) + 13 );
					
//					try {
//						g.drawString( 
//								w.attachedHeight.get( Feature.CORNICE ).d +", " +
//								w.attachedHeight.get( Feature.BALCONY ).d +", "+
//								w.attachedHeight.get( Feature.SILL ).d + ", ",
//								ma.toX( w.x ) + 3, ma.toY( -w.y - w.height ) + 13  );
//					} catch (NullPointerException e) {}
					
					
				}

				for ( Dir dir : Dir.values() ) {
					FRect n = w.getAdj( dir );
					if ( n != null ) {

						
						g.setColor( Rainbow.getColour( dir.ordinal() ) );

						Point2d s = getRectPoint( w, dir ), e = getRectPoint( n, dir.opposite );

						s.y = -s.y;
						e.y = -e.y;

						Line l = new Line( s, e );

						PaintThing.paint( l, g, ma );
						PaintThing.drawArrow( g, ma, l, 3 );

						//						if ( dir == Dir.L || dir == Dir.U ) {
						//							Point2d label = l.fromPPram( 0.5 );
						//							g.drawString( w.distanceToAdjacent( dir ) + "", ma.toX( label.x ), ma.toY( label.y ) );
						//						}
					}
				}
			}

//		for ( Outer o : outers )
//			o.paint( g, ma );

		if (false && mf.grid != null) {
				g.setColor(new Color (100,100,255, 200));
				
//				for ( DRectangle w : grid.dows )
//					g.fillRect( ma.toX( w.x ), ma.toY( -w.y - w.height ), ma.toZoom( w.width ), ma.toZoom( w.height ) );

				g.setColor(new Color (100,255,10, 200));
				
				if (false)
				for ( int x = 0; x < mf.grid.cols; x++ )
					for ( int y = 0; y < mf.grid.rows; y++ ) {
						g.fillRect( 
								ma.toX(  x * mf.grid.hspacing + mf.grid.x ), 
								ma.toY( -y * mf.grid.vspacing - mf.grid.wHeight - mf.grid.y )
								, ma.toZoom( mf.grid.wWidth ), ma.toZoom( mf.grid.wHeight ) );
					}
				
		}
		
		g.setColor( Color.gray );
		for (Loop<? extends Point2d> l : new ArrayList<Loop<? extends Point2d>> ( mf.skelFaces )) 
			paintPolygon( l, g, ma );
		
		
		g.setColor( Color.black );
		g.drawLine( ma.toX( mf.left ), ma.toY( 0 ), ma.toX( mf.left + mf.width ), ma.toY( 0 ) );
		g.setColor( Color.green );
		g.setColor( new Color (0,170,255) );
		g.setStroke( new BasicStroke( mf.softLeft ? 1 : 3 ) );
		g.drawLine( ma.toX( mf.left ), ma.toY(0), ma.toX( mf.left ), ma.toY(-mf.height) );
		g.setStroke( new BasicStroke( mf.softRight ? 1 : 3 ) );
		g.drawLine( ma.toX( mf.left + mf.width ), ma.toY(0), ma.toX( mf.left + mf.width ), ma.toY(-mf.height)  );
		g.setColor( Color.black );
		g.setStroke( new BasicStroke( 1 ) );
		g.drawLine( ma.toX( mf.left ), ma.toY( -mf.height ), ma.toX( mf.left + mf.width ), ma.toY( -mf.height ) );
		g.drawLine( ma.toX( mf.left ), ma.toY( -mf.groundFloorHeight ), ma.toX( mf.left + mf.width ), ma.toY( -mf.groundFloorHeight ) );
	}
	
	private static void paintPolygon (Loop<? extends Point2d> ll, Graphics2D g, PanMouseAdaptor ma) {
		
		Polygon p = new Polygon();
		
		for (Point2d pt : ll) 
			p.addPoint(ma.toX(pt.x), ma.toY(-pt.y));

		g.draw(p);
	}

	public void paintImage( Graphics2D g, PanMouseAdaptor ma, MiniFacade mf, double x1, double y1, double x2, double y2 ) {
		
		BufferedImage source = mf.imageFeatures.getRectified();

		// draw getRectified at getOrtho's width
		if (source == null)
			return;
		
		g.drawImage( source,  
			
				ma.toX( x1), ma.toY(y1),  ma.toX( x2), ma.toY(y2),
			
			(int)((mf.left - mf.imageXM) * mf.scale),
			source.getHeight()-(int)((mf.height) * mf.scale),
			(int)((mf.left + mf.width - mf.imageXM) * mf.scale),
			 source.getHeight(),
			null );
	}
	
	private Point2d getRectPoint( FRect w, Dir dir ) {
		
		switch (dir) {
		case D:
			return new Point2d(w.x + w.width/2, w.y );
		case U:
			return new Point2d(w.x + w.width/2, w.y + w.height);
		case L:
			return new Point2d(w.x , w.y + w.height/2);
		case R:
		default:
			return new Point2d(w.x + w.width , w.y + w.height/2);
		}
		
	}
	
	private Point2d flip (Point2d in )
	{
		return new Point2d( in.x, - in.y );
	}

	/****
	 * I can edit methods
	 */
	
	MiniFacade mf;
	@Override
	public void setObject( Object o ) {
		this.mf = (MiniFacade)o;
	}

	@Override
	public double getDistance( Point2d pt ) {

		pt = flip (pt);
		
		if ( mf.contains( pt ) )
			return 0;

		double dist = Double.MAX_VALUE;

		for ( Bounds b : new Bounds[] { XMIN, YMIN, XMAX, YMAX } ) {
			Line l = mf.getAsRect().getEdge( b );
			dist = Math.min( dist, l.distance( pt ) );
		}

		return dist;
	}

	transient FRect dragging = null;
	transient int mouseLastDown = -1;
	
	@Override
	public void mouseDown( MouseEvent e, PanMouseAdaptor ma ) {
		
		Point2d pt = flip ( ma.from( e ) );
		
		double bestDist = ma.fromZoom( 10 );
		
		dragging = null;
		mouseLastDown = e.getButton();
		
		for (FRect f: mf.featureGen.getRects()) {
			
			double dist = f.getDistance( pt );

			if (dist < bestDist) {
				bestDist = dist;
				dragging = f;
			}
		}
		
		if (dragging != null && e.getButton() == 3)
			dragging.mouseDown( e, ma );
	}


	@Override
	public void mouseDragged( MouseEvent e, PanMouseAdaptor ma ) {
		if (dragging != null && mouseLastDown == 3 )
			dragging.mouseDragged( e, ma );
	}


	@Override
	public void mouseReleased( MouseEvent e, PanMouseAdaptor ma ) {
		if (dragging != null && e.getButton() == 3)
			dragging.mouseReleased( e, ma );
	}


	@Override
	public void getMenu( MouseEvent e, PanMouseAdaptor ma, ChangeListener cl ) {
		
		mouseDown( e, ma );
		
		SimplePopup2 pop = new SimplePopup2( e );
		
		if (dragging != null)
		pop.add( "delete", new Runnable() {

			@Override
			public void run() {
				if (dragging != null)
					mf.featureGen.remove( dragging.f, dragging );
				
				cl.stateChanged( null );
			}
		});
		
		if (dragging != null)
		pop.add( "duplicate", new Runnable() {
			
			@Override
			public void run() {
				
				mouseDown( e, ma );
				
				if (dragging != null) {
					
					FRect rec = new FRect( dragging );
//					rec.x += 0.5;
					rec.x += rec.width + 0.3;
					mf.featureGen.put( rec.f, rec );
					cl.stateChanged( null );
					
				}
				
			}
		});
		
		for (Feature f : Feature.values() ) {
			pop.add( "add " + f.name().toLowerCase(), new Runnable() {
				
				@Override
				public void run() {
					
					Point2d pt = flip ( ma.from( e ) );
					
					FRect rec = new FRect( pt.x, pt.y, pt.x + 0.5, pt.y + 0.5);
					rec.f = f;
					
					mf.featureGen.put( f, rec );
				
					cl.stateChanged( null );
				}
			});
		}
		
		pop.add( "color", new Runnable() {
			@Override
			public void run() {

				new ColourPicker( null, new Color ( (float) mf.color[0], (float) mf.color[1], (float) mf.color[2]  )) {
					
					@Override
					public void picked( Color color ) {
						
						mf.color = new double[] {
								color.getRed() / 255f,
								color.getGreen() / 255f,
								color.getBlue() / 255f, 
								1
						};
						
						cl.stateChanged( null );
					}
				};
			}
		});
		
		pop.show();
		
	}
}
