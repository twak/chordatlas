package org.twak.tweed.tools;

import org.twak.tweed.Tweed;
import org.twak.tweed.handles.Handle;
import org.twak.tweed.handles.MoveHandle;
import org.twak.tweed.handles.RotHandle;
import org.twak.tweed.handles.ScaleHandle;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class MoveTool extends Tool {

	Handle currentDraggingHandle = null;
	Node currentHandles = new Node();//new HashSet<>();
	Tweed tweed;
	
	public MoveTool( Tweed tweed ) {
		super( tweed );
	}
	
	@Override
	public void activate( Tweed tweed ) {
		this.tweed = tweed;
		tweed.getRootNode().attachChild( currentHandles );
	}
	
	@Override
	public void deactivate() {
		currentHandles.removeFromParent();
	}
	
	@Override
	public void clear() {
		for (Spatial currentHandle : currentHandles.getChildren()) 
			currentHandle.removeFromParent();
	}
	
	@Override
	public void clickedOn( Spatial target, Vector3f vector3f, Vector2f cursorPosition ) {		
		
		clear();
		
		currentHandles.setLocalTranslation(new Vector3f());
		currentHandles.attachChild ( new MoveHandle (tweed, 0, target ) );
		currentHandles.attachChild ( new MoveHandle (tweed, 1, target ) );
		currentHandles.attachChild ( new MoveHandle (tweed, 2, target ) );
		currentHandles.attachChild ( new RotHandle  (tweed,    target ) );
		currentHandles.attachChild ( new ScaleHandle(tweed,    target ) );
	}
	
	@Override
	public void dragging( Vector2f screen, Vector3f d3) {
		currentDraggingHandle.dragging( screen );
	}
	
	@Override
	public void dragStart( Geometry target, Vector2f screen, Vector3f world ) {
		currentDraggingHandle = (Handle) target.getUserData(Tweed.CLICK);
		currentDraggingHandle.dragStart( screen );
	}
	
	@Override
	public boolean isDragging() {
		return currentDraggingHandle != null;
	}
	
	@Override
	public void dragEnd() {
		currentDraggingHandle.dragEnd();
		currentDraggingHandle = null;
	}
	
	@Override
	public String getName() {
		return "move";
	}
}
