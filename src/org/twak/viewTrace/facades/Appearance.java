package org.twak.viewTrace.facades;

import java.awt.Color;

public class Appearance {
	public enum TextureUVs {
		SQUARE, ZERO_ONE;
	}
	
	public enum AppMode {
		Color, Texture, Parent, Net
	}

	
	public Appearance( Appearance a ) {
		this.appMode = a.appMode;
		this.textureUVs = a.textureUVs;
		this.color = a.color;
		this.texture = a.texture;
		this.styleZ = a.styleZ;
	}
	
	public Appearance() {
	}

	public AppMode appMode = AppMode.Color;
	public TextureUVs textureUVs = TextureUVs.SQUARE;
	public Color color;
	public String texture;
	public double[] styleZ;
}
