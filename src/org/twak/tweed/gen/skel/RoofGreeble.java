package org.twak.tweed.gen.skel;

import java.awt.Color;

public enum RoofGreeble {
	Velux (Color.blue) , Chimney (Color.orange);
	
	public Color colour;
	
	RoofGreeble (Color color) {
		this.colour = color;
	}
}