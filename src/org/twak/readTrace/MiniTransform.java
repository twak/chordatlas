package org.twak.readTrace;

import java.util.HashMap;
import java.util.Map;

import javax.vecmath.Matrix4d;

import com.jme3.math.Matrix4f;

public class MiniTransform {
	
	public Map<Integer, Matrix4d> index = new HashMap(); // folder name to offset
	public Matrix4f offset = new Matrix4f(); // additional offset applied to all via ui
}
