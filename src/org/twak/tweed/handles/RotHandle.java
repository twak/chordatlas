package org.twak.tweed.handles;

import org.twak.tweed.EventMoveHandle;
import org.twak.tweed.Tweed;

import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Torus;

public class RotHandle extends Handle {
	
	Spatial target;
	Vector3f dir;
	
	public RotHandle(Tweed tweed, Spatial target) {
		super (tweed);
		this.target = target;
		this.dir = new Vector3f(0,1,0);
		
		
		
		Torus handleOne = new Torus(24, 8, 0.2f, 4f); //Box( 0.1f, 0.1f, 0.1f );
		g1 = new Geometry("h1", handleOne);
		g1.setUserData(Tweed.CLICK, this);
		Material mat1 = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat1.setColor( "Color", ColorRGBA.Yellow );
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
		
		Vector3f offset = new Vector3f( dir );
		
		BoundingBox bv =  (BoundingBox) target.getWorldBound();
		g1.setLocalTransform(new Transform(  bv.getCenter().add( offset.mult( bv.getExtent(null)) ).add(offset.mult(2)) ));
		
		g1.setLocalScale( scale * 1 );
		
		Quaternion rot = new Quaternion(new float[] {FastMath.PI/2, 0, 0 });
		
		g1.setLocalRotation( rot );
	}
	
	Vector2f start, lastScreen;
	
	@Override
	public void dragStart(Vector2f screen) {
		start = new Vector2f(screen);
	}

	@Override
	public void dragging(Vector2f screen) {
		
		Vector2f delta = start.subtract(screen );
		
		float scale = (float) Math.max ( Math.abs ( delta.y ), 1 );
		
		Quaternion rot = new Quaternion().fromAngleNormalAxis(target.getLocalRotation().toAngles(null)[1] + delta.x * scale * 0.0001f, dir);
		target.setLocalRotation( rot );
		
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
