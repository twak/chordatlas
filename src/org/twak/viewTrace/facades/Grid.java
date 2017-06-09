package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.MUtils;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.PanMouseAdaptor;

public class Grid implements ICanPaint {

	double tol = 0.1;
	
	public Id x, y;

	public Grid (double tol) {
		this.tol = tol;
	}
	
	public Grid( double tol, double xMin, double xMax, double yMin, double yMax ) {
		this(tol);
		
		x = new Id (xMin);
		x.splitAfter(xMax);
		y = new Id(yMin);
		y.splitAfter(yMax);
	}
	
	public void insert( DRectangle win, Griddable griddable ) {
		insert (win.x, win.y, win.width, win.height, griddable);
	}
	
	private void insert( double xp, double yp, double width, double height, Griddable gr ) {

		if (width < 0 || height < 0)
			throw new Error();
		
		if (x == null)
			x = new Id(xp);
		if (y == null)
			y = new Id(yp);
		
		if (xp < x.value - tol)
			x = new Id(xp, x);
		if (yp < y.value - tol)
			y = new Id(yp, y);
		
		x.splitAfter ( xp ).addGriddable ( x.splitAfter(xp+width ), gr );
		y.splitAfter ( yp ).addGriddable ( y.splitAfter(yp+height), gr );
	}

	class Id implements Iterable<Id> {
		Id next, prev;
		double value;

		public List<Griddable> here = new ArrayList<>();
		
		public Id( double value ) {
			this.value = value;
		}
		
		public Id( double val, Id next ) {
			this(val);
			this.next = next;
			next.prev = this;
		}

		public void addGriddable( Id last, Griddable gr ) {

			if (last == this)
				return;
			
			here.add(gr);
			
			if ( next != null && next != last )
				next.addGriddable (last, gr);
		}
		
		public Id splitAfter(double value) {
			
			if (Math.abs (this.value - value ) <= tol)
				return this;
			
			if (next == null) {
				next = new Id(value);
				next.prev = this;
				return next;
			}
			
			if (value >= next.value - tol)
				return next.splitAfter(value);
			
			
//			if (value < this.value)
//				return prev.after( value );
			
			Id neu = new Id (value);
			
			next.prev = neu;
			neu.next = next;
			next = neu;
			neu.prev = this;
			
			neu.here.addAll( here );
			
			return neu;
		}

		@Override
		public Iterator<Id> iterator() {
			
			return new Iterator<Id>() {

				Id c = Id.this;
				
				@Override
				public boolean hasNext() {
					return c != null;
				}

				@Override
				public Id next() {
					Id out = c;
					c = c.next;
					return out;
				}
				
			};
		}

		@Override
		public String toString() {
			return value +" ["+here.size()+"]";
		}

		public Id find( double a ) {
			
			if (a < value - tol)
				return null;
			else if (Math.abs (a - value ) <= tol)
				return this;
			else if (next != null)
				return next.find( a );
			else
				return null;
		}

		public List<Double> asList() {
			List<Double> out = new ArrayList<>();
			add(out);
			return out;
		}
		
		private void add(List<Double> vals) {
			vals.add(value);
			if (next != null)
				next.add(vals);
		}
	}
	
	public static abstract class Griddable {
		public abstract void instance(DRectangle rect);
		public boolean noneBehind() {
			return false;
		}
	}

	public Map<Griddable, DRectangle> findPositions() {
		
		Map<Griddable, Double[]> rx = findRange(x), ry = findRange( y );
		
		Map<Griddable, DRectangle> out = new HashMap<>();
		
		rx.keySet().stream().forEach( g ->  out.put (g, new DRectangle( 
				rx.get( g )[0],  
				ry.get( g )[0],  
				rx.get( g )[1] - rx.get( g )[0],  
				ry.get( g )[1] - ry.get( g )[0]
				) ) );
		
		return out;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder("x: ");
		for (Id i : x)
			sb.append( " "+i.value );
		
		sb.append( "\ny:" );
		
		for (Id i : y)
			sb.append( " "+i.value );

		return sb.toString();
	}
	
	private Map<Griddable, Double[]> findRange( Id ids ) {
		
		Cache<Griddable, Double[]> out = new Cach<>(x -> new Double[] {Double.MAX_VALUE, -Double.MAX_VALUE} );
		
		for (Id i : ids) {
			
			for (Griddable g : i.here) {
				
				out.get( g )[0] = Math.min( out.get( g )[0], i.value );
				
				if (i.next != null)
					out.get( g )[1] = Math.max( out.get( g )[1], i.next.value );
				
			}
		}
		
		return out.cache;
	}

	@Override
	public void paint( Graphics2D g, PanMouseAdaptor ma ) {

		g.setColor( Color.green );
		
		for (Id c : x) {
			
			Line l = new Line ( c.value, 0, c.value, 1 );
			PaintThing.paint( l, g, ma );
		}
		
		g.setColor (Color.blue);
		
		for (Map.Entry<Griddable, DRectangle> e : findPositions().entrySet()) {
			
//			DRectangle d2 = new DRectangle( e.getValue() );
//			d2.y = d2.y - d2.height;
			
			PaintThing.paint( e.getValue(), g, ma );
		}
	}

	public void instance( Griddable none ) {
		
		for (Id xi : x) {
			
			if (xi.next == null)
				continue;
			
			y:
			for (Id yi : y) {
				
				if (yi.next == null)
					continue;
				
				for (Griddable g1 : xi.here)
					if (!g1.noneBehind())
					for (Griddable g2 : yi.here)
						if (!g2.noneBehind() && g1 == g2)
							continue y;
		
				if (none != null)
					none.instance( new DRectangle( xi.value, yi.value, xi.next.value - xi.value, yi.next.value - yi.value ) );
			}
		}
		
		for (Map.Entry<Griddable, DRectangle> e : findPositions().entrySet()) 
			e.getKey().instance( e.getValue() );
	}

	public boolean fitSingleCell( DRectangle r ) {
		
		if (x == null || y == null)
			return false;
		
		Id x1 = x.find( r.x ),
		   x2 = x.find (r.getMaxX()),
		   y1 = y.find( r.y ),
		   y2 = y.find (r.getMaxY() );
		
		if (MUtils.notNull(x1, x2, y1, y2)) 
			return x1.next == x2 && y1.next == y2;

		return false;
	}	
}
