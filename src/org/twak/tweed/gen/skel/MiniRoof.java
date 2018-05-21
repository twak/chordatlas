package org.twak.tweed.gen.skel;

import java.util.Collections;
import java.util.List;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.Mathz;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.Pix2Pix;
import org.twak.viewTrace.franken.RoofApp;

public class MiniRoof implements HasApp {

	public LoopL<Point2d> boundary;
	public LoopL<Point2d> pitches, flats;
	public DRectangle bounds;
	
	public RoofApp app = new RoofApp( this );
	
	public MiniRoof( SuperFace superFace ) {
		app.parent = superFace;
	}

	public void setOutline( Output output ) {
		
		pitches = new LoopL<>();
		flats = new LoopL<>();
		
		
		for (Face f : output.faces.values() ) {
			
			if ( GreebleHelper.getTag( f.profile, RoofTag.class ) == null )
				continue;
			
			LoopL<Point2d> k = f.edge.uphill.angle( Mathz.Z_UP ) > Math.PI * 0.4 ? flats : pitches;
			
			k.addAll( f.points.new Map<Point2d>() {
				@Override
				public Point2d map( Loopable<Point3d> input ) {
					return Pointz.to2XY( input.get() );
				}
			}.run() );
		}
		
		LoopL<Point2d> all = new LoopL<>();
		all.addAll( flats );
		all.addAll( pitches );
		boundary = Loopz.removeInnerEdges( all );
		
		bounds = new DRectangle.Enveloper();
		for (Loop<Point2d> l : pitches)
			for (Point2d p : l) 
				bounds.envelop( p );
		
		for (Loop<Point2d> l : flats)
			for (Point2d p : l) 
				bounds.envelop( p );
	}
}
