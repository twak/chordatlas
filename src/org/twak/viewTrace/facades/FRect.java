package org.twak.viewTrace.facades;

import java.awt.event.MouseEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;

import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.MutableDouble;
import org.twak.utils.Pair;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.Plot.ICanEdit;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.DoorTexApp;
import org.twak.viewTrace.franken.PanesLabelApp;
import org.twak.viewTrace.franken.PanesTexApp;

public class FRect extends DRectangle implements ICanEdit {
	
	// these are variously used around the Regularizer...
	public MultiMap<Feature, FRect> attached = new MultiMap<>();
	private Feature f;
	FRect[] adjacent = new FRect[4];
	public int id = -1;
	
	public MiniFacade mf;

	public PanesLabelApp panesLabelApp;
	public PanesTexApp panesTexApp;
	
	public Cache<Feature, HeightDepth> attachedHeight = new Cach<>( new Cach.ConstMake(new HeightDepth( 0, 0.2 )) );
	
	public static class HeightDepth extends MutableDouble implements Serializable {
		
		public double depth = -1;
		
		public HeightDepth( double height ) {
			super( height );
		}
		
		public HeightDepth( double height, double depth ) {
			super( height );
			this.depth = depth;
		}
	}
	
	AOuter outer;
	int xi, yi; 
	int[] gridCoords;
	
	public FRect( FRect o ) {
		super(o);
		
		init( o );
		initApps();
	}


	private void init( FRect o ) {
		id = o.id;
		
		outer = o.outer;
		
		xi = o.xi;
		yi = o.yi;
		
		gridCoords = o.gridCoords == null ? null : Arrays.copyOf( o.gridCoords, o.gridCoords.length );
		attached = new MultiMap<>( attached );
		attachedHeight.cache = new HashMap<>( o.attachedHeight.cache );
		this.mf = o.mf;
		setFeat( o.getFeat() );
	}
	

	public FRect( FRect o, PanesLabelApp pla, PanesTexApp pta, DoorTexApp dta ) {
		super(o);
		
		init( o );
		
		this.panesLabelApp = pla;
		this.panesTexApp = pta;
	}
	
	public FRect(MiniFacade mf) {
		super();
		this.mf = mf;
		initApps();
	}
	
	public FRect( double x, double y, double w, double h, MiniFacade mf ) {
		super (x,y,w,h);
		this.mf = mf;
		initApps();
	}


	public FRect( DRectangle r, MiniFacade mf ) {
		super( r );
		this.mf = mf;
		initApps();
	}

	public FRect( Feature feature, double x, double y, double w, double h, MiniFacade mf ) {
		super (x,y,w,h);
		this.mf = mf;
		this.setFeat( feature );
		
		initApps();
	}
	

	private void initApps() {
		
		if (panesLabelApp == null)
			panesLabelApp = new PanesLabelApp( this );
		
		if (panesTexApp == null || 
				( this.f == Feature.DOOR   && this.panesTexApp.getClass() != DoorTexApp.class ) ||
				( this.f == Feature.WINDOW && this.panesTexApp.getClass() != PanesTexApp.class ) ||
				( this.f == Feature.SHOP   && this.panesTexApp.getClass() != PanesTexApp.class ) )
		{
			if (f == null)
				panesTexApp = new PanesTexApp( this );
			else
				switch ( f ) {
				case SHOP:
				case WINDOW:
					panesTexApp = new PanesTexApp( this );
					break;
				case DOOR:
					panesTexApp = new DoorTexApp( this );
					break;
				default:
					panesTexApp = null;
				}
		}
	}

	public FRect getAdj(Dir d) {
		return adjacent[d.ordinal()];
	}
	
	public void setAdj(Dir d, FRect rect) {
		adjacent[d.ordinal()] = rect;
	}
	
	public double distanceToAdjacent (Dir dir) {
		
		FRect o = getAdj( dir );
		
		if (o == null)
			return Double.MAX_VALUE;
		
		switch (dir) {
			case R:
				return o.x - getMaxX();
			case L:
				return x - o.getMaxX();
			case U:
				return o.y - getMaxY();
			case D:
				return y - o.getMaxY();
			default:
				return Double.NaN;
		}
	}

	public double sideLength( Bounds b ) {
		
		switch ( b ) {
		case XMIN:
		case XMAX:
			return height;
		case YMIN:
		case YMAX:
			return width;
		default:
			return -1;
		}
	}

	static Bounds[] horz = new Bounds[] {Bounds.XMIN, Bounds.XMAX}, vert = new Bounds[] {Bounds.YMIN, Bounds.YMAX};
	public List<FRect> fractureV( FRect infront ) {
		List<FRect> out = new ArrayList<>();
		
		if (!intersects( infront )) {
			out.add(this);
			return out;
		}
		
		List<FRect> a = new ArrayList<>(), b = new ArrayList ();
		
		b.add( this );
		
		for ( FRect r : b )
			fracture ( r, infront, infront.get( Bounds.XMIN ), horz, out, a );
		
		b.clear();
		
		for ( FRect r : a ) 
			fracture ( r, infront, infront.get( Bounds.XMAX ), horz, b, out );
		
		a.clear();
		
		for ( FRect r : b )
			fracture ( r, infront, infront.get( Bounds.YMIN ), vert, out, a );
		
		b.clear();
		
		for ( FRect r : a ) 
			fracture ( r, infront, infront.get( Bounds.YMAX), vert, b, out );
		
		return out;
	}
	
	private static void fracture ( FRect toSplit, FRect infront, 
			double l, 
			Bounds[] minMax,
			List<FRect> before, List<FRect> after) {

		
		if (toSplit.get( minMax[1] ) < l ) {
			before.add(toSplit);
		} else if (toSplit.get(minMax[0]) > l) {
			after.add( toSplit );
		} else {
			
			FRect g = new FRect( toSplit ), 
					p = new FRect( toSplit );
			
			g.set( minMax[1], l, true );
			before.add (g);
			p.set( minMax[0], l, true );
			after.add(p);
		}
	}

	private Point2d flip( Point2d in ) {
		return new Point2d( in.x, -in.y );
	}
	
	@Override
	public double getDistance( Point2d pt ) {
		
        double dist = Double.MAX_VALUE;

        for (Pair<Point2d,Point2d> pts : new ConsecutivePairs<Point2d> ( Arrays.asList( points() ), true))
            dist = Math.min (dist, new Line( pts.first(), pts.second() ).distance(pt, true));

        if (contains( pt ))
        	return 0;
        
        return dist;
	}

	Bounds dragging = null;
	Point2d lastPoint = null;
	
	@Override
	public void mouseDown( MouseEvent e, PanMouseAdaptor ma ) {
		
		double best = ma.fromZoom( 10 );
		dragging = null;
		Point2d pt = lastPoint = flip ( ma.from( e ) );
		
		for (Bounds b : new Bounds[] {XMIN, YMIN, XMAX, YMAX}) {
			double dist = getEdge(b).distance(pt);
			if (dist < best) {
				best = dist;
				dragging = b;
			}
		}

		// dragging == null means move whole rect
		
	}


	@Override
	public void mouseDragged( MouseEvent e, PanMouseAdaptor ma) {
		
		Point2d pt = flip ( ma.from( e ) );
		Point2d delta = new Point2d (pt);
		delta.sub( lastPoint );
		
		if ( dragging == null ) {

			Point2d n = new Point2d( 
					get( Bounds.XCEN ) + delta.x, 
					get( Bounds.YCEN ) + delta.y );

			set( Bounds.XCEN, n.x );
			set( Bounds.YCEN, n.y );
		} else {
			double value = get( dragging ) + ( dragging.name().charAt( 0 ) == 'Y' ? delta.y : delta.x );
			set( dragging, value, true );
		}
		
		lastPoint = pt;
	}

	@Override
	public void mouseReleased( MouseEvent e, PanMouseAdaptor ma ) {
		dragging = null;
	}

	@Override
	public void getMenu( MouseEvent e, PanMouseAdaptor ma, ChangeListener toRepaint ) {
	}

	@Override
	public void setObject( Object o ) {
		if (o != this)
			throw new Error("!");
	}	
	
	@Override
	public boolean equals( Object obj ) {
		
		if (!(obj instanceof FRect))
			return false;
		
		FRect o = (FRect)obj;
		
		return super.equals( o ) && o.getFeat() == getFeat();
	}


	public Feature getFeat() {
		return f;
	}


	public Feature setFeat( Feature f ) {
		this.f = f;
		initApps();
		return f;
	}
}
























