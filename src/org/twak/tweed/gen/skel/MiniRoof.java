package org.twak.tweed.gen.skel;

import java.util.HashMap;
import java.util.Map;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
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
import org.twak.viewTrace.franken.RoofTexApp;

public class MiniRoof implements HasApp {

	public Map<Loop<Point2d>, Face> origins = new HashMap<>();
	
	public LoopL<Point2d> boundary = new LoopL<>();
	public LoopL<Point2d> pitches = new LoopL<>(), flats = new LoopL<>();
	
	public DRectangle bounds;
	
	public RoofTexApp app = new RoofTexApp( this );
	
	public MiniRoof( SuperFace superFace ) {
		app.parent = superFace;
	}

	public void setOutline( Output output ) {
		
		pitches = new LoopL<>();
		flats = new LoopL<>();
		
		origins.clear();
		boundary.clear();
		pitches.clear();
		flats.clear();
		
		for (Face f : output.faces.values() ) {
			
			if ( GreebleHelper.getTag( f.profile, RoofTag.class ) == null )
				continue;
			
			LoopL<Point2d> category = f.edge.uphill.angle( Mathz.Z_UP ) > Math.PI * 0.4 ? flats : pitches;
			
			LoopL<Point2d> loopl = f.points.new Map<Point2d>() {
				@Override
				public Point2d map( Loopable<Point3d> input ) {
					return Pointz.to2XY( input.get() );
				}
			}.run();
			
			for (Loop<Point2d> lop : loopl)
				origins.put (lop, f);
			
			category.addAll( loopl );
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

	public LoopL<Point2d> getAllFaces() {
		
		LoopL<Point2d> out = new LoopL();
		
		out.addAll( pitches );
		out.addAll( flats);
		
		return out;
	}
	
}
