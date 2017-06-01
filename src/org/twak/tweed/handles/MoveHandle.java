package org.twak.tweed.handles;

import javax.vecmath.Point3d;

import org.twak.tweed.EventMoveHandle;
import org.twak.tweed.Tweed;
import org.twak.utils.Line3D;

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
import com.jme3.scene.shape.Cylinder;

public class MoveHandle extends Handle {

	Spatial target;
	Vector3f dir;
	
	Quaternion rot;
	
	public MoveHandle(Tweed tweed, int axis, Spatial target) {
		super (tweed);
		
		this.target = target;
		
		ColorRGBA color;
		
		switch (axis) {
		case 0:
			color = ColorRGBA.Red;
			rot =  new Quaternion();
			dir = new Vector3f(0,0,1);
			break;
		case 1:
			color = ColorRGBA.Green;
			rot = new Quaternion().fromAngles(0, FastMath.PI / 2, 0);
			dir = new Vector3f(1,0,0);
			break;
		case 2:
			color = ColorRGBA.Blue;
			rot = new Quaternion().fromAngles(FastMath.PI / 2, 0, 0);
			dir = new Vector3f(0,1,0);
			break;
		default:
			throw new Error();
		}
		
//		dir = rot.getRotationColumn(0);

		Cylinder handleOne = new Cylinder(2, 16, 0.25f, 10f, true);
		g1 = new Geometry("h1", handleOne);
		g1.setUserData(Tweed.CLICK, this);
		Material mat1 = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat1.setColor("Color", color);
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
//		offset = offset.mult(1);
		
		BoundingBox bv =  (BoundingBox) target.getWorldBound();
//		System.out.println(bv.getCenter());
		
		g1.setLocalTransform(new Transform(  bv.getCenter() ) );//.add( offset.mult(bv.getExtent(null)).add( dir ) ) ));		

		g1.setLocalRotation(rot);
		
		g1.setLocalScale( scale );
	}

	Vector3f dragStart = null;
	
	/* (non-Javadoc)
	 * @see org.twak.tweed.Handle#dragStart(com.jme3.math.Vector2f)
	 */
	@Override
	public void dragStart(Vector2f screen) {
		dragStart = toWorld(screen);
	}


	/* (non-Javadoc)
	 * @see org.twak.tweed.Handle#dragging(com.jme3.math.Vector2f)
	 */
	@Override
	public void dragging(Vector2f screen) {
		
		Vector3f newPt = toWorld ( screen );
		Vector3f delta = newPt.subtract(dragStart);
		
		target.setLocalTranslation( target.getLocalTranslation().add(delta) );
//		target.getParent().setLocalTranslation( new Vector3f() );//.getLocalTranslation().add(delta) );
//		target.updateGeometricState();
		
//		getParent().setLocalTranslation( getParent().getLocalTranslation().add(delta) );
		updateScale();
		dragStart = newPt;
		
	}

	/* (non-Javadoc)
	 * @see org.twak.tweed.Handle#dragEnd()
	 */
	@Override
	public void dragEnd() {
		Object[] pco = (Object[])target.getUserData(EventMoveHandle.class.getSimpleName());
		
		if (pco != null)
			((EventMoveHandle)(pco[0])).posChanged();
	}
	
	private Vector3f toWorld (Vector2f a) {
		
		Vector3f s  = target.getWorldBound().getCenter();
		Vector3f c  = tweed.getCamera().getLocation();
		Vector3f cd = tweed.getCamera().getWorldCoordinates(a, 0).subtract(c);
		
		Point3d pt = 
				new Line3D ( tod ( c ), tod ( cd ) ).closestPointOn(
				new Line3D ( tod ( s ), tod ( dir ) ) );
		
		return new Vector3f( (float) pt.x, (float) pt.y, (float) pt.z);
	}
	
	private static Point3d tod(Vector3f s) {
		return new Point3d(s.x, s.y, s.z);
	}
	
	private Vector2f toScreen(Vector3f a3) {
		Vector3f v = tweed.getCamera().getScreenCoordinates(a3);
		return new Vector2f(v.x, v.y);
	}

}
