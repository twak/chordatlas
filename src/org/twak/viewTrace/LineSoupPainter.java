package org.twak.viewTrace;

import java.awt.BasicStroke;
import java.awt.Graphics2D;

import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.geom.Line;
import org.twak.utils.ui.Rainbow;
import org.twak.utils.PaintThing;
import org.twak.utils.PanMouseAdaptor;

public class LineSoupPainter implements ICanPaintU {
	
		@Override
		public void paint(Object e, Graphics2D g, PanMouseAdaptor ma) {

			LineSoup o = (LineSoup)e;
			
				int c = 0;
				double scatter = 0.0;
				
				for (Line l : o.all) {
					if (o.multiColour)
						g.setColor(Rainbow.getColour(c++));
					
					g.drawLine(
							ma.toX(l.start.x + Math.random() * scatter), 
							ma.toY(l.start.y + Math.random() * scatter), 
							ma.toX(l.end.x   + Math.random() * scatter), 
							ma.toY(l.end.y   + Math.random() * scatter));
					
					int scale = 1;
					
					if ( g.getStroke() instanceof BasicStroke )
						scale = (int) ((BasicStroke)g.getStroke()).getLineWidth();
					
//					PaintThing.drawArrow(g, ma, l, scale * 5);
					
				}
			}
}
