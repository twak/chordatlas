package org.twak.tweed.gen;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2d;

import org.twak.utils.Line;
import org.twak.tweed.gen.FeatureGen.MegaFeatures;
import org.twak.utils.HalfMesh2.HalfEdge;
import org.twak.viewTrace.SuperLine;
import org.twak.viewTrace.facades.LineHeight;
import org.twak.viewTrace.facades.MiniFacade;

public class SuperEdge extends HalfEdge {

	public List<LineHeight> occlusions = new ArrayList<>();
	public SuperLine profLine;
	public Prof prof;
	public int profI;
	public boolean debug;
	public List<MiniFacade> mini;
	public MiniFacade toEdit;
	public MegaFeatures proceduralFacade;
	
	public SuperEdge( Point2d s, Point2d e, HalfEdge parent ) {
		super( s, e, parent );

		profLine = parent == null ? null : ( (SuperEdge) parent ).profLine;
	}

	public SuperEdge( SuperEdge o ) {
		
		super ( new Point2d ( o.start ), new Point2d ( o.end ), null);
		
		this.next = o.next;
		this.over = o.over;
		this.face = o.face; //!
		
		this.occlusions = new ArrayList (o.occlusions);
		this.profLine   = o.profLine;
		this.prof       = o.prof;
		this.profI      = o.profI;
		this.mini       = o.mini;
	}

	public void addMini( MiniFacade m ) {
		
		if (m== null)
			return;
		
		if (mini==null)
			mini = new ArrayList<>();
		
		mini.add(m);
	}

	public double[] findRange() {
		
		if ( mini.isEmpty() || mini.get( 0 ).imageFeatures == null)
			return null;
		
 	    Line mf = mini.get(0).imageFeatures.mega.megafacade; // todo: bad place for this method.
		double mfL = mf.length();
		return new double[] { mf.findPPram( start ) * mfL, mf.findPPram( end ) * mfL };
	}
}