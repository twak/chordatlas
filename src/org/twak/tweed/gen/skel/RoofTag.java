package org.twak.tweed.gen.skel;

import org.twak.camp.Tag;
import org.twak.siteplan.tags.PlanTag;
import org.twak.viewTrace.facades.GreebleSkel;

public class RoofTag extends PlanTag{
	
	public float[] color;
	
	public RoofTag() {
		this (GreebleSkel.BLANK_ROOF);
	}
	
	public RoofTag(float[] roofColor) {
		
		super( "roof" );
		if (roofColor != null)
			color = new float[] { roofColor[ 0 ], roofColor[ 1 ], roofColor[ 2 ], 1 };
	}
}
