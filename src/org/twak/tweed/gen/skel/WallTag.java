package org.twak.tweed.gen.skel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.twak.siteplan.tags.PlanTag;
import org.twak.tweed.gen.SuperEdge;
import org.twak.utils.Line;
import org.twak.utils.ui.Colourz;
import org.twak.viewTrace.facades.MiniFacade;

public class WallTag extends PlanTag{
	
	public Line planLine;
	public MiniFacade miniFacade;

	public SuperEdge occlusionID; 
	public Set<SuperEdge> occlusions = new HashSet<>();
	
	public WallTag() {
		super("wall");
	}
	
	public WallTag(Line planLine, SuperEdge occlusionID, Set<SuperEdge> occlusions, MiniFacade miniFacade) {
		super( "wall" );
		
		this.occlusions = occlusions;
		this.occlusionID = occlusionID;
		
		this.planLine = planLine;
		this.miniFacade = miniFacade;
	}

	public WallTag( WallTag wtf, MiniFacade scaledMF ) {
		this (wtf.planLine, wtf.occlusionID, wtf.occlusions, scaledMF );
	}
}
