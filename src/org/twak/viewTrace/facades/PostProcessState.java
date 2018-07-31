package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.vecmath.Point2d;

import org.twak.utils.IdentityHashSet;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;

public class PostProcessState {

	// last perimeter from skeleton evaluation
	public List<Loop<? extends Point2d>> 
			wallFaces = new ArrayList<>(), 
			roofFaces = new ArrayList<>();
	
	public DRectangle innerFacadeRect;
	public DRectangle outerWallRect;
	public LoopL<Point2d> occluders = new LoopL();
	
	/**
	 * Window lifecycle:
	 * 
	 * FacadeLabelApp populates miniFacade.featureGen
	 * GreebleSkel (run after FacadeLabelApp) populates PostProcessState.generateWindows to cull those outside geometry
	 * FacadeTextureApp uses these windows to create textures
	 * 
	 */
	public PostProcessState( Loop<LPoint2d> flat ) {
		this.wallFaces.add( flat );
	}
	
	public PostProcessState() {}
}
