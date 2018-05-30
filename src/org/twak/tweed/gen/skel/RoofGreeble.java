package org.twak.tweed.gen.skel;

import java.awt.Color;

public enum RoofGreeble {
	Velux (Color.blue, false) , Chimney (Color.orange, true);
	
	public Color colour;
	public boolean verticalProjection;
	
	RoofGreeble (Color color, boolean v) {
		this.colour = color;
		this.verticalProjection = v;
	}
}