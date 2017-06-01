package org.twak.viewTrace.facades;

import org.twak.straightskeleton.Tag;

public class RoofTag extends Tag{
	
	public float[] color;
	
	public RoofTag(float[] roofColor) {
		
		super( "roof" );
		if (roofColor != null)
			color = new float[] { roofColor[ 0 ], roofColor[ 1 ], roofColor[ 2 ], 1 };
	}
}
