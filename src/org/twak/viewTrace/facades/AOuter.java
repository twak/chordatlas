package org.twak.viewTrace.facades;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.Grid.Griddable;
import org.twak.viewTrace.facades.Grid.Id;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class AOuter extends Outer {

	double[] xes, yes;
	
	FRect[][] elements;
	
	public AOuter( Feature f ) {
		super( f );
	}

	@Override
	public double[] getHorizontal() {
		return xes;
	}

	@Override
	public double[] getVertical() {
		return yes;
	}

	@Override
	public FRect get( int x, int y ) {
		return elements[x][y];
	}
	
	class OuterGrid extends Griddable {

		FRect rect;
		
		public OuterGrid (FRect rect) {
			this.rect = rect;
		}
		
		@Override
		public void instance( DRectangle pos ) {
			
			rect.setFrom( pos );
			
			rect.xi = xLookup.get(pos.x);
			rect.yi = yLookup.get(pos.y);
			
			rect.outer = AOuter.this;
			
			elements[rect.xi][rect.yi] = rect;
		}
	}
	
	Grid building = new Grid(0.1);
	
	public boolean extend( FRect r ) {
		
		if (
			! ( isSingle( building.x, r.x, r.x+r.width  ) &&
			    isSingle( building.y, r.y, r.y+r.height ) ) )
			return false;
		
		building.insert( r, new OuterGrid(r) );
		
		return true;
	}
	
	private boolean isSingle( Id i, double a, double b ) {
		
		if (i == null || i.next == null)
			return true;

		for (double ab : new double[] {a,b})
			if (i.value + building.tol < ab && i.next.value - building.tol > ab)
				return false;
		
		if (a < i.value + building.tol && b > i.next.value + building.tol )
			return false;
		
		return isSingle(i.next, a, b);
	}
	
	public boolean easyAdd( FRect r ) {
		
		if ( ! ( building.fitSingleCell( r )  ) )
			return false;
		
		building.insert( r, new OuterGrid(r) );
		
		return true;
	}
	
	Map<Double, Integer> xLookup, yLookup;
	public void done() {
		
		List<Double> 
			xl = building.x.asList(), 
			yl = building.y.asList();
		
		x = xl.get(0);
		width  = xl.get(xl.size()-1) - x;
		y = yl.get(0);
		height = yl.get(yl.size()-1) - y;
		
		xLookup = new HashMap<>();
		yLookup = new HashMap<>();
		
		for (int i = 0; i < xl.size(); i++)
			xLookup.put (xl.get(i), i);
		
		for (int i = 0; i < yl.size(); i++)
			yLookup.put (yl.get(i), i);
		
		xes = new double[xl.size()-1];
		yes = new double[yl.size()-1];
		
		for (int i = 0; i < xl.size()-1; i++)
			xes[i] = xl.get( i+1 ) - xl.get(i);
		
		for (int i = 0; i < yl.size()-1; i++)
			yes[i] = yl.get( i+1 ) - yl.get(i);
		
	 	elements = new FRect[xes.length][yes.length];
	 	building.instance( null );

	 	xLookup = null;
	 	yLookup = null;
		building = null;
	}

}
