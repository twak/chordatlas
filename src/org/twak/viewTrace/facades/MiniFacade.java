package org.twak.viewTrace.facades;

import static org.twak.utils.geom.DRectangle.Bounds.*;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;

import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.utils.Line;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.DRectangle.Bounds;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.Rainbow;
import org.twak.utils.ui.SimplePopup;
import org.twak.utils.ui.SimplePopup2;
import org.twak.utils.ui.Plot.ICanEdit;

public class MiniFacade implements ICanPaint, ICanEdit {
	
	// locations in imageFeatures.mega.megafacade R^2 space.
	
	public double width, height, groundFloorHeight, left;
	public boolean softLeft, softRight;
	public double[] 
			color = new double[] { 1, 0, 0, 1 },
			groundColor = null;
	public double uncertainty = 0;
	
	public enum Feature {
		
		BALCONY (new Color (170,0,255)), CORNICE (Color.blue), DOOR (new Color (170,255,0)), 
		SHOP (new Color (255,0,170)), SILL (new Color (255,170,0)), WINDOW ( new Color (0,255,170) ), MOULDING ( new Color (0,255,170) ),
		GRID (Color.black);
		
		Color color;
		
		private Feature (Color c) {
			this.color = c;
		}
	}
	
	public MultiMap<Feature, FRect> rects = new MultiMap<>();
	public List<Outer> outers = new ArrayList<>();
	public ImageFeatures imageFeatures;
	
	public List<Double> 
			hMargin = new ArrayList<>(), 
			vMargin = new ArrayList<>();

	public MiniFacade( MiniFacade m ) {
		
		this.width = m.width;
		this.height = m.height;
		this.groundFloorHeight = m.groundFloorHeight;
		this.left = m.left;
		this.color = Arrays.copyOf( m.color, m.color.length );
		if (m.groundColor != null)
			this.groundColor = Arrays.copyOf( m.groundColor, m.groundColor.length );
		this.softLeft = m.softLeft;
		this.softRight = m.softRight;
		this.imageFeatures = m.imageFeatures;
		this.imageXM = m.imageXM;
		this.scale = m.scale;
		
		Arrays.stream( Feature.values() ).forEach( f -> m.rects.get(f).stream().forEach( r -> rects.put(f, new FRect(r)) ) ); 
		
		this.hMargin = new ArrayList(m.hMargin);
		this.vMargin = new ArrayList(m.vMargin);
		
		if (m.grid != null)
			this.grid = new WinGrid ( m.grid ); 
	}

	
	public MiniFacade(){
		
	}
	
	double imageXM, scale;
	
	public MiniFacade (
			
			ImageFeatures ifs,
			Map yaml, 
			double imageWidthM, 
			double imageHeightM,
			double scale,
			double imageXM
			) {
		
		this.imageFeatures = ifs;
		this.imageXM = imageXM;
		this.scale = scale;
		
		double topM = imageHeightM;
		
		        left =  Double.parseDouble( (String) yaml.get( "facade-left"  ) ) / scale + imageXM;
		double right =  Double.parseDouble( (String) yaml.get( "facade-right" ) ) / scale + imageXM;
		
		width = right-left;
		
		{
			double softTol = TweedSettings.settings.miniSoftTol;
			
			double imgRight = imageXM + imageWidthM;

			softLeft  = imgRight - left  < softTol || left  - imageXM < softTol;
			softRight = imgRight - right < softTol || right - imageXM < softTol;
		}
		
		height            = topM - Double.parseDouble( (String) yaml.get( "sky-line"   ) ) / scale;
		groundFloorHeight = topM - Double.parseDouble( (String) yaml.get( "door-line"  ) ) / scale;
		
		List<String> rgb = (List) yaml.get( "rgb" );
		
		color = new double[] {
				Double.parseDouble ( rgb.get( 0 ) ),
				Double.parseDouble ( rgb.get( 1 ) ),
				Double.parseDouble ( rgb.get( 2 ) ), 1 };
		
		readRegions ( scale, imageXM, topM, ((Map) yaml.get("regions")) );
//		hMargin = readMargin( ((List) yaml.get("win_h_margin")) );
//		vMargin = readMargin( ((List) yaml.get("win_v_margin")) );
		readWinGrid( scale, imageXM, topM, ((Map) yaml.get("window-grid")) );
		
		if (yaml.get( "uncertainty" ) != null)
		try{
			uncertainty = Double.parseDouble( (String) yaml.get( "uncertainty" ) );
		}
		catch (NumberFormatException th) {
		}
		
		if (yaml.get("mezzanine") != null) {
			List<String> col = (List) ((Map)yaml.get( "mezzanine" )).get("rgb");
			groundColor = new double[] {
					Double.parseDouble ( col.get( 0 ) ),
					Double.parseDouble ( col.get( 1 ) ),
					Double.parseDouble ( col.get( 2 ) ), 1 };
		}
		
		
	}
	
	public boolean invalid() {
		return softLeft && softRight && width < 2;
	}

	private List<Double> readMargin( List<String> map ) {
		
		List<Double> out = new ArrayList<>();
		
		for (String s : map)
			out.add (Double.parseDouble( s )); 
		
		return out;
	}

	private void readRegions( double scale, double imageXM, double topM, Map map ) {
		
		if (map == null)
			map = new HashMap<>();
		
		for (Feature f : Feature.values()) 
			rects.putAll (f,readRects (scale, imageXM, topM, map, f.name().toLowerCase() ), false );
	}

	private List<FRect> readRects( double scale, double imageXM, double topM, Map<String, List<Map>>map, String name ) {
		
		List<FRect> out = new ArrayList<>();
		
		if (!map.containsKey( name ))
			return out;
		
		for (Map r : map.get(name)) {
			
			double h = (Double.parseDouble( (String) r.get( "bottom" ) ) - Double.parseDouble( (String) r.get( "top"  ) )) / scale;
			
			out.add (new FRect(
					Double.parseDouble( (String) r.get( "left"   ) ) / scale + left,
				    topM - Double.parseDouble( (String) r.get( "bottom"    ) ) / scale,
 				   (Double.parseDouble( (String) r.get( "right"  ) ) - Double.parseDouble( (String) r.get( "left" ) )) / scale,
				   h
					));
		}
		
		return out;
		
	}

	public static class WinGrid {
		
		
		public WinGrid(){}
		
		public WinGrid( WinGrid o ) {
			this.x = o.x;
			this.y = o.y;
			this.width = o.width;
			this.height = o.height;
			this.wHeight = o.wHeight;
			this.wWidth = o.wWidth;
			this.vspacing = o.vspacing;
			this.hspacing = o.hspacing;
			this.cols = o.cols;
			this.rows = o.cols;
		}
		
		List<Win> dows = new ArrayList<>();// deleteme
		
		double x, y, width, height, wHeight, wWidth, vspacing, hspacing;
		int cols, rows;
	}
	
	public static class Win { //deleteme
		public double x, y, width, height;
		public Win(){}
		public Win (double x, double y, double width, double height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
	
	WinGrid grid;
	
	private void readWinGrid( double scale, double imageXM, double topM, Map yGrid ) {
		{
			
			if ( yGrid == null || yGrid.get( "bottom" ).equals( "-1" ) )
				return;
			
			grid = new WinGrid();
			
			grid.x       = Double.parseDouble( (String) yGrid.get( "left"   ) ) / scale + imageXM;
			double gr = Double.parseDouble( (String) yGrid.get( "right"  ) ) / scale + imageXM;
			grid.width = gr - grid.x;
			
			grid.y       = topM - Double.parseDouble( (String) yGrid.get( "bottom"    ) ) / scale;
			double gt = topM - Double.parseDouble( (String) yGrid.get( "top" ) ) / scale;
			grid.height = gt - grid.y;
			
			grid.wHeight = Double.parseDouble( (String) yGrid.get( "height"  ) ) / scale;
			grid.wWidth  = Double.parseDouble( (String) yGrid.get( "width"   ) ) / scale;
			grid.vspacing  = scale ( yGrid, scale, "vertical_spacing" );
			grid.hspacing  = scale ( yGrid, scale, "horizontal_spacing" );
			
			grid.cols = Integer.parseInt( (String) yGrid.get( "cols" ) );
			grid.rows = Integer.parseInt( (String) yGrid.get( "rows" ) );
			
			for (Object wp : (List) yGrid.get("rectangles") ) {
				
				List<Double> dp = ((List<String>)wp).stream().map(x -> Double.parseDouble(x)).collect( Collectors.toList() );
				
				FRect win = new FRect();
				
				win.f = Feature.GRID;
				win.x      = dp.get(1) / scale + imageXM;
				win.y      = topM - dp.get(2) / scale;
				win.width  = (dp.get(3) / scale + imageXM) - win.x;
				win.height = (topM - dp.get(0) / scale) - win.y;
				
				rects.put( win.f, win );
			}
		}
	}

	private double scale( Map yGrid, double scale, String key ) {
		try {
			return  Double.parseDouble( (String) yGrid.get( key ) ) / scale;
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	public java.awt.geom.Rectangle2D.Double toRect() {
		return new Rectangle2D.Double( left, 0, width, height );
	}

	public void scaleX( double lp, double rp ) {
		
		if (!softLeft && !softRight) 
			scaleX (new DRectangle(lp, -1, rp-lp, -1));
		else if (softLeft) 
			scaleX(new DRectangle(rp - width, -1, width, -1) );
		else if (softRight) 
			scaleX(new DRectangle(lp, -1, width, -1) );
		else 
			throw new Error("no way to ground scale!");
	}
	
	public void scaleX2( double lp, double rp ) {
		scaleX (new DRectangle(lp, -1, rp-lp, -1));
	}
	
	public void scaleX( DRectangle all ) {
		
		double xFactor = all.width / width;
		
		for (Feature f : Feature.values())  
			rects.map.put( f, scale ( rects.get(f), xFactor, all.x-left ) );
		
		
//		if (grid != null) {
//			
//			List<DRectangle> scaled = new ArrayList<>();
//			
//		}
		
		width = all.width;
		left = all.x;
	}

	private List<FRect> scale( List<FRect> rects, double widthRatio, double xOffset ) {
		
		List<FRect> out = new ArrayList();
		if ( rects != null )
			for ( FRect o : rects ) {
				FRect n = new FRect( o );
				n.width *= widthRatio;
				n.x = (n.x - left) * widthRatio + left + xOffset;
				out.add( n );
			}
		
		return out;
	}

	public static boolean PAINT_IMAGE = false;

	@Override
	public void paint( Graphics2D g, PanMouseAdaptor ma ) {

		
		if ( PAINT_IMAGE && imageFeatures != null) {
			
			Composite oldC = g.getComposite();
		    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f) );
			paintImage( g, ma, left, -height, left+width, 0 );
			g.setComposite( oldC );
		}
		
		g.setStroke( new BasicStroke( 2f ) );

		if (width < 5)
			return;
		
		for ( Feature f : Feature.values() )
			if (f != Feature.GRID)
			for ( FRect w : rects.get( f ) ) {
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

		if (false && grid != null) {
				g.setColor(new Color (100,100,255, 200));
				
//				for ( DRectangle w : grid.dows )
//					g.fillRect( ma.toX( w.x ), ma.toY( -w.y - w.height ), ma.toZoom( w.width ), ma.toZoom( w.height ) );

				g.setColor(new Color (100,255,10, 200));
				
				if (false)
				for ( int x = 0; x < grid.cols; x++ )
					for ( int y = 0; y < grid.rows; y++ ) {
						g.fillRect( 
								ma.toX(  x * grid.hspacing + grid.x ), 
								ma.toY( -y * grid.vspacing - grid.wHeight - grid.y )
								, ma.toZoom( grid.wWidth ), ma.toZoom( grid.wHeight ) );
					}
				
		}
		
		g.setColor( Color.black );
		g.drawLine( ma.toX( left ), ma.toY( 0 ), ma.toX( left + width ), ma.toY( 0 ) );
		g.setColor( Color.green );
		g.setColor( new Color (0,170,255) );
		g.setStroke( new BasicStroke( softLeft ? 1 : 3 ) );
		g.drawLine( ma.toX( left ), ma.toY(0), ma.toX( left ), ma.toY(-height) );
		g.setStroke( new BasicStroke( softRight ? 1 : 3 ) );
		g.drawLine( ma.toX( left + width ), ma.toY(0), ma.toX( left + width ), ma.toY(-height)  );
		g.setColor( Color.black );
		g.setStroke( new BasicStroke( 1 ) );
		g.drawLine( ma.toX( left ), ma.toY( -height ), ma.toX( left + width ), ma.toY( -height ) );
		g.drawLine( ma.toX( left ), ma.toY( -groundFloorHeight ), ma.toX( left + width ), ma.toY( -groundFloorHeight ) );
			

	}

	public void paintImage( Graphics2D g, PanMouseAdaptor ma, double x1, double y1, double x2, double y2 ) {
		
		BufferedImage source = imageFeatures.getRectified();

		// draw getRectified at getOrtho's width
		if (source == null)
			return;
		
		g.drawImage( source, 
			
				ma.toX( x1), ma.toY(y1),  ma.toX( x2), ma.toY(y2),
			
			(int)((left - imageXM) * scale),
			source.getHeight()-(int)((height) * scale),
			(int)((left + width - imageXM) * scale),
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

	public List<FRect> getRects( Feature ... feats ) {
		
		if (feats.length == 0)
			feats = Feature.values();
		
		List<FRect> rs = new ArrayList<>();
		
		for (Feature f : feats)
			rs.addAll(rects.get(f));
			
		return rs;
	}

	public boolean contains( Point2d avg ) {
		return getAsRect().contains( avg );
	}

	public DRectangle getAsRect() {
		return new DRectangle(left, 0, width, height);
	}

	private Point2d flip (Point2d in )
	{
		return new Point2d( in.x, - in.y );
	}

	@Override
	public double getDistance( Point2d pt ) {

		pt = flip (pt);
		
		if ( contains( pt ) )
			return 0;

		double dist = Double.MAX_VALUE;

		for ( Bounds b : new Bounds[] { XMIN, YMIN, XMAX, YMAX } ) {
			Line l = getAsRect().getEdge( b );
			dist = Math.min( dist, l.distance( pt ) );
		}

		return dist;
	}

	FRect dragging = null;
	
	@Override
	public void mouseDown( MouseEvent e, PanMouseAdaptor ma ) {
		
		Point2d pt = flip ( ma.from( e ) );
		
		double bestDist = ma.fromZoom( 10 );
		
		for (FRect f: getRects()) {
			
			double dist = f.getDistance( pt );

			if (dist < bestDist) {
				bestDist = dist;
				dragging = f;
			}
		}
		
		if (dragging != null)
			dragging.mouseDown( e, ma );
	}


	@Override
	public void mouseDragged( MouseEvent e, PanMouseAdaptor ma ) {
		if (dragging != null)
			dragging.mouseDragged( e, ma );
	}


	@Override
	public void mouseReleased( MouseEvent e, PanMouseAdaptor ma ) {
		if (dragging != null)
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
					rects.remove( dragging.f, dragging );
				
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
					rects.put( rec.f, rec );
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
					
					rects.put( f, rec );
				
					cl.stateChanged( null );
				}
			});
		}
		
		pop.add( "color", new Runnable() {
			@Override
			public void run() {

				new ColourPicker( null, new Color ( (float) color[0], (float) color[1], (float) color[2]  )) {
					
					@Override
					public void picked( Color color ) {
						
						MiniFacade.this.color = new double[] {
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

	public double right() {
		return left + width;
	}
	
	public double left() {
		return left;
	}
	
	public double center() {
		return left + width/2;
	}
}
