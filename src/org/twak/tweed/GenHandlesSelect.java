package org.twak.tweed;

import javax.vecmath.Point3d;

import org.twak.tweed.gen.BlockGen;
import org.twak.utils.LoopL;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public interface GenHandlesSelect {

	public void select( Spatial target, Vector3f contactPoint, Vector2f cursorPosition );

	public void blockSelected( LoopL<Point3d> loopL, BlockGen blockGen );

}
