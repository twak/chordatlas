package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.List;

import org.twak.utils.Line;
import org.twak.utils.geom.Graph2D;
import org.twak.utils.geom.LinearForm;

public class PerpSupport {
	
	List<SupportPoint > supportPoints = new ArrayList();
	double supportMax = 0;
	
	public PerpSupport(LineSoup slice, Graph2D gis) {
		
		for (Line l : gis.allLines()) {
			
			double length = l.length();
			int noPoints = (int) Math.max ( 2, length / 0.1 );
			
			for ( int n = 0; n <= noPoints; n ++ ) {
				
				SupportPoint sp = new SupportPoint( l, l.fromPPram(n/ (double)noPoints) );
				
				LinearForm perp = new LinearForm( l );
				perp.perpendicular();
				perp.findC(sp.pt);
				
				sp.support = slice.intersect(2, perp, sp.pt );
				
				supportMax = Math.max(supportMax, sp.support);
				supportPoints.add ( sp );
			}
		}
	}
	
}
