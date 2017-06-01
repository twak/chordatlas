package org.twak.tweed.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.ProfileGen.MegaFacade;
import org.twak.utils.Cach;
import org.twak.utils.Cache;
import org.twak.utils.HalfMesh2.HalfEdge;
import org.twak.utils.Line;
import org.twak.utils.Loop;
import org.twak.utils.Loopable;
import org.twak.utils.Loopz;
import org.twak.viewTrace.SuperLine;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class VizSkelGen extends SkelGen {

	Mode mode;
	
	public enum Mode {
		Outline, Extrude, CE, Profiles
	}
	
	public VizSkelGen( List<Line> footprint, Tweed tweed, BlockGen blockGen, Mode mode ) {
		
		super(tweed );
		
		this.mode = mode;
		this.footprint = footprint;
		this.blockGen = blockGen;
	}
	
	@Override
	public void calculate() {

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();

		
		SuperFace sf;
		
		if (mode == Mode.Extrude)  {
			sf = toHalf ( Loopz.to3d( Loopz.removeInnerEdges( Loopz.toXZLoop ( blockGen.polies ) ).get(0)  , 0, 1) );
			PlanSkeleton skel = calc( sf );
			setSkel( skel, skel.output , sf);
		}
		else for (Loop <Point3d> loop : blockGen.polies ) { 
			sf = toHalf(loop);
			PlanSkeleton skel = calc( sf );
			setSkel( skel, skel.output , sf);
		}
		gNode.updateModelBound();
		gNode.updateGeometricState();
		setVisible(visible);
	}

	private SuperFace toHalf(Loop<Point3d> loop ) {
		
		Cache<Point3d, Point2d> look = new Cach<>( x -> Pointz.to2(x) );
		
		HalfEdge last = null, first = null;
		SuperFace out = new SuperFace();
		
		for (Loopable<Point3d> edge : loop.loopableIterator()) {
			
			SuperEdge e = new SuperEdge( look.get ( edge.get() ), look.get ( edge.getNext().get()), null );
			
			if (first == null)
				first = e;
			
			if (last != null) 
				last.next = e;
		
			e.face = out;
			e.prof = null;//!
			e.mini = Collections.EMPTY_LIST;
			
			
			if (mode == Mode.Profiles)
			for (Line l : footprint) {
				if (l.absAngle(e.line()) < 0.1 && 
						l.distance(e.start, true) < 1.5 && 
						l.distance(e.end, true) < 1.5 
						) {
					
					SuperLine sl = (SuperLine)l;
					MegaFacade mf =  sl.getMega();
					
					e.prof = findProf (e.start, e.end, sl, mf);
					
				}
			}
			
			last = e;
		}
		
		last.next = first;
		
		out.e = first;
		
		SkelFootprint.meanModeHeightColor( Loopz.toXZLoop(loop) , out, blockGen);
		
		if (mode == Mode.CE) {
			for (HalfEdge ee : out) {
				SuperEdge e = (SuperEdge)ee;

				Matrix4d m = new Matrix4d();
				m.setIdentity();
				
				e.prof = new Prof(m, new Vector3d());
				
				e.prof.add(new Point2d(0,0));
				e.prof.add(new Point2d(0,out.height));
				e.prof.add(new Point2d(-1,out.height+1));
			}
		}
		
		return out;
	}

	private Prof findProf(Point2d start, Point2d end, SuperLine sl, MegaFacade mf) {

		int s = mf.getIndex( start ), e = mf.getIndex( end ) + 1;

		List<Prof> ps = new ArrayList<>();
		
		for ( int ii = s; ii <= e; ii++ ) {
			Prof p = mf.profiles.get( ii );
			if ( p != null )
				ps.add(p);
		}
		
		if (ps.isEmpty())
			return null;
		
		return Prof.parameterize(sl, ps);
	}

}
