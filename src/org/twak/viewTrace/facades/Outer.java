package org.twak.viewTrace.facades;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.Colour;
import org.twak.viewTrace.facades.MiniFacade.Feature;

/**
 * Outer factorisation of a window grid.
 */
public abstract class Outer extends DRectangle implements ICanPaint {

		// getHorizontal returns a list of widths
		public abstract double[] getHorizontal() ;
		public abstract double[] getVertical() ;
		public abstract FRect get(int x, int y);

		int id = -1;
		
		Feature feature;
		public Outer (Feature f) {
			this.feature = f;
		}
		
		@Override
		public void paint( Graphics2D g, PanMouseAdaptor ma ) {
			
			g.setColor( feature.color.darker() );
			g.drawRect( ma.toX( x ), ma.toY (-y-height ), ma.toZoom( width ), ma.toZoom( height ) );
			
			
			List<FRect> toPaint = list();
			for (FRect w : toPaint)  {
				g.setColor( Colour.transparent( feature.color, 50 ) );
				g.fillRect( ma.toX( w.x ), ma.toY (-w.y-w.height ), ma.toZoom( w.width ), ma.toZoom( w.height ) );
				
				g.setColor( feature.color );
				if (w.id >= 0) 
					g.drawString( w.id+"", ma.toX( w.x ) + 3, ma.toY (-w.y-w.height )+ 13  );
//				if (w.gridCoords != null)
//				{	
//					String s = "";
//					for (int i : w.gridCoords)
//						s+= i+", ";
//					g.drawString( s+"", ma.toX( w.x ) + 3, ma.toY (-w.y-w.height ) + 23 );
//					g.drawString( 
//							w.attachedHeight.get(Feature.CORNICE).d+" " +
//							w.attachedHeight.get(Feature.BALCONY).d+" " +
//							w.attachedHeight.get(Feature.SILL).d+" "
//							, ma.toX( w.x ) + 3, ma.toY (-w.y-w.height ) + 33 );
//				}
			}
			
		}
		
		List<FRect> list() {
			List<FRect> out = new ArrayList<>();
			
			double[] xes = getHorizontal(), yes = getVertical();

			double tx = width /Arrays.stream( xes ).sum(), 
				   ty = height/Arrays.stream( yes ).sum(),
				   xc = x, 
				   yc = y;
			
			for (int xx = 0; xx < xes.length; xx++) {
				
				double xWidth = xes[xx] * tx;
				// x-column from xc to xc+xWidth

				yc = y;
				for (int yy = 0; yy < yes.length; yy++) {

					double yHeight = yes[yy] * ty;
					
					FRect r = get(xx,yy);
					if (r != null) {
						r.x      = xc;
						r.width  = xWidth;
						r.y      = yc;
						r.height = yHeight;
						
						out.add(r);
					}
					
					yc += yHeight;
				}
				
				xc += xWidth;
			}
		
			return out;
		}
	}