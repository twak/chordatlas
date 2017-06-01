package org.twak.tweed.dbg;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.vecmath.Tuple3d;

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Gen;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public class DebugGen extends Gen {

	public DebugGen( List<Tuple3d> todebug2, Tweed tweed ) {
		super( "foo", tweed );
		this.cubes = todebug2;
	}

	
	public static List<Tuple3d> toDebug = new ArrayList();
	public static void go (Tweed tweed) {
		tweed.frame.removeGens( DebugGen.class );
		tweed.frame.addGen(new DebugGen(toDebug, tweed), true);
		toDebug = new ArrayList<>();
	}
	
	List<Tuple3d> cubes = new ArrayList<>();
	
	@Override
	public void calculate() {

		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
		
		Mesh mesh = new Mesh();
		mesh.setMode( Mode.Points );
		
		Vector3f[] verts = new Vector3f[cubes.size()];
		
		for (int i = 0; i < cubes.size(); i++) {
			verts[ i ] = new com.jme3.math.Vector3f( (float)cubes.get( i ).x, (float)cubes.get( i ).y, (float)cubes.get( i ).z );
		}
		
		mesh.setBuffer( VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer( verts ) );
		
		Material mat1 = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
		mat1.setBoolean( "VertexColor", true );
		Geometry depth = new Geometry( "depth", mesh );
		depth.setMaterial( mat1 );
		
		depth.updateModelBound();
		depth.updateGeometricState();
		
		gNode.attachChild(depth);
		
		super.calculate();
	}

	@Override
	public JComponent getUI() {
		return null;
	}

	public void addMarker (Tuple3d pt) {
		
	}
	
	
	
}
