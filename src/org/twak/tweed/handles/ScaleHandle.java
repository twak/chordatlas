package org.twak.tweed.handles;

import org.twak.tweed.EventMoveHandle;
import org.twak.tweed.Tweed;

import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

public class ScaleHandle extends Handle {

	Spatial target;
	
	public ScaleHandle(Tweed tweed, Spatial target) {
		super (tweed);
		
		this.target = target;
		
		
//		dir = rot.getRotationColumn(0);

		Box handleOne = new Box( 0.5f, 0.5f, 0.5f );
		g1 = new Geometry("h1", handleOne);
		g1.setUserData(Tweed.CLICK, this);
		Material mat1 = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat1.setColor("Color", ColorRGBA.Magenta);
		g1.setMaterial(mat1);
		
		updateScale();
		
		attachChild(g1);

	}
	
	@Override
	public void updateLogicalState( float tpf ) {
		super.updateLogicalState( tpf );

		updateScale();
	}
	
	private void updateScale() {
		
		float scale = handleScale();
		
		Vector3f offset = new Vector3f( 0, 1, 0 );
		
		BoundingBox bv =  (BoundingBox) target.getWorldBound();
		g1.setLocalTransform(new Transform(  bv.getCenter().add( offset.mult( bv.getExtent(null)) ).add(offset.mult(2)) ));
		
		g1.setLocalScale( scale );
		
		
		g1.updateGeometricState();
//		System.out.println("world transform\n"+g1.getWorldMatrix());
	}

	Vector2f start, lastScreen;
	
	@Override
	public void dragStart(Vector2f screen) {
		start = new Vector2f(screen);
	}

	@Override
	public void dragging(Vector2f screen) {
		
		Vector2f delta = start.subtract(screen );
		
		float scale = target.getLocalScale().x * ( 1 - delta.x * 0.001f );
		target.setLocalScale( scale, scale, scale );
		
		start.x = screen.x;
		lastScreen = new Vector2f(screen);
	}

	@Override
	public void dragEnd() {
		Object[] pco = (Object[])target.getUserData(EventMoveHandle.class.getSimpleName());
		
		if (pco != null)
			((EventMoveHandle)(pco[0])).posChanged();
	}
}
