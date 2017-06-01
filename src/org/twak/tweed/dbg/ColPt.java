package org.twak.tweed.dbg;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.vecmath.Point2d;

import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.PanMouseAdaptor;

public class ColPt extends Point2d implements ICanPaint {
	
	Color color;
	

	@Override
	public void paint( Graphics2D g, PanMouseAdaptor ma ) {
		g.setColor( color );
		g.fillOval( ma.toX( x )-2, ma.toY( y )-2, 4, 4 );
		
	}

	public ColPt( double x, double y, float r, float g, float b ) {
		super (x,y);
		
		this.color = new Color (r,g,b);
	}
}
