package org.twak.tweed.tools;

import javax.swing.Icon;
import javax.swing.JPanel;

import org.twak.tweed.Tweed;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

public class Tool {
	
	Tweed tweed;
	
	public Tool (Tweed tweed) {
		this.tweed = tweed;
	}
	
	public void clear() {}

	public void clickedOn( Spatial target, Vector3f vector3f, Vector2f cursorPosition ) {}

	public void dragStart( Geometry geometry, Vector2f cursorPosition ) {}

	public void dragging( Vector2f vector2f ) {}

	public void dragEnd() {}
	
	public boolean isDragging() {
		return false;
	}

	public void activate( Tweed tweedApp ) {}

	public void deactivate() {}

	public String getName() {
		return "flibble";
	}

	public void getUI( JPanel genUI ) {}

}
