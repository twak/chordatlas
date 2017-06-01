package org.twak.viewTrace;

import java.awt.Color;
import java.awt.Graphics2D;

import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.PaintThing.ICanPaintU;

import com.jme3.math.ColorRGBA;

public class ColorRGBAPainter implements ICanPaintU {
	@Override
	public void paint( Object oa, Graphics2D g2, PanMouseAdaptor ma ) {
				
		ColorRGBA col = (ColorRGBA)oa;
		
		int r = (int)(col.r * 255), 
			g = (int)(col.g * 255), 
			b = (int)(col.b * 255);
		
		float[] hsb = Color.RGBtoHSB( r,g,b, null );
		
		g2.setColor(new Color(r,g,b) );
		
		g2.drawRect( ma.toX( hsb[0] * 10), ma.toY( hsb[2] * 10), ma.toZoom( 0.1 ), ma.toZoom( 0.1 )  );
		
	}
}