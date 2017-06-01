package org.twak.tweed.handles;

import org.twak.tweed.Tweed;

import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

public abstract class Handle extends Node {
	
	protected Tweed tweed;
	protected Geometry g1;
	
	public Handle (Tweed tweed) {
		this.tweed = tweed;
	}
	
	public abstract void dragStart(Vector2f screen);

	public abstract void dragging(Vector2f screen);

	public abstract void dragEnd();

	protected float handleScale() {
		return 0.05f * tweed.getCamera().getLocation().distance( g1.getLocalTranslation() );
	}

}