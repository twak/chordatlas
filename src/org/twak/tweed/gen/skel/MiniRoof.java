package org.twak.tweed.gen.skel;

import java.util.Collections;
import java.util.List;

import javax.vecmath.Point2d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.tweed.gen.Pointz;
import org.twak.utils.Mathz;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.viewTrace.facades.Appearance;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.HasApp;

public class MiniRoof implements HasApp {

	LoopL<Point2d> boundary;
	LoopL<Point2d> pitches = new LoopL<>(), flats = new LoopL<>();
	
	public Appearance app = new Appearance(this);
	
	public void setOutline( Output output ) {
		
		for (Face f : output.faces.values() ) {
			
			if ( GreebleHelper.getTag( f.profile, WallTag.class ) == null )
				continue;
			
			LoopL<Point2d> k = f.edge.uphill.angle( Mathz.Z_UP ) > Math.PI * 0.4 ? flats : pitches;
			
			k.addAll( f.edges.new Map<Point2d>() {
				@Override
				public Point2d map( Loopable<SharedEdge> input ) {
					return Pointz.to2( input.get().start );
				}
			}.run() );
		}
		
		LoopL<Point2d> all = new LoopL<>();
		all.addAll( flats );
		all.addAll( pitches );
		boundary = Loopz.removeInnerEdges( all );
	}
	
	public List<HasApp> getAppChildren() {
		return Collections.emptyList();
	}
}
